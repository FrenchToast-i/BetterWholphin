package com.github.damontecres.wholphin.ui.search

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.rememberContextMenu
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.titleStringRes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

internal const val SEARCH_ROW = 0
internal const val TAB_ROW = SEARCH_ROW + 1
internal const val SEERR_ROW = TAB_ROW + 1
internal const val COMBINED_ROW = SEERR_ROW
internal const val RESULTS_START = SEERR_ROW + 1

@Composable
fun SearchPage(
    initialQuery: String,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val glass = LocalLiquidGlass.current
    val state by viewModel.state.collectAsState()
    val programDialogState by viewModel.programDialogState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(userPreferences)
            .value.appPreferences.interfacePreferences.searchPreferences
    val combinedMode = prefs.combinedSearchResults
    val voiceSearchButtonVisible = prefs.showVoiceSearchButton

//    val query = rememberTextFieldState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // The field's value is read when the page is disposed (leaving Search is the moment the
    // viewer has committed to a query), so the callback must not close over a stale one.
    val latestQuery by rememberUpdatedState(query)
    DisposableEffect(Unit) {
        onDispose { viewModel.recordSearch(latestQuery) }
    }
    val focusRequesters =
        remember(state.includedSearchableTypes.size) { List(RESULTS_START + state.includedSearchableTypes.size) { FocusRequester() } }
    // The discovery shelf that appears where the library had nothing. It is the only focusable
    // content on an otherwise empty results page.
    val tmdbShelfFocusRequester = remember { FocusRequester() }
    // A fixture the query named. The guide's card is what can actually play a game, so this is
    // the one result on an empty page that leads straight to the thing the viewer asked for.
    // The newest remembered query, so down from the field lands on a search rather than on Clear.
    val recentSearchFocusRequester = remember { FocusRequester() }

    val seerrActive by viewModel.seerrActive.collectAsState()
    var selectedTab by rememberSaveable(seerrActive, state.discoverEnabled) { mutableIntStateOf(0) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var showFilterTypeDialog by rememberSaveable { mutableStateOf(false) }
    var searchClicked by rememberSaveable(query) { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var showProgramDialog by remember { mutableStateOf(false) }

    val position by viewModel.position.collectAsState()

    fun setPosition(pos: RowColumn) {
        Timber.v("pos=%s", pos)
        viewModel.position.value = pos
    }

    val contextMenu = rememberContextMenu(userPreferences, viewModel)

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        searchClicked = true
        viewModel.recordSearch(searchQuery)
        viewModel.search(searchQuery, combinedMode)
    }

    LaunchedEffect(query, combinedMode) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750.milliseconds)
                viewModel.search(query, combinedMode)
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position.row)?.tryRequestFocus()
    }
    val onClickItem = { _: Int, item: BaseItem ->
        Timber.v("Clicked %s, type=%s", item.id, item.type)
        if (item.type == BaseItemKind.TV_PROGRAM || item.type == BaseItemKind.PROGRAM || item.type == BaseItemKind.LIVE_TV_PROGRAM) {
            viewModel.fetchProgramForDialog(item.id)
            showProgramDialog = true
        } else {
            viewModel.navigationManager.navigateTo(item.destination())
        }
    }
    val onLongClickItem = { rowIndex: Int, index: Int, item: BaseItem ->
        setPosition(RowColumn(rowIndex, index))
        contextMenu.showContextMenu(index, item)
    }
    val onPlayItem = { _: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(Destination.Playback(item))
    }

    val onClickDiscover = { _: Int, item: DiscoverItem ->
        val dest =
            if (item.jellyfinItemId != null && item.type.baseItemKind != null) {
                Destination.MediaItem(
                    itemId = item.jellyfinItemId,
                    type = item.type.baseItemKind,
                )
            } else {
                Destination.DiscoveredItem(item)
            }
        viewModel.navigationManager.navigateTo(dest)
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }
    val positionCallback = { columns: Int, index: Int ->
        showHeader = index < columns
    }
    val showTabs =
        seerrActive && state.discoverEnabled && query.isNotBlank() && showHeader && combinedMode
    val isLibraryTab = selectedTab == 0

    LaunchedEffect(seerrActive, query) {
        if (!seerrActive || query.isBlank()) {
            selectedTab = 0
        }
    }

    LaunchedEffect(
        searchClicked,
        state,
        combinedMode,
        selectedTab,
        seerrActive,
    ) {
        if (!searchClicked || position.row > TAB_ROW) return@LaunchedEffect

        withContext(WholphinDispatchers.IO) {
            // Want to focus on the first successful row after all the ones before it are finished searching
            val results =
                if (isLibraryTab) {
                    if (combinedMode) {
                        listOf(state.combinedResults)
                    } else {
                        state.includedSearchableTypes.map { state.results[it] }
                    }
                } else {
                    listOf(state.seerrResults)
                }
            // A search that finished with nothing in the library has no row to hand focus to,
            // and the first-row request below then lands on a row that drew nothing: the search
            // key would leave the viewer with no focus on the page at all, while the TMDB shelf
            // sits on screen and unreachable. That shelf is the only focusable thing there, so
            // it takes the focus.
            val everythingEmpty =
                results.isNotEmpty() &&
                    results.all { result ->
                        when (result) {
                            is SearchResult.Success -> result.items.isEmpty()
                            is SearchResult.SuccessSeerr -> result.items.isEmpty()
                            else -> false
                        }
                    }
            if (everythingEmpty) {
                // The discovery shelf is the only focusable thing on the page, so the handoff
                // goes straight to it. Without it the request targets result row 0, which drew
                // nothing, and the posters sit on screen unreachable.
                onMain { tmdbShelfFocusRequester.tryRequestFocus() }
                return@withContext
            }
            val firstSuccess =
                results.indexOfFirst { it is SearchResult.Success || it is SearchResult.SuccessSeerr }
            if (firstSuccess >= 0) {
                val anyBeforeSearching =
                    results.subList(0, firstSuccess).any { it is SearchResult.Searching }
                if (!anyBeforeSearching) {
                    val targetRow =
                        if (isLibraryTab) {
                            if (combinedMode) {
                                COMBINED_ROW
                            } else {
                                firstSuccess
                            }
                        } else {
                            SEERR_ROW
                        }
//                    setPosition(RowColumn(targetRow, 0))
                    onMain { focusRequesters.getOrNull(targetRow)?.tryRequestFocus() }
                }
            }
        }
    }

    val tabs =
        remember(seerrActive, state.discoverEnabled) {
            buildList {
                add(TabDetails(R.string.library))
                if (seerrActive && state.discoverEnabled) {
                    add(TabDetails(R.string.discover))
                }
            }
        }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            // Search opens on an empty screen, so glass mode composes it as a centred invitation:
            // a page title, a line explaining what is searchable, and one wide field. Left-aligning
            // it made the page look like a form that had lost its results.
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val textFieldFocusRequester = remember { FocusRequester() }

            // Opening Search should put the caret in the field. Navigating in from the bar used
            // to leave focus on the bar capsule, so the first thing a viewer had to do was find
            // the field with the D-pad before they could type.
            LaunchedEffect(glass) {
                if (glass) {
                    textFieldFocusRequester.tryRequestFocus()
                }
            }

            if (glass) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 22.dp),
                ) {
                    Text(
                        text = stringResource(R.string.search),
                        style = GlassType.pageTitle,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.glass_search_hint),
                        style = GlassType.meta,
                        color = Color.White.copy(alpha = 0.64f),
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            SearchFieldRow(
                query = query,
                onQueryChange = { query = it },
                onSearch = { triggerImmediateSearch(query) },
                voiceInputManager = viewModel.voiceInputManager,
                focusRequester = textFieldFocusRequester,
                glass = glass,
                showVoiceSearch = voiceSearchButtonVisible,
                downFocusRequester = recentSearchFocusRequester.takeIf { recentSearches.isNotEmpty() },
                onOpenViewOptions = { showViewOptions = true },
                modifier =
                    Modifier
                        // The shell already insets the page in glass mode; adding the classic
                        // 16dp here would push the row out of line with every other page.
                        .padding(
                            start = if (glass) 0.dp else 16.dp,
                            end = if (glass) 0.dp else 16.dp,
                            top = if (glass) 0.dp else 16.dp,
                        ),
            )
        }
        AnimatedVisibility(
            visible = showTabs,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                tabs = tabs,
                onClick = {
                    selectedTab = it
                    val row =
                        when (selectedTab) {
                            0 -> COMBINED_ROW
                            else -> SEERR_ROW
                        }
                    setPosition(RowColumn(row, 0))
                },
                modifier =
                    Modifier
                        // Glass mode hugs its tabs, so it must not be forced to the page width.
                        .then(if (LocalLiquidGlass.current) Modifier else Modifier.fillMaxWidth())
                        .padding(start = 16.dp, end = 16.dp)
                        .onFocusChanged {
                            if (it.hasFocus) setPosition(RowColumn(TAB_ROW, 0))
                        },
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
//            SideEffect {
//                Timber.v("isLibraryTab=%s, combinedMode=%s", isLibraryTab, combinedMode)
//            }
            if (query.isBlank()) {
                // Nothing typed means nothing to search, so the page becomes discovery instead
                // of a blank canvas. The shelf draws nothing at all without a TMDB key, which
                // leaves the page exactly as it was.
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = if (glass) 0.dp else 16.dp,
                                end = if (glass) 0.dp else 16.dp,
                                top = 8.dp,
                            ),
                ) {
                    // Personal history first: discovery is what to watch next, but the viewer who
                    // came back for yesterday's search does not have to type it again.
                    RecentSearches(
                        searches = recentSearches,
                        onSelect = { search ->
                            query = search
                            triggerImmediateSearch(search)
                        },
                        onClear = viewModel::clearRecentSearches,
                        firstChipFocusRequester = recentSearchFocusRequester,
                    )
                    TmdbTrendingSection(
                        // A discovered title is not in the library, so the only way to act on it
                        // is to look for it there. This turns the shelf into a real search
                        // instead of a dead end.
                        onFindInLibrary = { item ->
                            viewModel.navigationManager.navigateTo(Destination.Search(item.displayTitle))
                        },
                    )
                }
            } else when {
                isLibraryTab && combinedMode -> {
                    SearchCombinedResults(
                        result = state.combinedResults,
                        query = query,
                        focusRequester = focusRequesters[COMBINED_ROW],
                        onClickItem = onClickItem,
                        onLongClickItem = { index, item ->
                            onLongClickItem(COMBINED_ROW, index, item)
                        },
                        onPlayItem = onPlayItem,
                        onClickPosition = { setPosition(it) },
                        onClickDiscover = onClickDiscover,
                        positionCallback = positionCallback,
                        onFocusItem = { viewModel.updateBackdrop(it) },
                        onFocusDiscoverItem = { viewModel.updateBackdrop(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                !isLibraryTab && combinedMode -> {
                    SearchCombinedResults(
                        result = state.seerrResults,
                        query = query,
                        focusRequester = focusRequesters[SEERR_ROW],
                        onClickItem = onClickItem,
                        onLongClickItem = { _, _ -> },
                        onPlayItem = onPlayItem,
                        onClickPosition = { setPosition(it) },
                        onClickDiscover = onClickDiscover,
                        positionCallback = positionCallback,
                        onFocusItem = { viewModel.updateBackdrop(it) },
                        onFocusDiscoverItem = { viewModel.updateBackdrop(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                isLibraryTab -> {
                    // Every type finished and every one is empty. Without this the page is a
                    // blank canvas, which reads as a broken search rather than an honest "you
                    // don't have this" — especially when the query arrived from a discovery
                    // card, where not owning the title is the common case.
                    val allTypesEmpty =
                        state.includedSearchableTypes.isNotEmpty() &&
                            state.includedSearchableTypes.all { type ->
                                (state.results[type] as? SearchResult.Success)?.items?.isEmpty() == true
                            }
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                // In glass mode the shell supplies the page inset; a second
                                // 16dp would make search results sit 16dp right of the home rows.
                                start = if (glass) 0.dp else 16.dp,
                                end = if (glass) 0.dp else 16.dp,
                                top = 8.dp,
                                bottom = 44.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.focusGroup(),
                    ) {
                        if (allTypesEmpty) {
                            // One item, deliberately. "Not in your library" is only half an
                            // answer; the other half is what TMDB has for the query, with the
                            // credit its terms require. Splitting the sentence and the shelf
                            // into two list items let the focus handoff scroll the credit off
                            // the top, which is the one line on this page that may not be
                            // clipped. Kept together, the section is the whole page and the
                            // message reads as its lead-in.
                            item {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_no_results_message, query),
                                        style = if (glass) GlassType.meta else MaterialTheme.typography.titleMedium,
                                        color = if (glass) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                    TmdbSearchSection(
                                        query = query,
                                        firstFocusRequester = tmdbShelfFocusRequester,
                                        singleRow = true,
                                        onFindInLibrary = { item ->
                                            viewModel.navigationManager.navigateTo(
                                                Destination.Search(item.displayTitle),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        itemsIndexed(state.includedSearchableTypes) { index, type ->
                            val rowIndex = RESULTS_START + index
                            val result = state.results.getOrDefault(type, SearchResult.Searching)
                            val focusRequester =
                                remember(state.includedSearchableTypes.size) {
                                    focusRequesters.getOrNull(rowIndex) ?: FocusRequester()
                                }
                            SearchRowResult(
                                title = type.titleStringRes,
                                result = result,
                                rowIndex = rowIndex,
                                position = position,
                                focusRequester = focusRequester,
                                onClickItem = onClickItem,
                                onLongClickItem = { index, item ->
                                    onLongClickItem(rowIndex, index, item)
                                },
                                onClickPosition = { setPosition(it) },
                                modifier = Modifier.fillMaxWidth(),
                                cardContent = { index, item, mod, onClick, onLongClick ->
                                    SearchPageCard(
                                        item = item,
                                        type = type,
                                        onClick = {
                                            setPosition(RowColumn(rowIndex, index))
                                            onClick.invoke()
                                        },
                                        onLongClick = onLongClick,
                                        modifier = mod,
                                    )
                                },
                            )
                        }

                        if (seerrActive && state.discoverEnabled) {
                            item {
                                SearchRowResult(
                                    title = R.string.discover,
                                    result = state.seerrResults,
                                    rowIndex = SEERR_ROW,
                                    position = position,
                                    focusRequester = focusRequesters[SEERR_ROW],
                                    onClickItem = onClickItem,
                                    onLongClickItem = { _, _ -> },
                                    onClickDiscover = onClickDiscover,
                                    onClickPosition = { setPosition(it) },
                                    cardContent = { _, _, _, _, _ -> },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        contextMenu.Compose()
    }

    if (showViewOptions) {
        SearchViewOptionsDialog(
            combinedResults = combinedMode,
            onCombinedResultsChange = viewModel::setCombinedResults,
            voiceSearchButtonVisible = voiceSearchButtonVisible,
            onVoiceSearchButtonVisibleChange = viewModel::setVoiceSearchButtonVisible,
            onClickFilterTypes = { showFilterTypeDialog = true },
            onDismissRequest = { showViewOptions = false },
        )
    }

    if (showFilterTypeDialog) {
        SearchTypeOptionsDialog(
            onDismissRequest = { showFilterTypeDialog = false },
            searchableTypes = state.possibleSearchableTypes,
            excludedSearchableTypes = state.excludedSearchableTypes,
            discoverAvailable = seerrActive,
            discoverEnabled = state.discoverEnabled,
            onClick = viewModel::onClickExcludeSearchableType,
            onClickDiscover = viewModel::onClickExcludeDiscover,
        )
    }

    if (showProgramDialog) {
        val context = LocalContext.current
        val onDismissRequest = { showProgramDialog = false }
        ProgramDialog(
            state = programDialogState.loading,
            canRecord = true,
            onDismissRequest = onDismissRequest,
            onWatch = {
                onDismissRequest.invoke()
                val channelId = it.data.channelId
                if (channelId != null) {
                    viewModel.navigationManager.navigateTo(
                        Destination.Playback(
                            itemId = channelId,
                            positionMs = 0L,
                        ),
                    )
                } else {
                    Toast.makeText(context, "Program has no channel ID", Toast.LENGTH_LONG).show()
                }
            },
            onRecord = { program, series ->
                viewModel.record(
                    programId = program.id,
                    series = series,
                )
                onDismissRequest.invoke()
            },
            onCancelRecord = { program, series ->
                viewModel.cancelRecording(
                    series = series,
                    timerId = if (series) program.data.seriesTimerId else program.data.timerId,
                )
                onDismissRequest.invoke()
            },
        )
    }
}

@Composable
fun SearchCombinedResults(
    result: SearchResult,
    query: String = "",
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPlayItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Focused library result, so the page's backdrop follows the card the viewer is on. */
    onFocusItem: (BaseItem) -> Unit = {},
    /** The discovery equivalent, for a page whose results are Seerr items. */
    onFocusDiscoverItem: (DiscoverItem) -> Unit = {},
) {
    when (result) {
        SearchResult.NoQuery -> {}

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.searching),
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = result.ex.localizedMessage ?: "Error occurred during search",
                messageColor = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Success -> {
            if (result.items.isEmpty()) {
                // Combined mode is also where a TMDB bridge lands ("Search your library"),
                // and those titles are mostly not owned: name the query so the empty state
                // reads as an answer instead of a broken search.
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message =
                        if (query.isNotBlank()) {
                            stringResource(R.string.search_no_results_message, query)
                        } else {
                            stringResource(R.string.no_results)
                        },
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onPlayItem = onPlayItem,
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        GridCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier =
                                details.mod.onFocusChanged {
                                    if (it.isFocused) details.item?.let(onFocusItem)
                                },
                            fillWidth = details.widthPx,
                        )
                    },
                    modifier = modifier,
                )
            }
        }

        is SearchResult.SuccessSeerr -> {
            if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message =
                        if (query.isNotBlank()) {
                            stringResource(R.string.search_no_results_message, query)
                        } else {
                            stringResource(R.string.no_results)
                        },
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickDiscover,
                    onLongClickItem = { _, _ -> },
                    onPlayItem = { _, _ -> },
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        DiscoverItemCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier =
                                details.mod.onFocusChanged {
                                    if (it.isFocused) details.item?.let(onFocusDiscoverItem)
                                },
                        )
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun <T : CardGridItem> SearchGrid(
    items: List<T?>,
    focusRequester: FocusRequester,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: (Int, T) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    cardContent: @Composable (GridItemDetails<T>) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
    ) {
        ItemRowTitle(stringResource(R.string.results))

        CardGrid(
            pager = items,
            onClickItem = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onClickItem.invoke(index, item)
            },
            onLongClickItem = onLongClickItem,
            onClickPlay = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onPlayItem.invoke(index, item)
            },
            letterPosition = { -1 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            positionCallback = positionCallback,
            modifier = Modifier.fillMaxSize(),
            cardContent = cardContent,
        )
    }
}

@Composable
fun SearchRowResult(
    @StringRes title: Int,
    result: SearchResult,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    onClickDiscover: ((Int, DiscoverItem) -> Unit)? = null,
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
) {
    when (val r = result) {
        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(title),
                message = r.ex.localizedMessage ?: "Error occurred during search",
                messageColor = MaterialTheme.colorScheme.error,
                modifier = Modifier,
            )
        }

        SearchResult.NoQuery -> {
            // no-op
        }

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(title),
                message = stringResource(R.string.searching),
                modifier = modifier,
            )
        }

        is SearchResult.Success -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = stringResource(title),
                    items = r.items,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    modifier = modifier.focusRequester(focusRequester),
                    cardContent = cardContent,
                )
            }
        }

        is SearchResult.SuccessSeerr -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = stringResource(title),
                    items = r.items,
                    onClickItem = { index, item ->
                        onClickPosition.invoke(RowColumn(rowIndex, index))
                        onClickDiscover?.invoke(index, item)
                    },
                    onLongClickItem = { _, _ -> },
                    modifier = modifier.focusRequester(focusRequester),
                    cardContent = { index: Int, item: DiscoverItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            showOverlay = true,
                            modifier = mod,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun SearchResultPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val glass = LocalLiquidGlass.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = if (glass) GlassType.rowTitle else MaterialTheme.typography.titleLarge,
            color = if (glass) Color.White else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = if (glass) GlassType.meta else MaterialTheme.typography.titleMedium,
            color = if (glass) Color.White.copy(alpha = 0.64f) else messageColor,
        )
    }
}
