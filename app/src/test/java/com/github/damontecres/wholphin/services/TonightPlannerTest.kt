package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * Pins the arithmetic behind Tonight: the evening is a deadline, a title that fits beats a better
 * title that does not, and every pick can say why it is there.
 */
class TonightPlannerTest {
    private val bedtime = 23 * 60 + 30

    private fun at(
        hour: Int,
        minute: Int = 0,
        dayOfMonth: Int = 10,
    ) = LocalDateTime.of(2026, 11, dayOfMonth, hour, minute)

    private fun candidate(
        id: Int,
        name: String = "Item $id",
        minutes: Long? = 90,
        progress: Double = 0.0,
        rating: Double? = 7.0,
        genres: List<String> = emptyList(),
        seriesName: String? = null,
        seriesId: Int? = null,
        isEpisode: Boolean = false,
    ) = TonightCandidate(
        id = UUID(0, id.toLong()),
        title = name,
        seriesName = seriesName,
        seriesId = seriesId?.let { UUID(0, it.toLong()) },
        minutes = minutes,
        progress = progress,
        rating = rating,
        genres = genres,
        isEpisode = isEpisode,
    )

    @Test
    fun aWeeknightWindowRunsUntilBedtime() {
        val window = TonightPlanner.window(at(20), bedtime)
        assertEquals(210L, window.minutes)
        assertFalse(window.late)
        assertFalse(window.weekend)
        assertEquals(at(23, 30), window.endsAt)
    }

    @Test
    fun afterBedtimeTheEveningBecomesOneSitting() {
        val window = TonightPlanner.window(at(23, 45), bedtime)
        assertTrue(window.late)
        assertEquals(TonightPlanner.LATE_WINDOW_MINUTES, window.minutes)
    }

    @Test
    fun theSmallHoursAreAlwaysLate() {
        val window = TonightPlanner.window(at(2), bedtime)
        assertTrue(window.late)
        assertEquals(TonightPlanner.SMALL_HOURS_WINDOW_MINUTES, window.minutes)
    }

    @Test
    fun fridayAndSaturdayRunLater() {
        // 2026-11-13 is a Friday.
        val window = TonightPlanner.window(at(22, dayOfMonth = 13), bedtime)
        assertTrue(window.weekend)
        assertEquals(135L, window.minutes)
        assertEquals(at(0, 15, dayOfMonth = 14), window.endsAt)
    }

    @Test
    fun aBedtimeAfterMidnightIsTomorrow() {
        val window = TonightPlanner.window(at(20), 30) // 00:30
        assertEquals(270L, window.minutes)
        assertFalse(window.late)
        assertEquals(at(0, 30, dayOfMonth = 11), window.endsAt)
    }

    @Test
    fun aBedtimeShortlyAfterMidnightIsNotStretchedIntoTheSmallHours() {
        // 00:15 with a 00:30 bedtime is a quarter of an hour, not the hour the small-hours
        // window would hand an evening that is nearly over.
        val window = TonightPlanner.window(at(0, 15), 30)
        assertFalse(window.late)
        // The window is the time that is actually left, not a stretched minimum: the page says
        // "15m" and offers a short, and the deadline it prints is the viewer's own bedtime.
        assertEquals(15L, window.minutes)
        assertEquals(at(0, 30), window.endsAt)
    }

    @Test
    fun aBedtimeAtThreeAmIsItsOwnWindow() {
        val window = TonightPlanner.window(at(2), 3 * 60)
        assertFalse(window.late)
        assertEquals(60L, window.minutes)
    }

    @Test
    fun aLongGapIsCappedSoNoonDoesNotPlanTwelveHours() {
        val window = TonightPlanner.window(at(6), bedtime)
        assertEquals(TonightPlanner.MAX_WINDOW_MINUTES, window.minutes)
    }

    @Test
    fun aTitleThatFitsBeatsABetterTitleThatDoesNot() {
        val short = candidate(1, "Short", minutes = 80, rating = 6.0)
        val epic = candidate(2, "Epic", minutes = 200, rating = 9.5)
        val window = TonightPlanner.window(at(22), bedtime) // 90 minutes
        assertEquals(short, TonightPlanner.pick(listOf(epic, short), window, emptyMap()))
    }

    @Test
    fun tasteBreaksTiesBetweenTitlesThatBothFit() {
        val drama = candidate(1, "Drama", minutes = 60, rating = 7.0, genres = listOf("Drama"))
        val comedy = candidate(2, "Comedy", minutes = 60, rating = 7.0, genres = listOf("Comedy"))
        val window = TonightPlanner.window(at(20), bedtime)
        val pick = TonightPlanner.pick(listOf(drama, comedy), window, mapOf("comedy" to 1.0))
        assertEquals(comedy, pick)
    }

    @Test
    fun aStartedTitleIsPreferredWhenTheAlternativesAreClose() {
        val started = candidate(1, "Started", minutes = 60, progress = 20.0)
        val fresh = candidate(2, "Fresh", minutes = 60, progress = 0.0)
        val window = TonightPlanner.window(at(20), bedtime)
        assertEquals(started, TonightPlanner.pick(listOf(started, fresh), window, emptyMap()))
    }

    @Test
    fun dismissedTitlesAreNotOfferedAgain() {
        val first = candidate(1, "First", minutes = 60)
        val second = candidate(2, "Second", minutes = 60)
        val window = TonightPlanner.window(at(20), bedtime)
        val pick =
            TonightPlanner.pick(
                listOf(first, second),
                window,
                emptyMap(),
                dismissed = setOf(first.id),
            )
        assertEquals(second, pick)
    }

    @Test
    fun titlesWithoutARuntimeCannotLeadThePage() {
        val unknown = candidate(1, "Unknown", minutes = null)
        val known = candidate(2, "Known", minutes = 60)
        val window = TonightPlanner.window(at(20), bedtime)
        assertEquals(known, TonightPlanner.pick(listOf(unknown, known), window, emptyMap()))
        assertNull(TonightPlanner.pick(listOf(unknown), window, emptyMap()))
    }

    @Test
    fun anEffectivelyFinishedTitleIsNotRecommended() {
        val finished = candidate(1, "Finished", minutes = 10, progress = 98.0)
        assertNull(TonightPlanner.pick(listOf(finished), TonightPlanner.window(at(20), bedtime), emptyMap()))
    }

    @Test
    fun aResumeSaysContinueAndNamesTheShowForAnEpisode() {
        val episode = candidate(1, progress = 30.0, seriesName = "Severance", isEpisode = true)
        assertEquals(
            TonightReason.Continue("Severance"),
            TonightPlanner.reason(episode, emptyMap()),
        )
    }

    @Test
    fun aNextEpisodeNamesItsSeries() {
        val episode = candidate(1, seriesName = "Severance", isEpisode = true)
        assertEquals(
            TonightReason.NextEpisode("Severance"),
            TonightPlanner.reason(episode, emptyMap()),
        )
    }

    @Test
    fun anUntouchedTitleExplainsItselfByGenre() {
        val movie = candidate(1, genres = listOf("Comedy", "Drama"))
        assertEquals(
            TonightReason.MatchesTaste("Comedy"),
            TonightPlanner.reason(movie, mapOf("comedy" to 0.9, "drama" to 0.3)),
        )
    }

    @Test
    fun aWeakGenreIsNotClaimedAsTheReason() {
        val movie = candidate(1, genres = listOf("Drama"))
        assertEquals(
            TonightReason.TopPick,
            TonightPlanner.reason(movie, mapOf("drama" to 0.2)),
        )
    }

    @Test
    fun theFinishLineKeepsOnlySeriesWithOneEpisodeLeft() {
        val aboutToFinish = candidate(1, "Last one", seriesId = 10)
        val halfway = candidate(2, "Middle", seriesId = 11)
        val movie = candidate(3, "A film")
        val finishLine =
            TonightPlanner.finishLine(
                nextUp = listOf(aboutToFinish, halfway, movie),
                seriesWithOneLeft = setOf(UUID(0, 10)),
            )
        assertEquals(listOf("Last one"), finishLine.map { it.title })
    }

    @Test
    fun theFinishLineListsEachSeriesOnce() {
        // Two stranded episodes of the same show arrive from a paged next-up query; the shelf is a
        // list of series to finish, not a list of episodes.
        val finishLine =
            TonightPlanner.finishLine(
                nextUp =
                    listOf(
                        candidate(1, "First", seriesId = 10),
                        candidate(2, "Second", seriesId = 10),
                        candidate(3, "Other", seriesId = 12),
                    ),
                seriesWithOneLeft = setOf(UUID(0, 10), UUID(0, 12)),
                limit = 12,
            )
        assertEquals(listOf("First", "Other"), finishLine.map { it.title })
    }

    @Test
    fun theDeadlineBarFractionIsClamped() {
        val window = TonightPlanner.window(at(20), bedtime) // 210 minutes
        assertEquals(0.5f, TonightPlanner.windowFraction(candidate(1, minutes = 105), window), 0.001f)
        assertEquals(1f, TonightPlanner.windowFraction(candidate(2, minutes = 400), window), 0.001f)
        assertEquals(0f, TonightPlanner.windowFraction(candidate(3, minutes = null), window), 0.001f)
    }
}
