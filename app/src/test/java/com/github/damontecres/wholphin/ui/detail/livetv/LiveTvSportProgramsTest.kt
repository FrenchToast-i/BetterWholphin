package com.github.damontecres.wholphin.ui.detail.livetv

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class LiveTvSportProgramsTest {
    private val noon = LocalDateTime.of(2026, 11, 22, 12, 0)

    private fun program(
        category: ProgramCategory?,
        startHour: Long,
        channelId: UUID = UUID.randomUUID(),
    ) = TvProgram(
        id = UUID.randomUUID(),
        channelId = channelId,
        start = noon.plusHours(startHour),
        end = noon.plusHours(startHour + 2),
        startHours = startHour.toFloat(),
        endHours = startHour + 2f,
        duration = 2.hours,
        name = "match",
        subtitle = null,
        seasonEpisode = null,
        isRecording = false,
        isSeriesRecording = false,
        isRepeat = false,
        category = category,
    )

    @Test
    fun `the targeted sport query wins over the guide`() {
        val fixture = program(ProgramCategory.SPORTS, 1)
        val inGuide = program(ProgramCategory.SPORTS, 5)
        val state =
            LiveTvState(
                sportFixtures = listOf(fixture),
                programs = FetchedPrograms(programs = listOf(inGuide)),
            )

        assertEquals(listOf(fixture), state.sportPrograms)
    }

    @Test
    fun `a tuner the query cannot answer for still reads the guide`() {
        val inGuide = program(ProgramCategory.SPORTS, 5)
        val state =
            LiveTvState(
                sportFixtures = emptyList(),
                programs = FetchedPrograms(programs = listOf(inGuide)),
            )

        assertEquals(listOf(inGuide), state.sportPrograms)
    }

    @Test
    fun `news and filler are not sport`() {
        val match = program(ProgramCategory.SPORTS, 2)
        val state =
            LiveTvState(
                sportFixtures =
                    listOf(
                        program(ProgramCategory.NEWS, 1),
                        match,
                        program(ProgramCategory.FAKE, 3),
                        program(null, 4),
                    ),
            )

        assertEquals(listOf(match), state.sportPrograms)
    }

    @Test
    fun `fixtures are ordered by kickoff`() {
        val late = program(ProgramCategory.SPORTS, 8)
        val early = program(ProgramCategory.SPORTS, 1)
        val state = LiveTvState(sportFixtures = listOf(late, early))

        assertEquals(listOf(early, late), state.sportPrograms)
    }

    @Test
    fun `an empty state is an empty list, not a crash`() {
        assertEquals(emptyList<TvProgram>(), LiveTvState().sportPrograms)
    }
}
