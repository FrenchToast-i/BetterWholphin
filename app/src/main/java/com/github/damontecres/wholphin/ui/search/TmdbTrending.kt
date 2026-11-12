package com.github.damontecres.wholphin.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TmdbTrendingViewModel
    @Inject
    constructor(
        private val tmdb: TmdbService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        sealed interface State {
            /** No key configured: the shelf is absent rather than empty or broken. */
            data object NoKey : State

            data object Loading : State

            /** The key was sent and refused: a settings problem, not a network one. */
            data object Rejected : State

            data object Unavailable : State

            data class Ready(
                val items: List<TmdbItem>,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Loading)
        val state = _state.asStateFlow()

        init {
            viewModelScope.launch {
                // Follow the key rather than reading it once: Search stays on the back stack
                // while Settings is open, so a ViewModel that froze the original key would keep
                // answering "no key" after the viewer has just added one.
                tmdb.keyFlow.collectLatest { key ->
                    _state.value = State.Loading
                    _state.value =
                        if (key.isBlank()) {
                            State.NoKey
                        } else {
                            when (val result = tmdb.trending()) {
                                is TmdbResult.Success -> State.Ready(result.value)
                                TmdbResult.Rejected -> State.Rejected
                                TmdbResult.Unavailable -> State.Unavailable
                            }
                        }
                }
            }
        }

        /**
         * Let the shell's backdrop follow a trending poster.
         *
         * Glass has nothing to refract if the page keeps the previous screen's artwork: the bar,
         * the field and the copy panes all sit on whatever was focused before Search opened. The
         * backdrop is addressed by URL because these titles are not in the library and have no
         * item id to ask Jellyfin for.
         */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch {
                backdropService.submit("tmdb_${item.id}", url)
            }
        }
    }

/**
 * What is trending this week, for the empty search page.
 *
 * Discovery is what an empty search page should offer: the viewer opened Search with nothing in
 * mind, so a shelf of titles the rest of the world is watching is more use than a blank screen.
 * It only exists when the viewer has added a TMDB key — no key, no shelf, and the page stays
 * exactly as it was.
 */
@Composable
fun TmdbTrendingSection(
    modifier: Modifier = Modifier,
    onFindInLibrary: ((TmdbItem) -> Unit)? = null,
    viewModel: TmdbTrendingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<TmdbItem?>(null) }

    when (val s = state) {
        is TmdbTrendingViewModel.State.Ready -> {
            if (s.items.isEmpty()) return
            // The page opened on nothing, so give it the shelf's own artwork instead of the
            // previous screen's: the top trending title is what this surface is about.
            LaunchedEffect(s.items.first().id) {
                viewModel.submitBackdrop(s.items.first())
            }
            TmdbPosterShelf(
                heading = stringResource(R.string.tmdb_trending),
                items = s.items,
                onSelect = { details = it },
                onFocusItem = viewModel::submitBackdrop,
                modifier = modifier,
            )
        }
        // A key that is set but cannot be used is worth saying out loud: the shelf simply
        // vanishing looks like the setting was ignored. No key stays silent, because there the
        // shelf is deliberately absent rather than broken. A rejected key and an unreachable
        // service say different things, so the viewer knows whether to edit the setting or wait.
        TmdbTrendingViewModel.State.Rejected -> {
            TmdbMessage(stringResource(R.string.tmdb_key_rejected), modifier)
        }

        TmdbTrendingViewModel.State.Unavailable -> {
            TmdbMessage(stringResource(R.string.tmdb_unavailable), modifier)
        }

        TmdbTrendingViewModel.State.Loading,
        TmdbTrendingViewModel.State.NoKey,
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


