package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDateTime
import java.util.UUID

/**
 * One real item on the home page, together with the configured row and card it came from.
 *
 * The position is kept because the page's existing click/long-click behaviour is row-aware:
 * continue-watching cards can start playback on a single click while library cards open details.
 * Promoting an item into the lead or aggregating it into New must not lose that context.
 */
internal data class HomeFeedItem(
    val item: BaseItem,
    val position: RowColumn,
)

/** The item the top of the page is built around. */
internal enum class HomeLeadKind {
    LIVE,
    CONTINUE,
    NEW,
    START,
}

/**
 * A configured library row that is drawn below the three editorial sections.
 *
 * A close look at the home page revealed that the configured rows are already the household's
 * own ordering; the renovation should add a lead, Keep Going and New above them, not replace them.
 */
internal data class HomeLibraryRow(
    val index: Int,
    val row: HomeRowLoadingState.Success,
)

/**
 * The home page's real content, separated from how it is drawn.
 *
 * The page used to be a list of rows in configuration order. That made a row's importance depend
 * on where it happened to be configured, not on what is happening now: a live game could sit in a
 * horizontal channel strip below two resume posters, and a series could appear in both Continue
 * Watching and Next Up. This model gives the page a stable editorial spine while leaving every
 * source row real:
 *
 * 1. one lead (live now, then resume, then new, then the first library item),
 * 2. actual live programmes from the tuner,
 * 3. one Keep Going card per series,
 * 4. one New shelf aggregated from the recently-added rows,
 * 5. the configured library rows that were not consumed above.
 *
 * It is deliberately pure. The matching is the risky part — a duplicate series or a made-up live
 * card is immediately visible — so it is kept out of the composable and covered by unit tests.
 */
internal data class HomeFeed(
    val lead: HomeFeedItem?,
    val leadKind: HomeLeadKind,
    val live: List<HomeFeedItem>,
    val keepGoing: List<HomeFeedItem>,
    val newItems: List<HomeFeedItem>,
    val library: List<HomeLibraryRow>,
) {
    val isEmpty: Boolean
        get() = lead == null && live.isEmpty() && keepGoing.isEmpty() && newItems.isEmpty() && library.isEmpty()
}

private data class HomeSource(
    val index: Int,
    val state: HomeRowLoadingState,
    val config: HomeRowConfig?,
)

/**
 * Build the page from the rows that have actually loaded.
 *
 * Rows still loading or failed are not turned into content: this page never fabricates a shelf.
 * The caller can show its skeleton while any core row is outstanding, and the real sections
 * appear as their rows arrive.
 */
internal fun buildHomeFeed(
    homeRows: List<HomeRowLoadingState>,
    rowConfigs: List<HomeRowConfig>,
    now: LocalDateTime,
): HomeFeed {
    val sources =
        homeRows.mapIndexed { index, row ->
            HomeSource(index, row, rowConfigs.getOrNull(index))
        }

    val successful = sources.mapNotNull { source -> source.asSuccess() }

    val live =
        successful
            .filter { it.config is HomeRowConfig.TvPrograms }
            .flatMap { it.items() }
            .filter { it.item.data.channelId != null && it.item.isProgramType() }
            .filter { it.item.isAiringAt(now) }
            .distinctBy { it.item.id }
            .take(MAX_LIVE)

    val watching =
        successful
            .filter { it.isWatchRow() }
            .flatMap { it.items() }

    val new =
        successful
            .filter { it.config is HomeRowConfig.RecentlyAdded }
            .flatMap { it.items() }
            .distinctBy { it.item.id }

    val library =
        successful
            .filterNot { it.isConsumedRow() }
            .filter { source -> source.items().isNotEmpty() }
            .map { HomeLibraryRow(it.index, it.state as HomeRowLoadingState.Success) }

    val leadLive = live.firstOrNull()
    val leadKeep = watching.firstOrNull()
    val leadNew = new.firstOrNull()
    val leadLibrary = library.firstOrNull()?.firstItem()

    val (lead, leadKind) =
        when {
            leadLive != null -> leadLive to HomeLeadKind.LIVE
            leadKeep != null -> leadKeep to HomeLeadKind.CONTINUE
            leadNew != null -> leadNew to HomeLeadKind.NEW
            leadLibrary != null -> leadLibrary to HomeLeadKind.START
            else -> null to HomeLeadKind.START
        }

    val leadKey = lead?.item?.seriesKey()
    val keepGoing =
        watching
            .filterNot { it.item.id == lead?.item?.id }
            .filterNot { leadKey != null && it.item.seriesKey() == leadKey }
            .distinctBy { it.item.seriesKey() }
            .take(MAX_KEEP_GOING)

    val keepKeys = keepGoing.map { it.item.seriesKey() }.toSet()
    val newItems =
        new
            .filterNot { it.item.id == lead?.item?.id }
            .filterNot { leadKey != null && it.item.seriesKey() == leadKey }
            .filterNot { it.item.seriesKey() in keepKeys }
            .take(MAX_NEW)

    return HomeFeed(
        lead = lead,
        leadKind = leadKind,
        live = live,
        keepGoing = keepGoing,
        newItems = newItems,
        library = library,
    )
}

private fun HomeSource.asSuccess(): HomeSource? = if (state is HomeRowLoadingState.Success) this else null

private fun HomeSource.items(): List<HomeFeedItem> {
    val success = state as? HomeRowLoadingState.Success ?: return emptyList()
    return success.items.mapIndexedNotNull { column, item ->
        item?.let { HomeFeedItem(it, RowColumn(index, column)) }
    }
}

private fun HomeSource.isWatchRow(): Boolean =
    config is HomeRowConfig.ContinueWatching ||
        config is HomeRowConfig.NextUp ||
        config is HomeRowConfig.ContinueWatchingCombined

private fun HomeSource.isConsumedRow(): Boolean =
    isWatchRow() ||
        config is HomeRowConfig.RecentlyAdded ||
        config is HomeRowConfig.TvPrograms

private fun HomeLibraryRow.firstItem(): HomeFeedItem? {
    val first =
        row.items
            .withIndex()
            .firstOrNull { it.value?.isLeadPlayable() == true }
            ?: return null
    return HomeFeedItem(requireNotNull(first.value), RowColumn(index, first.index))
}

/**
 * One series, one Keep Going card.
 *
 * Episodes carry a series id; a movie is its own series. Using the item id alone would let a
 * resume episode and its Next Up sibling occupy two cards, which is exactly the duplication the
 * old Continue Watching + Next Up stack produced.
 */
private fun BaseItem.seriesKey(): UUID = data.seriesId ?: id

private fun BaseItem.isProgramType(): Boolean =
    type == BaseItemKind.PROGRAM || type == BaseItemKind.TV_PROGRAM

/**
 * A library card can be playable and still be the wrong thing to lead the page with. A bare TV
 * channel tunes a live stream rather than starting a title, so it is not a play target here.
 */
private fun BaseItem.isLeadPlayable(): Boolean =
    playable && type != BaseItemKind.TV_CHANNEL

/**
 * The guide's airing flag is the primary source of truth; the dates are a second check where the
 * server provides them. A program without dates is still a real guide entry, so it is not
 * discarded — but it is only ever the tuner's own item, never an invented one.
 */
private fun BaseItem.isAiringAt(now: LocalDateTime): Boolean {
    val start = data.startDate ?: return true
    val end = data.endDate ?: return true
    return !now.isBefore(start) && now.isBefore(end)
}

private const val MAX_LIVE = 6
private const val MAX_KEEP_GOING = 10
private const val MAX_NEW = 8
