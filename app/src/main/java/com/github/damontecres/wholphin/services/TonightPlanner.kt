package com.github.damontecres.wholphin.services

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * A title the Tonight planner may recommend.
 *
 * Deliberately free of `BaseItem` and the server: the whole point of the plan is the arithmetic
 * between the clock, the runtime and the viewer's taste, and that arithmetic has to be testable
 * without a network or an Android runtime. [TonightService] maps the server's items onto this.
 *
 * @param minutes how long this would take from here: the remaining time for something started,
 *   otherwise its full runtime. Null when the server does not know, which keeps it out of the pick
 *   but still lets it appear in a shelf.
 * @param progress percent watched, 0 for something untouched
 * @param lastPlayedDaysAgo for a played title, how long since the viewer last finished it
 */
data class TonightCandidate(
    val id: UUID,
    val title: String,
    val seriesName: String? = null,
    /** For an episode: the series it belongs to, which is what the finish-line shelf counts. */
    val seriesId: UUID? = null,
    val minutes: Long? = null,
    val progress: Double = 0.0,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val isEpisode: Boolean = false,
    val lastPlayedDaysAgo: Long? = null,
)

/**
 * The evening as the app understands it.
 *
 * @param minutes how much viewing time is left before the effective bedtime, clamped so that a
 *   television left running at noon does not report twelve hours of "evening"
 * @param late past the bedtime (or the small hours): the window becomes one comfortable sitting
 * @param weekend Friday or Saturday, when the evening is allowed to run later
 */
data class TonightWindow(
    val now: LocalDateTime,
    val bedtime: LocalTime,
    val minutes: Long,
    val late: Boolean,
    val weekend: Boolean,
) {
    /** When the recommended title would end if it started now. */
    val endsAt: LocalDateTime get() = now.plusMinutes(minutes)
}

/** Why the planner chose the title it did. Rendered by the page, so it stays a type. */
sealed interface TonightReason {
    /** Something already started. [title] is the series for an episode. */
    data class Continue(
        val title: String,
    ) : TonightReason

    /** The next unwatched episode of a show the viewer is following. */
    data class NextEpisode(
        val series: String,
    ) : TonightReason

    /** An untouched title in a genre the viewer keeps returning to. */
    data class MatchesTaste(
        val genre: String,
    ) : TonightReason

    /** Nothing more specific applies; the ranker simply chose it. */
    data object TopPick : TonightReason
}

/**
 * "What can I actually finish tonight?"
 *
 * Every other surface in the app answers "what should I watch?" from taste alone. That question
 * has a second half — the clock. A great 2h40m film is a bad answer at 10:40 PM, and the app has
 * the data to know it: runtimes, resume positions, and a bedtime the viewer can set. The planner
 * turns those into one recommendation with a reason and a finish time, plus the shelves that make
 * the rest of the evening legible.
 *
 * Everything here is pure and deterministic for a given [LocalDateTime]; the only randomness is a
 * small tie-breaker seeded by the day, so the pick is stable through an evening but not identical
 * every night.
 */
object TonightPlanner {
    /** Past bedtime, the evening is one more episode, not a double feature. */
    const val LATE_WINDOW_MINUTES = 90L

    /** After midnight there is even less runway. */
    const val SMALL_HOURS_WINDOW_MINUTES = 60L

    /** Friday and Saturday are allowed to run past the weekday bedtime. */
    const val WEEKEND_EXTRA_MINUTES = 45L

    /** The smallest useful window; below this every title is "too long" and the page reads broken. */
    const val MIN_WINDOW_MINUTES = 20L

    /** A television left running at noon should not plan a twelve-hour evening. */
    const val MAX_WINDOW_MINUTES = 360L

    private const val SMALL_HOURS_CUTOFF_HOUR = 5

    /** Below this the genre is too weak to claim as the reason. */
    private const val TASTE_REASON_THRESHOLD = 0.45

    /** An item this far through is effectively finished; the server just has not been told. */
    private const val EFFECTIVELY_FINISHED = 95.0

    /**
     * The evening available from [now] until [bedtimeMinutes] (minutes after midnight; values
     * past 1440 mean "after midnight tonight").
     */
    fun window(
        now: LocalDateTime,
        bedtimeMinutes: Int,
    ): TonightWindow {
        val weekend = now.dayOfWeek == DayOfWeek.FRIDAY || now.dayOfWeek == DayOfWeek.SATURDAY
        val bedtime = LocalTime.of((bedtimeMinutes / 60) % 24, bedtimeMinutes % 60)
        val effectiveBedtime = bedtime.plusMinutes(if (weekend) WEEKEND_EXTRA_MINUTES else 0)
        val smallHours = now.hour < SMALL_HOURS_CUTOFF_HOUR

        val target =
            if (effectiveBedtime <= now.toLocalTime() && effectiveBedtime.hour < SMALL_HOURS_CUTOFF_HOUR) {
                // "00:30" said at 23:00 means tomorrow; said at 00:15 it is already past.
                now.toLocalDate().atTime(effectiveBedtime).plusDays(1)
            } else {
                now.toLocalDate().atTime(effectiveBedtime)
            }
        val raw = java.time.Duration.between(now, target).toMinutes()

        // Small hours reshape the evening only when the bedtime is not actually just ahead. A
        // viewer at 00:15 with a 00:30 bedtime has fifteen minutes; the small-hours window used
        // to say "Late night · 1h 30m" and then recommend a title that ran past the bed they had
        // just set. The rule is still needed for the viewer awake at 2 AM with a 23:30 bedtime,
        // whose next bedtime is a whole day away.
        val bedtimeSoon = raw in 1..SMALL_HOURS_WINDOW_MINUTES
        val late = raw <= 0 || (smallHours && !bedtimeSoon)
        val minutes =
            when {
                // No floor here: with the bedtime minutes away, the honest window is the time
                // that is actually left, even if that is a single short.
                bedtimeSoon -> raw.coerceAtMost(SMALL_HOURS_WINDOW_MINUTES)
                smallHours -> SMALL_HOURS_WINDOW_MINUTES
                raw <= 0 -> LATE_WINDOW_MINUTES
                else -> raw.coerceIn(MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES)
            }
        return TonightWindow(now, effectiveBedtime, minutes, late, weekend)
    }

    /**
     * Order the candidates best-first for this evening.
     *
     * A title that fits the window is worth far more than one that does not; after that the
     * ranking is taste, quality and a gentle preference for continuing something already started.
     */
    fun rank(
        candidates: List<TonightCandidate>,
        window: TonightWindow,
        affinity: Map<String, Double>,
        dismissed: Set<UUID> = emptySet(),
    ): List<TonightCandidate> =
        candidates
            .asSequence()
            .filter { it.id !in dismissed }
            .filter { (it.minutes ?: 0L) > 0L }
            .filter { it.progress < EFFECTIVELY_FINISHED }
            .distinctBy { it.id }
            .sortedByDescending { score(it, window, affinity) }
            .toList()

    /**
     * Series that are one episode from finished, in next-up order.
     *
     * A viewer with twenty minutes left wants the shortest path to *done*, and the shortest path
     * is usually the last episode of something they are already into. Counted per series rather
     * than per season so that "one episode left" is true to the word.
     */
    fun finishLine(
        nextUp: List<TonightCandidate>,
        seriesWithOneLeft: Set<UUID>,
        limit: Int = Int.MAX_VALUE,
    ): List<TonightCandidate> =
        nextUp
            .filter { it.seriesId != null && it.seriesId in seriesWithOneLeft }
            .distinctBy { it.seriesId }
            .take(limit)

    /** The single title to lead the page with, or null when nothing is known about runtimes. */
    fun pick(
        candidates: List<TonightCandidate>,
        window: TonightWindow,
        affinity: Map<String, Double>,
        dismissed: Set<UUID> = emptySet(),
    ): TonightCandidate? = rank(candidates, window, affinity, dismissed).firstOrNull()

    /** Why [candidate] was picked. */
    fun reason(
        candidate: TonightCandidate,
        affinity: Map<String, Double>,
    ): TonightReason {
        val series = candidate.seriesName?.takeIf { it.isNotBlank() }
        return when {
            candidate.progress > 0.0 -> TonightReason.Continue(series ?: candidate.title)
            candidate.isEpisode && series != null -> TonightReason.NextEpisode(series)
            else -> {
                val genre =
                    candidate.genres
                        .map { it to (affinity[it.lowercase()] ?: 0.0) }
                        .filter { it.second >= TASTE_REASON_THRESHOLD }
                        .maxByOrNull { it.second }
                        ?.first
                if (genre != null) TonightReason.MatchesTaste(genre) else TonightReason.TopPick
            }
        }
    }

    /**
     * How much of the window a title would use, 0..1, clamped at 1. The deadline bar draws this.
     */
    fun windowFraction(
        candidate: TonightCandidate,
        window: TonightWindow,
    ): Float {
        val minutes = candidate.minutes ?: return 0f
        return (minutes.toDouble() / window.minutes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun score(
        candidate: TonightCandidate,
        window: TonightWindow,
        affinity: Map<String, Double>,
    ): Double {
        val minutes = candidate.minutes ?: return Double.NEGATIVE_INFINITY
        val overflow = (minutes - window.minutes).coerceAtLeast(0L)
        var score = 100.0 - overflow * 0.30
        if (candidate.progress > 0.0) {
            // Continuing is the least expensive start there is; the page should prefer it when the
            // alternatives are equal, but not let a mediocre resume beat a better fit.
            score += 14.0
        }
        if (candidate.isEpisode) {
            // An episode is the natural unit of a weeknight.
            score += 5.0
        }
        val bestGenre = candidate.genres.maxOfOrNull { affinity[it.lowercase()] ?: 0.0 } ?: 0.0
        score += bestGenre * 40.0
        score += (candidate.rating ?: 6.5).coerceIn(0.0, 10.0) * 2.2
        // Stable for a day, different the next: a tie-breaker, not a shuffle.
        score += ((candidate.id.hashCode() xor window.now.dayOfYear) and 0x7fffffff) % 9
        return score
    }
}
