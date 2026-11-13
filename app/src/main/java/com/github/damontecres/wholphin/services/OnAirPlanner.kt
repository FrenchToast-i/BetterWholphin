package com.github.damontecres.wholphin.services

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Random
import java.util.UUID

/**
 * One playable entry in a channel's loop.
 *
 * Deliberately tiny: a schedule is arithmetic, and arithmetic should be testable without a
 * server, a ViewModel or a `BaseItem`.
 */
data class OnAirEntry(
    val id: UUID,
    val runtimeMinutes: Int,
)

/** One airing: an [entry] shown between [startMs] and [endMs]. */
data class OnAirProgram(
    val entry: OnAirEntry,
    val startMs: Long,
    val endMs: Long,
) {
    fun elapsedMs(nowMs: Long): Long = (nowMs - startMs).coerceIn(0L, endMs - startMs)

    fun progress(nowMs: Long): Float {
        val runtime = (endMs - startMs).coerceAtLeast(1L)
        return (elapsedMs(nowMs).toFloat() / runtime).coerceIn(0f, 1f)
    }
}

/**
 * The arithmetic behind On Air: a deterministic loop of library entries per channel.
 *
 * There is no schedule table to maintain. A channel is a playlist seeded by the week and the
 * channel's key, laid end to end since Monday 00:00; [schedule] projects that loop onto the
 * current time. The result is continuous across midnight (a film that starts at 23:40 keeps
 * running at 00:20), stable while the viewer moves around the page, and different every week.
 */
object OnAirPlanner {
    /** A row of four is the minimum that looks like programming rather than a mistake. */
    const val MIN_ENTRIES = 4

    const val DEFAULT_EPISODE_MINUTES = 24
    const val DEFAULT_MOVIE_MINUTES = 100

    /** A loop entry is clamped so a missing runtime cannot create a zero-length program. */
    const val MIN_RUNTIME_MINUTES = 5
    const val MAX_RUNTIME_MINUTES = 300

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Build the channel's loop: deterministic for a seed, varied across channels and weeks. */
    fun playlist(
        channelKey: String,
        entries: List<OnAirEntry>,
        seed: Long,
    ): List<OnAirEntry> {
        if (entries.size < 2) return entries
        val shuffled = entries.toMutableList()
        val random = Random(seed * 31L + channelKey.hashCode())
        for (i in shuffled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        return shuffled
    }

    /**
     * Project the loop onto [nowMs], returning the current program first.
     *
     * [nowMs] is clamped into the loop rather than rejected, so a clock that moved backwards
     * still yields a sane schedule.
     */
    fun schedule(
        playlist: List<OnAirEntry>,
        anchorMs: Long,
        nowMs: Long,
        count: Int,
    ): List<OnAirProgram> {
        if (playlist.isEmpty() || count <= 0) return emptyList()
        val runtimes = playlist.map { it.runtimeMs() }
        val total = runtimes.sum().coerceAtLeast(1L)
        // Local positive modulo rather than Math.floorMod: that overload landed in API 24 and the
        // app still supports 23, where the call would be a NoSuchMethodError.
        val delta = (nowMs - anchorMs) % total
        val offset = if (delta < 0) delta + total else delta

        var index = 0
        var elapsed = 0L
        while (index < runtimes.size && offset >= elapsed + runtimes[index]) {
            elapsed += runtimes[index]
            index++
        }
        if (index >= runtimes.size) {
            index = 0
            elapsed = 0L
        }

        val programs = mutableListOf<OnAirProgram>()
        var start = nowMs - (offset - elapsed)
        var i = index
        repeat(count) {
            val runtime = runtimes[i]
            programs += OnAirProgram(playlist[i], start, start + runtime)
            start += runtime
            i = (i + 1) % playlist.size
        }
        return programs
    }

    /**
     * The program a viewer pressing "Tune in" should get: the one airing now, or the first of
     * the projection if the clock sits outside the list (a schedule recomputed around a tick).
     */
    fun tuneIn(
        programs: List<OnAirProgram>,
        nowMs: Long,
    ): OnAirProgram? =
        programs.firstOrNull { nowMs >= it.startMs && nowMs < it.endMs }
            ?: programs.firstOrNull()

    /** Monday 00:00 in the viewer's own timezone: the start of this week's programming. */
    fun weekAnchor(
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** The seed changes once a week, when [weekAnchor] moves. */
    fun weekSeed(
        anchorMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = Instant.ofEpochMilli(anchorMs).atZone(zone).toLocalDate().toEpochDay()

    /** Runtime of an entry, with the same clamp the schedule uses. */
    fun OnAirEntry.runtimeMs(): Long =
        runtimeMinutes.coerceIn(MIN_RUNTIME_MINUTES, MAX_RUNTIME_MINUTES) * MILLIS_PER_MINUTE
}
