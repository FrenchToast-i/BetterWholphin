package com.github.damontecres.wholphin.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbResult
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.ui.components.TmdbDetailDialogHost
import com.github.damontecres.wholphin.ui.components.TmdbMessage
import com.github.damontecres.wholphin.ui.components.TmdbPosterShelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TMDB's matches for what the viewer typed.
 *
 * The library is asked first, and this is what a page can offer when the library owns nothing by
 * that name. Without it, the only answer to "do I have Severance?" is "no", and the discovery
 * dialog's bridge into search leads to a second dead end.
 *
 * The key is followed rather than read once, for the same reason as the trending shelf: Search
 * stays on the back stack while Settings is open, so a key added there has to take effect here
 * without navigating away and back.
 */
@HiltViewModel
class TmdbSearchViewModel
    @Inject
    constructor(
        private val tmdb: TmdbService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        sealed interface State {
            /** Nothing typed yet, or no key: draw nothing. */
            data object Idle : State

            data object NoKey : State

            data object Loading : State

            /** The key was sent and refused: a settings problem, not a network one. */
            data object Rejected : State

            data object Unavailable : State

            data class Ready(
                val items: List<TmdbItem>,
            ) : State
        }

        private val query = MutableStateFlow("")

        private val _state = MutableStateFlow<State>(State.Idle)
        val state = _state.asStateFlow()

        init {
            viewModelScope.launch {
                combine(tmdb.keyFlow, query) { key, typed -> key to typed.trim() }
                    .collectLatest { (key, typed) ->
                        when {
                            key.isBlank() -> _state.value = State.NoKey
                            typed.isBlank() -> _state.value = State.Idle
                            else -> {
                                // A hand-rolled debounce: collectLatest cancels this block when
                                // the next keystroke arrives, so a half-typed query never leaves
                                // the device. A TV keyboard types one character at a time, and
                                // every prefix of a title is its own search.
                                delay(QUERY_DEBOUNCE_MS)
                                _state.value = State.Loading
                                _state.value =
                                    when (val result = tmdb.search(typed)) {
                                        is TmdbResult.Success -> State.Ready(result.value)
                                        TmdbResult.Rejected -> State.Rejected
                                        TmdbResult.Unavailable -> State.Unavailable
                                    }
                            }
                        }
                    }
            }
        }

        fun updateQuery(value: String) {
            query.value = value
        }

        /** The focused discovery poster's backdrop, so the page follows what the viewer is on. */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch {
                backdropService.submit("tmdb_${item.id}", url)
            }
        }

        private companion object {
            const val QUERY_DEBOUNCE_MS = 350L
        }
    }

/**
 * "Also on TMDB" — what the library does not have, for the query it could not answer.
 *
 * Silent without a key and silent while there is nothing to show, so the page keeps exactly the
 * empty state it had before. A set key that cannot be used is reported instead, because that is
 * the one case where the viewer can do something about it.
 */
@Composable
fun TmdbSearchSection(
    query: String,
    modifier: Modifier = Modifier,
    /**
     * Where focus lands when the page hands it over after a search that the library could not
     * answer. The shelf is the only focusable thing on that page, so this is how a viewer gets
     * from the search field to the posters.
     */
    firstFocusRequester: FocusRequester? = null,
    onFindInLibrary: ((TmdbItem) -> Unit)? = null,
    /** One poster row, so the section fits the page the focus handoff lands on. */
    singleRow: Boolean = false,
    viewModel: TmdbSearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(query) {
        viewModel.updateQuery(query)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<TmdbItem?>(null) }

    when (val s = state) {
        is TmdbSearchViewModel.State.Ready -> {
            if (s.items.isEmpty()) return
            TmdbPosterShelf(
                heading = stringResource(R.string.tmdb_search_heading),
                items = s.items,
                onSelect = { details = it },
                firstFocusRequester = firstFocusRequester,
                singleRow = singleRow,
                onFocusItem = viewModel::submitBackdrop,
                modifier = modifier,
            )
        }

        TmdbSearchViewModel.State.Rejected -> {
            TmdbMessage(stringResource(R.string.tmdb_key_rejected), modifier)
        }

        TmdbSearchViewModel.State.Unavailable -> {
            TmdbMessage(stringResource(R.string.tmdb_unavailable), modifier)
        }

        TmdbSearchViewModel.State.Loading,
        TmdbSearchViewModel.State.NoKey,
        TmdbSearchViewModel.State.Idle,
        -> {}
    }

    details?.let { item ->
        TmdbDetailDialogHost(
            item = item,
            onDismissRequest = { details = null },
            onFindInLibrary = onFindInLibrary,
        )
    }
}
