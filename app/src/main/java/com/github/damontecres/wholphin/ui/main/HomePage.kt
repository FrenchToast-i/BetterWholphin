package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.cards.ViewMoreCard
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FocusableItemRow
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.init()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

            val playlistState by playlistViewModel.playlistState.collectAsState()
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            homeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        val row =
                            (homeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                        val canRemoveContinueWatching =
                            row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        val canRemoveNextUp =
                            row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete =
                                    viewModel.canDelete(
                                        item,
                                        preferences.appPreferences,
                                    ),
                                canRemoveContinueWatching = canRemoveContinueWatching,
                                canRemoveNextUp = canRemoveNextUp,
                                actions =
                                    ContextMenuActions(
                                        navigateTo = viewModel.navigationManager::navigateTo,
                                        onClickWatch = viewModel::setWatched,
                                        onClickFavorite = viewModel::setFavorite,
                                        onClickAddPlaylist = { itemId ->
                                            playlistViewModel.loadPlaylists()
                                            showPlaylistDialog = itemId
                                        },
                                        onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                                        onDeleteItem = {
                                            viewModel.deleteItem(position, it)
                                        },
                                        onChooseVersion = { _, _ ->
                                            // Not supported on this page
                                        },
                                        onChooseTracks = {
                                            // Not supported on this page
                                        },
                                        onShowOverview = {
                                            overviewDialog = ItemDetailsDialogInfo(it)
                                        },
                                        onClearChosenStreams = {},
                                        onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                                    ),
                            )
                    }
                }
            val onClickPlay =
                remember {
                    { _: RowColumn, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    }
                }
            val onClickViewMore =
                remember {
                    { _: RowColumn, row: HomeRowLoadingState.Success ->
                        viewModel.navigationManager.navigateTo(
                            Destination.MoreHomeRow(row.title, row.rowType!!, row.items.size),
                        )
                    }
                }

            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = onFocusPosition,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = true,
                onClickViewMore = onClickViewMore,
                // The home page is a stack of the viewer's own rows. Other callers (a series'
                // recommendations, the home settings preview) still want the focused-item
                // header, so the page opts out rather than the component dropping it.
                showHeader = false,
                modifier = modifier,
            )
            overviewDialog?.let { info ->
                ItemDetailsDialog(
                    info = info,
                    showFilePath = false,
                    onDismissRequest = { overviewDialog = null },
                )
            }
            showContextMenu?.let { contextMenu ->
                ContextMenuDialog(
                    onDismissRequest = { showContextMenu = null },
                    getMediaSource = null,
                    contextMenu = contextMenu,
                    preferredSubtitleLanguage = null,
                )
            }
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    onSearch = playlistViewModel::loadPlaylists,
                    elevation = 3.dp,
                )
            }
        }
    }
}

/**
 * One child of the home page's lazy list: a library row, or a discovery shelf drawn after the
 * watch row. A shelf is a separate list entry so the page can scroll it into view on its own.
 */
private sealed interface HomeListEntry {
    val key: String

    data class Row(
        val index: Int,
    ) : HomeListEntry {
        override val key: String get() = "row_$index"
    }

    data class Shelf(
        val index: Int,
    ) : HomeListEntry {
        override val key: String get() = "shelf_$index"
    }

    data class TrailingShelf(
        val index: Int,
    ) : HomeListEntry {
        override val key: String get() = "trailing_shelf_$index"
    }

    /**
     * A quiet section caption. The home page is a stack of unrelated shelves, and without a
     * label the discovery rails read as if they were library rows that happen to be full of
     * films the viewer does not own.
     */
    data class Label(
        val id: String,
        val text: String,
    ) : HomeListEntry {
        override val key: String get() = "label_$id"
    }
}

val HomeRowLoadingState?.isContinueWatchingNextUp: Boolean
    get() =
        (this as? HomeRowLoadingState.Success).let { row ->
            row?.rowType is HomeRowConfig.ContinueWatching ||
                row?.rowType is HomeRowConfig.NextUp ||
                row?.rowType is HomeRowConfig.ContinueWatchingCombined
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    showViewMore: Boolean,
    /**
     * Whether the page draws its fixed header above the rows. Home sets this to false: the
     * focused card's artwork is already the backdrop, and the 200dp pane left a 540dp television
     * showing exactly one row of its own library.
     */
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
    listState: LazyListState = rememberLazyListState(),
    takeFocus: Boolean = true,
    showEmptyRows: Boolean = false,
    headerComposable: (@Composable (focusedItem: BaseItem?) -> Unit)? = null,
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit = { _, _ -> },
    /**
     * Rows that are not library rows — the discovery shelves — drawn after the watch row.
     *
     * One lazy item each rather than one item containing all of them: a block taller than the
     * viewport cannot be scrolled fully into view, so stacking two shelves in one slot left the
     * bottom row sliced by the screen edge every time focus reached it.
     *
     * Each shelf reports the item its focus is on. Shelves of real library items (For You) report
     * the focused card, so the hero shows that exact title; a discovery shelf whose posters are
     * not library items (TMDB) reports the seed it is recommending from. The wrapper clears the
     * report when focus leaves the shelf, which is what sends the hero back to the row above.
     */
    shelves: List<@Composable (reportFocus: (BaseItem?) -> Unit) -> Unit> = emptyList(),
    /**
     * Shelves drawn after every configured library row. Requests and the Top 10 are the end of
     * the page, not the middle of it: they used to sit in the same block as the personal rails,
     * so five discovery shelves stacked under the watch row and pushed the viewer's own library
     * below the fold.
     */
    trailingShelves: List<@Composable (reportFocus: (BaseItem?) -> Unit) -> Unit> = emptyList(),
    shelfSectionLabel: String? = null,
    librarySectionLabel: String? = null,
    librarySectionStart: Int = -1,
) {
    val rowFocusedItem =
        remember(homeRows, position) {
            (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(
                position.column,
            )
        }

    // Shelf cards are not part of `position`, so they cannot report through it. The hero follows
    // the focused shelf card instead of keeping whatever library row was focused last.
    var shelfFocusedItem by remember { mutableStateOf<BaseItem?>(null) }
    val focusedItem = shelfFocusedItem ?: rowFocusedItem

    // After the watch row when there is one, otherwise at the end of the stack: a home page the
    // viewer arranged without a continue-watching row should not have a discovery rail pushed into
    // the middle of it.
    val shelfAfterRow =
        remember(homeRows, shelves.size) {
            if (shelves.isEmpty()) {
                null
            } else {
                homeRows
                    .indexOfFirst { it.isContinueWatchingNextUp }
                    .takeIf { it >= 0 }
                    ?: homeRows.lastIndex.takeIf { it >= 0 }
            }
        }

    // Rows and shelves as one ordered list of entries, so each shelf is its own lazy item and
    // can be scrolled into view on its own. Built from indices only: it holds no lambdas, so it
    // is cheap and cannot go stale.
    val entries: List<HomeListEntry> =
        buildList {
            homeRows.forEachIndexed { index, _ ->
                if (index == librarySectionStart && librarySectionLabel != null) {
                    add(HomeListEntry.Label("library", librarySectionLabel))
                }
                add(HomeListEntry.Row(index))
                if (index == shelfAfterRow) {
                    if (shelves.isNotEmpty() && shelfSectionLabel != null) {
                        add(HomeListEntry.Label("shelves", shelfSectionLabel))
                    }
                    shelves.indices.forEach { add(HomeListEntry.Shelf(it)) }
                }
            }
            trailingShelves.indices.forEach { add(HomeListEntry.TrailingShelf(it)) }
        }

    val rowFocusRequesters = remember(homeRows.size) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }

    val currentPosition by rememberUpdatedState(position)
    val currentOnFocusPosition by rememberUpdatedState(onFocusPosition)
    val currentOnClickPlay by rememberUpdatedState(onClickPlay)

    if (takeFocus) {
        // Rows are drawn as placeholders the moment the page opens and fill in as their queries
        // return, so focus may not be given to a row that is still a skeleton: that placeholder
        // leaves the composition when the real row arrives, and the D-pad's next press then
        // searches from nothing and lands on the first discovery shelf — scrolling the whole home
        // page past its own library rows. Wait for a row with real cards in it.
        val rowHasCards: (HomeRowLoadingState?) -> Boolean = { row ->
            row is HomeRowLoadingState.Success && row.items.isNotEmpty()
        }
        LaunchedEffect(homeRows) {
            if (!firstFocused && homeRows.isNotEmpty()) {
                val preferred = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
                val index =
                    preferred.takeIf { rowHasCards(homeRows.getOrNull(it)) }
                        ?: homeRows.indexOfFirst(rowHasCards)
                if (index >= 0) {
                    // Anchor the list at its first item before focus is taken. The rows arrive at
                    // different times, and by the time the first card can hold focus the list can
                    // already have drifted a few tens of dp — enough to slice the first row's
                    // heading off the top of the page on open.
                    listState.scrollToItem(0)
                    // The row's own composition is what attaches this requester, and a row can be
                    // in the data before its cards are laid out. Retry rather than give up: a
                    // request that lands on nothing must not be recorded as "focus was taken",
                    // because the page would then have no focused control at all and every
                    // D-pad press would do nothing until the viewer left the page.
                    val requester = rowFocusRequesters.getOrNull(index)
                    repeat(10) {
                        val taken = requester != null && runCatching { requester.requestFocus() }.isSuccess
                        if (taken) {
                            // The first focus is not a scroll. `ScrollToTopBringIntoViewSpec`
                            // keeps a row's heading visible while the viewer moves between
                            // shelves, but on the page's first focus the list is already at the
                            // top, and aligning the focused row to that edge slid the heading —
                            // and half of the row above it — off the screen before the viewer
                            // had read anything. Use the default spec for that one request.
                            delay(120)
                            firstFocused = true
                            return@LaunchedEffect
                        }
                        delay(50)
                    }
                }
            }
        }
    }
    LaunchedEffect(onUpdateBackdrop, focusedItem) {
        focusedItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .focusProperties {
                        onEnter = {
                            rowFocusRequesters.getOrNull(currentPosition.row)?.tryRequestFocus()
                        }
                    }.fillMaxSize(),
        ) {
            if (showHeader) {
                val header: @Composable (BaseItem?) -> Unit =
                    headerComposable ?: { focusedItem ->
                        HomePageHeader(
                            item = focusedItem,
                            showLogo = showLogo,
                            modifier =
                                if (LocalLiquidGlass.current) {
                                    HeaderUtils.glassModifier
                                } else {
                                    HeaderUtils.modifier
                                },
                        )
                    }
                header.invoke(focusedItem)
            }

            val density = LocalDensity.current
            val spaceAbovePx =
                remember(density) {
                    with(density) {
                        // Room for a row's heading when a row is scrolled to the top: a title
                        // (36dp), its gap, and — on the discovery shelf — the required TMDB
                        // credit under it (14dp) plus the shelf's own top padding. At the old
                        // 50dp the shelf's heading was sliced in half every time focus reached
                        // its posters, and a clipped credit is a clipped attribution.
                        70.dp.toPx()
                    }
                }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides
                    if (firstFocused) {
                        ScrollToTopBringIntoViewSpec(spaceAbovePx)
                    } else {
                        defaultBringIntoViewSpec
                    },
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = Cards.height2x3,
                        ),
                    modifier =
                        Modifier
                            .focusRestorer(),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        if (entry is HomeListEntry.Label) {
                            HomeSectionLabel(entry.text)
                            return@items
                        }
                        if (entry is HomeListEntry.Shelf || entry is HomeListEntry.TrailingShelf) {
                            val shelf =
                                if (entry is HomeListEntry.Shelf) {
                                    shelves[entry.index]
                                } else {
                                    trailingShelves[(entry as HomeListEntry.TrailingShelf).index]
                                }
                            Box(
                                modifier =
                                    Modifier.onFocusChanged {
                                        if (!it.hasFocus) shelfFocusedItem = null
                                    },
                            ) {
                                shelf.invoke { item -> shelfFocusedItem = item }
                            }
                            return@items
                        }
                        val rowIndex = (entry as HomeListEntry.Row).index
                        val row = homeRows[rowIndex]
                        val rowModifier =
                            Modifier
                                .animateItem(placementSpec = null)
                                .padding(bottom = 8.dp)
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides defaultBringIntoViewSpec,
                        ) {
                            when (val r = row) {
                                is HomeRowLoadingState.Loading,
                                is HomeRowLoadingState.Pending,
                                -> {
                                    FocusableItemRow(
                                        title = r.title.getString(),
                                        subtitle = stringResource(R.string.loading),
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Error -> {
                                    FocusableItemRow(
                                        title = r.title.getString(),
                                        subtitle = r.localizedMessage,
                                        isError = true,
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Success -> {
                                    if (row.items.isNotEmpty()) {
                                        val viewOptions = row.viewOptions
                                        ItemRow(
                                            title = row.title.getString(),
                                            items = row.items,
                                            onClickItem =
                                                remember(rowIndex, onClickItem) {
                                                    { index, item ->
                                                        onClickItem.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                index,
                                                            ),
                                                            item,
                                                        )
                                                    }
                                                },
                                            onLongClickItem =
                                                remember(rowIndex, onLongClickItem) {
                                                    { index, item ->
                                                        onLongClickItem.invoke(
                                                            RowColumn(rowIndex, index),
                                                            item,
                                                        )
                                                    }
                                                },
                                            modifier =
                                                rowModifier
                                                    .fillMaxWidth()
                                                    .focusGroup()
                                                    .focusRequester(rowFocusRequesters[rowIndex]),
                                            horizontalPadding = viewOptions.spacing.dp,
                                            cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                                val onFocus =
                                                    remember(rowIndex, index) {
                                                        { isFocused: Boolean ->
                                                            if (isFocused) {
                                                                currentOnFocusPosition(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        index,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                val onKey =
                                                    remember(item) {
                                                        { event: KeyEvent ->
                                                            if (isPlayKeyUp(event) && item?.type?.playable == true) {
                                                                Timber.v("Clicked play on ${item.id}")
                                                                currentOnClickPlay(
                                                                    currentPosition,
                                                                    item,
                                                                )
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                    }
                                                HomePageCardContent(
                                                    index = index,
                                                    item = item,
                                                    onClick = onClick,
                                                    onLongClick = onLongClick,
                                                    viewOptions = viewOptions,
                                                    modifier =
                                                        cardModifier
                                                            .onFocusChanged { onFocus(it.isFocused) }
                                                            .onKeyEvent { onKey(it) },
                                                )
                                            },
                                            showViewMore = showViewMore && row.showViewMore,
                                            viewMoreCardContent = { mod ->
                                                HomePageViewMoreCard(
                                                    isEpisode = row.items.last()?.type == BaseItemKind.EPISODE,
                                                    onClick = {
                                                        onClickViewMore.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                r.items.size,
                                                            ),
                                                            r,
                                                        )
                                                    },
                                                    onLongClick = {},
                                                    viewOptions = viewOptions,
                                                    modifier =
                                                        mod.onFocusChanged {
                                                            if (it.isFocused) {
                                                                currentOnFocusPosition.invoke(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        r.items.size,
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                )
                                            },
                                        )
                                    } else if (showEmptyRows) {
                                        FocusableItemRow(
                                            title = r.title.getString(),
                                            subtitle = stringResource(R.string.no_results),
                                            modifier = rowModifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
    }
}

/**
 * A quiet caption between the page's blocks.
 *
 * No pill, no glass: the sections are an ordering aid, not another object competing with the
 * shelves. The tracking is what makes a small caps line read as structure from the sofa.
 */
@Composable
private fun HomeSectionLabel(text: String) {
    val glass = LocalLiquidGlass.current
    Text(
        text = text.uppercase(),
        style = if (glass) GlassType.sectionLabel else MaterialTheme.typography.titleSmall,
        color =
            if (glass) {
                Color.White.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (glass) 0.dp else 16.dp,
                    top = 14.dp,
                    bottom = 2.dp,
                ),
    )
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val title =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.EPISODE,
                BaseItemKind.SEASON,
                -> item.data.seriesName

                else -> item?.title
            }
        }
    val subtitle =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                -> item.name

                else -> null
            }
        }
    HomePageHeader(
        title = title,
        subtitle = subtitle,
        overview = item?.data?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
        endsAt = item?.data?.endDate,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        modifier = modifier,
    )
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val copy: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TitleOrLogo(
                title = title,
                logoImageUrl = logoImageUrl,
                showLogo = showLogo,
                modifier = Modifier.fillMaxWidth(.75f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(.6f),
            ) {
                if (subtitle != null) {
                    EpisodeName(subtitle)
                }
                QuickDetails(quickDetails, timeRemaining, endsAt = endsAt)
                // Glass headers sit inside a fixed 200dp slot on library pages, and the panel's
                // rim is what clips the copy. Two lines of synopsis is what fits under the logo,
                // the metadata and the genre line without the last row being sliced by the edge.
                val glassHeader = LocalLiquidGlass.current
                val overviewModifier =
                    Modifier
                        .padding(0.dp)
                        .height(
                            if (glassHeader) {
                                40.dp
                            } else {
                                48.dp + if (!overviewTwoLines) 12.dp else 0.dp
                            },
                        ).width(400.dp)
                if (overview.isNotNullOrBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (glassHeader || overviewTwoLines) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = overviewModifier,
                    )
                } else {
                    Spacer(overviewModifier)
                }
            }
        }
    }
    if (LocalLiquidGlass.current) {
        // Library pages use the same pane as the home hero, so moving from Home into a
        // collection does not drop the material for plain type on bare artwork. The page frame
        // already insets its content, so the pane adds no margin of its own: its edge lines up
        // with the row titles underneath.
        Box(
            modifier =
                modifier
                    .fillMaxWidth(),
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(0.66f),
                scrim = 1f,
            ) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                    copy()
                }
            }
        }
    } else {
        Column(modifier = modifier) { copy() }
    }
}

@Composable
fun HomePageCardContent(
    index: Int,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    when (item?.type) {
        BaseItemKind.GENRE -> {
            GenreCard(
                genreId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        BaseItemKind.STUDIO -> {
            StudioCard(
                studioId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        else -> {
            val imageType =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeImageType.imageType
                    } else {
                        viewOptions.imageType.imageType
                    }
                }
            val ratio =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeAspectRatio.ratio
                    } else {
                        viewOptions.aspectRatio.ratio
                    }
                }
            val scale =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeContentScale.scale
                    } else {
                        viewOptions.contentScale.scale
                    }
                }
            if (viewOptions.showTitles) {
                BannerCardWithTitle(
                    title = item?.title,
                    subtitle = item?.subtitle,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            } else {
                BannerCard(
                    name = item?.data?.seriesName ?: item?.name,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    interactionSource = null,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            }
        }
    }
}

@Composable
fun HomePageViewMoreCard(
    isEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    val aspectRatio =
        remember(isEpisode, viewOptions) {
            if (isEpisode) {
                viewOptions.episodeAspectRatio
            } else {
                viewOptions.aspectRatio
            }
        }
    ViewMoreCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = aspectRatio,
        size = DpSize(height = viewOptions.heightDp.dp, width = Dp.Unspecified),
        showTitle = viewOptions.showTitles,
    )
}
