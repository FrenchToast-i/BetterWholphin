package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.search.SearchCombinedResults
import com.github.damontecres.wholphin.ui.search.SearchFieldRow
import com.github.damontecres.wholphin.ui.search.SearchResult
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DiscoverSearchViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
        private val seerrService: SeerrService,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val seerrResults = MutableStateFlow<SearchResult>(SearchResult.NoQuery)

        private var searchJob: Job? = null
        var currentQuery: String = ""

        fun search(query: String) {
            if (query == currentQuery) {
                return
            }
            currentQuery = query
            viewModelScope.launchIO {
                if (query.isBlank()) {
                    seerrResults.value = SearchResult.NoQuery
                    return@launchIO
                }
                Timber.v("Starting seerr search")
                seerrResults.value = SearchResult.Searching
                try {
                    val results =
                        seerrService
                            .search(query)
                            .map { seerrService.createDiscoverItem(it) }
                            .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                    Timber.v("Seerr search complete: %s results", results.size)
                    seerrResults.value = SearchResult.SuccessSeerr(results)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during seerr search")
                    seerrResults.value = SearchResult.Error(ex)
                }
            }
        }

        /**
         * A discovered title has no Jellyfin item, so the page's own backdrop follows the focused
         * poster instead. Without it the shell kept the previous screen's artwork behind a page
         * that is entirely about other titles.
         */
        fun updateBackdrop(item: DiscoverItem) {
            viewModelScope.launchIO { backdropService.submit(item) }
        }
    }

@Composable
fun DiscoverSearchPage(
    preferences: UserPreferences,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverSearchViewModel = hiltViewModel(),
) {
    val glass = LocalLiquidGlass.current
    val seerrResults by viewModel.seerrResults.collectAsState()
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf(viewModel.currentQuery) }
    var position by rememberInt(-1)

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(preferences)
            .value.appPreferences.interfacePreferences.searchPreferences

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

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        viewModel.search(searchQuery)
    }

    LaunchedEffect(query) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750L)
                viewModel.search(query)
            }
        }
    }
    val gridFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (position >= 0) {
            gridFocusRequester.tryRequestFocus("grid")
        } else {
            textFieldFocusRequester.tryRequestFocus("textField")
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SearchFieldRow(
            query = query,
            onQueryChange = { query = it },
            onSearch = { triggerImmediateSearch(query) },
            voiceInputManager = viewModel.voiceInputManager,
            focusRequester = textFieldFocusRequester,
            glass = glass,
            showVoiceSearch = prefs.showVoiceSearchButton,
            // Classic mode keeps the page's own 16dp inset; glass mode is inset by the shell,
            // like every other page, so the field lines up with the rows under it.
            modifier = Modifier.padding(start = if (glass) 0.dp else 16.dp, end = if (glass) 0.dp else 16.dp),
        )
        SearchCombinedResults(
            result = seerrResults,
            query = query,
            focusRequester = gridFocusRequester,
            onClickItem = { _, _ -> },
            onLongClickItem = { _, _ -> },
            onPlayItem = { _, _ -> },
            onClickPosition = { position = it.column },
            onClickDiscover = onClickDiscover,
            positionCallback = { columns, index ->
                position = index
                positionCallback.invoke(columns, index)
            },
            onFocusDiscoverItem = { viewModel.updateBackdrop(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
