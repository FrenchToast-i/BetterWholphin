package com.github.damontecres.wholphin.ui.detail.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class LiveTvModelTest {
    private val now = LocalDateTime.of(2026, 11, 17, 20, 0)
    private val sports = channel("Sports", "5", favorite = true)
    private val news = channel("News", "12", favorite = false)
    private val kids = channel("Kids", "20", favorite = true)

    private fun channel(
        name: String,
        number: String,
        favorite: Boolean,
    ) = TvChannel(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        number = number,
        name = name,
        imageUrl = null,
        favorite = favorite,
    )

    private fun program(
        channel: TvChannel,
        start: LocalDateTime,
        end: LocalDateTime,
        name: String = "Programme",
        category: ProgramCategory? = null,
        fake: Boolean = false,
    ) = TvProgram(
        id = UUID.randomUUID(),
        channelId = channel.id,
        start = start,
        end = end,
        startHours = 0f,
        endHours = 1f,
        duration = 60.minutes,
        name = name,
        subtitle = null,
        seasonEpisode = null,
        isRecording = false,
        isSeriesRecording = false,
        isRepeat = false,
        category = if (fake) ProgramCategory.FAKE else category,
    )

    @Test
    fun `filters compose across every Live TV surface`() {
        val liveSports = program(sports, now.minusMinutes(30), now.plusMinutes(30), "Match", ProgramCategory.SPORTS)
        val liveNews = program(news, now.minusMinutes(10), now.plusMinutes(20), "Bulletin", ProgramCategory.NEWS)
        val laterKids = program(kids, now.plusHours(2), now.plusHours(3), "Cartoon", ProgramCategory.KIDS)
        val channels = listOf(sports, news, kids)
        val current = mapOf(sports.id to liveSports, news.id to liveNews)
        val all = mapOf(sports.id to listOf(liveSports), news.id to listOf(liveNews), kids.id to listOf(laterKids))

        val favourites =
            filterChannels(
                channels,
                LiveTvFilters(favoritesOnly = true),
                current,
                all,
                setOf(sports.id),
                now,
            )
        assertEquals(listOf(sports, kids), favourites)

        val liveOnly =
            filterChannels(
                channels,
                LiveTvFilters(liveOnly = true),
                current,
                all,
                setOf(sports.id),
                now,
            )
        assertEquals(listOf(sports, news), liveOnly)

        val sportsOnly =
            filterChannels(
                channels,
                LiveTvFilters(sportsOnly = true),
                current,
                all,
                setOf(sports.id),
                now,
            )
        assertEquals(listOf(sports), sportsOnly)

        val withGuide =
            filterChannels(
                channels,
                LiveTvFilters(hasGuideOnly = true),
                current,
                all,
                setOf(sports.id),
                now,
            )
        assertEquals(listOf(sports, news, kids), withGuide)

        val combination =
            filterChannels(
                channels,
                LiveTvFilters(favoritesOnly = true, liveOnly = true),
                current,
                all,
                setOf(sports.id),
                now,
            )
        assertEquals(listOf(sports), combination)
    }

    @Test
    fun `category filter uses current programme before the full guide`() {
        val currentSports = program(sports, now.minusMinutes(5), now.plusMinutes(55), "Match", ProgramCategory.SPORTS)
        val laterMovie = program(kids, now.plusHours(1), now.plusHours(3), "Film", ProgramCategory.MOVIE)
        val all = mapOf(sports.id to listOf(currentSports), kids.id to listOf(laterMovie))

        val filtered =
            filterChannels(
                listOf(sports, kids),
                LiveTvFilters(category = ProgramCategory.SPORTS),
                mapOf(sports.id to currentSports),
                all,
                emptySet(),
                now,
            )

        assertEquals(listOf(sports), filtered)
    }

    @Test
    fun `live programmes fall back to the loaded guide and skip filler`() {
        val fromGuide = program(news, now.minusMinutes(10), now.plusMinutes(20), "News", ProgramCategory.NEWS)
        val filler = program(sports, now.minusMinutes(10), now.plusMinutes(20), "No data", fake = true)

        val live =
            liveProgramsByChannel(
                currentPrograms = emptyMap(),
                fallbackProgramsByChannel = mapOf(news.id to listOf(fromGuide), sports.id to listOf(filler)),
                now = now,
            )

        assertEquals(mapOf(news.id to fromGuide), live)
    }

    @Test
    fun `starting soon is one real programme per channel inside the window`() {
        val soon = program(sports, now.plusMinutes(20), now.plusMinutes(80), "Soon")
        val later = program(news, now.plusHours(2), now.plusHours(3), "Later")
        val tooLate = program(kids, now.plusHours(5), now.plusHours(6), "Late")
        val duplicateChannelEarlier = program(news, now.plusMinutes(10), now.plusMinutes(40), "Sooner")
        val filler = program(sports, now.plusMinutes(5), now.plusMinutes(15), "Filler", fake = true)

        val result = startingSoon(listOf(soon, later, tooLate, duplicateChannelEarlier, filler), now)

        assertEquals(listOf(duplicateChannelEarlier, soon), result)
    }

    @Test
    fun `channel numbers accept exact prefix and leading zeroes`() {
        assertTrue(channelNumberMatches(news, "12"))
        assertTrue(channelNumberMatches(news, "1"))
        assertTrue(channelNumberMatches(news, "012"))
        assertFalse(channelNumberMatches(news, "2"))
        assertEquals(news, findChannelByNumber(listOf(sports, news), "012"))
        assertNull(findChannelByNumber(listOf(sports, news), "999"))
    }

    @Test
    fun `conflicts report both sides of an overlap`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val third = UUID.randomUUID()
        val conflicted =
            conflictedProgramIds(
                listOf(
                    first to (now to now.plusHours(1)),
                    second to (now.plusMinutes(30) to now.plusHours(2)),
                    third to (now.plusHours(3) to now.plusHours(4)),
                ),
            )

        assertEquals(setOf(first, second), conflicted)
    }

    @Test
    fun `next programme is the earliest real programme after now`() {
        val next = program(news, now.plusMinutes(15), now.plusMinutes(45), "Next")
        val later = program(news, now.plusHours(2), now.plusHours(3), "Later")
        val filler = program(news, now.plusMinutes(5), now.plusMinutes(10), "Filler", fake = true)

        assertEquals(
            next,
            nextProgramForChannel(mapOf(news.id to listOf(filler, later, next)), news.id, now),
        )
    }
}
