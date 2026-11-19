package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * The home renovation moved the page's decisions out of the composable and into [buildHomeFeed].
 * These tests pin the decisions a viewer notices immediately: a real live game leads, the live
 * rail never invents a channel, a series appears once in Keep Going, and the page falls back
 * cleanly when there is no live TV or resume state.
 */
class HomeFeedTest {
    private val now = LocalDateTime.of(2026, 11, 16, 19, 0)

    private fun id(value: Int): UUID = UUID(0, value.toLong())

    private fun movie(
        value: Int,
        name: String = "Movie $value",
    ): BaseItem =
        BaseItem(
            BaseItemDto(
                id = id(value),
                type = BaseItemKind.MOVIE,
                name = name,
            ),
        )

    private fun episode(
        value: Int,
        series: Int,
        name: String = "Episode $value",
    ): BaseItem =
        BaseItem(
            BaseItemDto(
                id = id(value),
                type = BaseItemKind.EPISODE,
                name = name,
                seriesId = id(series),
            ),
        )

    private fun program(
        value: Int,
        channelId: UUID? = id(9_000 + value),
        start: LocalDateTime? = now.minusMinutes(20),
        end: LocalDateTime? = now.plusMinutes(40),
    ): BaseItem =
        BaseItem(
            BaseItemDto(
                id = id(value),
                type = BaseItemKind.PROGRAM,
                name = "Program $value",
                channelId = channelId,
                startDate = start,
                endDate = end,
            ),
        )

    private fun success(
        config: HomeRowConfig,
        items: List<BaseItem?>,
    ): HomeRowLoadingState.Success =
        HomeRowLoadingState.Success(
            title = StringStringProvider("row"),
            items = items,
            viewOptions = config.viewOptions,
            rowType = config,
        )

    private fun feed(
        rows: List<HomeRowLoadingState>,
        configs: List<HomeRowConfig>,
    ): HomeFeed = buildHomeFeed(rows, configs, now)

    @Test
    fun `live programme leads and only airing programmes with channels are kept`() {
        val live = program(1)
        val comingUp = program(2, start = now.plusMinutes(10), end = now.plusMinutes(70))
        val withoutChannel = program(3, channelId = null)
        val televisionChannel = BaseItem(BaseItemDto(id = id(4), type = BaseItemKind.TV_CHANNEL, name = "Channel"))
        val continueWatching = episode(5, series = 50)
        val newlyAdded = movie(6)
        val library = movie(7)

        val configs =
            listOf(
                HomeRowConfig.TvPrograms(),
                HomeRowConfig.ContinueWatchingCombined(),
                HomeRowConfig.RecentlyAdded(id(100)),
                HomeRowConfig.ByParent(id(101)),
            )
        val result =
            feed(
                listOf(
                    success(configs[0], listOf(live, comingUp, withoutChannel, televisionChannel)),
                    success(configs[1], listOf(continueWatching)),
                    success(configs[2], listOf(newlyAdded)),
                    success(configs[3], listOf(library)),
                ),
                configs,
            )

        assertEquals(HomeLeadKind.LIVE, result.leadKind)
        assertEquals(live.id, result.lead?.item?.id)
        assertEquals(listOf(live.id), result.live.map { it.item.id })
        assertEquals(listOf(continueWatching.id), result.keepGoing.map { it.item.id })
        assertEquals(listOf(newlyAdded.id), result.newItems.map { it.item.id })
        assertEquals(listOf(3), result.library.map { it.index })
    }

    @Test
    fun `a guide entry without dates is still a real live programme`() {
        val guideEntry = program(1, start = null, end = null)
        val config = HomeRowConfig.TvPrograms()

        val result = feed(listOf(success(config, listOf(guideEntry))), listOf(config))

        assertEquals(listOf(guideEntry.id), result.live.map { it.item.id })
    }

    @Test
    fun `lead falls back from live to resume to new to the first playable library item`() {
        val resume = episode(1, series = 10)
        val newMovie = movie(2)
        val libraryChannel = BaseItem(BaseItemDto(id = id(3), type = BaseItemKind.TV_CHANNEL, name = "Channel"))
        val libraryMovie = movie(4)

        val resumeConfig = HomeRowConfig.ContinueWatchingCombined()
        val newConfig = HomeRowConfig.RecentlyAdded(id(100))
        val libraryConfig = HomeRowConfig.ByParent(id(101))

        val resumeResult =
            feed(
                listOf(success(resumeConfig, listOf(resume)), success(newConfig, listOf(newMovie))),
                listOf(resumeConfig, newConfig),
            )
        assertEquals(HomeLeadKind.CONTINUE, resumeResult.leadKind)
        assertEquals(resume.id, resumeResult.lead?.item?.id)

        val newResult =
            feed(
                listOf(
                    success(resumeConfig, emptyList()),
                    success(newConfig, listOf(newMovie)),
                    success(libraryConfig, listOf(libraryChannel, libraryMovie)),
                ),
                listOf(resumeConfig, newConfig, libraryConfig),
            )
        assertEquals(HomeLeadKind.NEW, newResult.leadKind)
        assertEquals(newMovie.id, newResult.lead?.item?.id)

        val libraryResult =
            feed(
                listOf(success(libraryConfig, listOf(libraryChannel, libraryMovie))),
                listOf(libraryConfig),
            )
        assertEquals(HomeLeadKind.START, libraryResult.leadKind)
        assertEquals(libraryMovie.id, libraryResult.lead?.item?.id)
    }

    @Test
    fun `keep going contains one card per series and never repeats the lead`() {
        val first = episode(1, series = 10)
        val sameSeries = episode(2, series = 10)
        val second = episode(3, series = 20)
        val film = movie(4)
        val config = HomeRowConfig.ContinueWatchingCombined()

        val result = feed(listOf(success(config, listOf(first, sameSeries, second, film))), listOf(config))

        assertEquals(first.id, result.lead?.item?.id)
        assertEquals(listOf(id(20), film.id), result.keepGoing.map { it.item.data.seriesId ?: it.item.id })
        assertEquals(
            result.keepGoing.size,
            result.keepGoing.map { it.item.data.seriesId ?: it.item.id }.distinct().size,
        )
    }

    @Test
    fun `new aggregates every recently added row and removes duplicate ids`() {
        val first = movie(1, "First")
        val duplicate = movie(1, "First again")
        val second = movie(2, "Second")
        val firstConfig = HomeRowConfig.RecentlyAdded(id(100))
        val secondConfig = HomeRowConfig.RecentlyAdded(id(101))

        val result =
            feed(
                listOf(
                    success(firstConfig, listOf(first, second)),
                    success(secondConfig, listOf(duplicate)),
                ),
                listOf(firstConfig, secondConfig),
            )

        assertEquals(first.id, result.lead?.item?.id)
        assertEquals(listOf(second.id), result.newItems.map { it.item.id })
        assertTrue(result.library.isEmpty())
    }

    @Test
    fun `new does not repeat a series already in keep going`() {
        val lead = episode(1, series = 10)
        val keep = episode(2, series = 20)
        val newEpisode = episode(3, series = 20)
        val newMovie = movie(4)
        val watchConfig = HomeRowConfig.ContinueWatchingCombined()
        val newConfig = HomeRowConfig.RecentlyAdded(id(100))

        val result =
            feed(
                listOf(
                    success(watchConfig, listOf(lead, keep)),
                    success(newConfig, listOf(newEpisode, newMovie)),
                ),
                listOf(watchConfig, newConfig),
            )

        assertEquals(listOf(keep.id), result.keepGoing.map { it.item.id })
        assertEquals(listOf(newMovie.id), result.newItems.map { it.item.id })
    }

    @Test
    fun `only non-empty configured library rows survive below the editorial sections`() {
        val watch = HomeRowConfig.ContinueWatchingCombined()
        val new = HomeRowConfig.RecentlyAdded(id(100))
        val live = HomeRowConfig.TvPrograms()
        val library = HomeRowConfig.ByParent(id(101))

        val result =
            feed(
                listOf(
                    success(watch, listOf(episode(1, series = 10))),
                    HomeRowLoadingState.Loading(StringStringProvider("new")),
                    success(live, emptyList()),
                    success(library, listOf(movie(2))),
                ),
                listOf(watch, new, live, library),
            )

        assertEquals(listOf(3), result.library.map { it.index })
    }

    @Test
    fun `rows still loading or failed do not invent content`() {
        val result =
            feed(
                listOf(
                    HomeRowLoadingState.Pending(StringStringProvider("one")),
                    HomeRowLoadingState.Loading(StringStringProvider("two")),
                    HomeRowLoadingState.Error(StringStringProvider("three"), message = "offline"),
                ),
                listOf(
                    HomeRowConfig.ContinueWatchingCombined(),
                    HomeRowConfig.RecentlyAdded(id(100)),
                    HomeRowConfig.ByParent(id(101)),
                ),
            )

        assertNull(result.lead)
        assertTrue(result.live.isEmpty())
        assertTrue(result.keepGoing.isEmpty())
        assertTrue(result.newItems.isEmpty())
        assertTrue(result.library.isEmpty())
        assertTrue(result.isEmpty)
    }

    @Test
    fun `editorial rails are capped so the page does not become a second library`() {
        val liveConfig = HomeRowConfig.TvPrograms()
        val watchConfig = HomeRowConfig.ContinueWatchingCombined()
        val newConfig = HomeRowConfig.RecentlyAdded(id(100))
        val live = (1..8).map { program(it) }
        val watch = (20..31).map { episode(it, series = 1_000 + it) }
        val new = (40..49).map { movie(it) }

        val result =
            feed(
                listOf(
                    success(liveConfig, live),
                    success(watchConfig, watch),
                    success(newConfig, new),
                ),
                listOf(liveConfig, watchConfig, newConfig),
            )

        assertEquals(6, result.live.size)
        assertEquals(10, result.keepGoing.size)
        assertEquals(8, result.newItems.size)
    }

    @Test
    fun `an episode is not duplicated when the same series arrives twice`() {
        val first = episode(1, series = 10)
        val second = episode(2, series = 10)
        val config = HomeRowConfig.NextUp()

        val result = feed(listOf(success(config, listOf(first, second))), listOf(config))

        assertEquals(first.id, result.lead?.item?.id)
        assertTrue(result.keepGoing.isEmpty())
    }
}
