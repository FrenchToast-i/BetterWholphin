package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbResult
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.TmdbAttribution
import com.github.damontecres.wholphin.ui.components.TmdbDetailDialogHost
import com.github.damontecres.wholphin.ui.components.TmdbPosterCard
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * "Top 10 on TMDB this week" — the one shelf that is the same for everyone.
 *
 * The other discovery rails are personal: recommendations from what the viewer is watching, a
 * watchlist they built. This one is the zeitgeist, and it is deliberately numbered. A ranked row
 * says "these ten, in this order" where a recommendation row says "some things like what you
 * have", and the number is what makes a poster wall feel like a chart rather than another grid.
 *
 * Silent on every failure, like the home recommendation rail: Search is where discovery reports
 * its health, and an error line between two library rows reads as a broken row.
 */
@HiltViewModel
class TmdbTopTenViewModel
    @Inject
    constructor(
        private val tmdb: TmdbService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        sealed interface State {
            data object Hidden : State

            data class Ready(
                val items: List<TmdbItem>,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Hidden)
        val state = _state.asStateFlow()

        private var job: Job? = null

        fun refresh() {
            job?.cancel()
            job =
                viewModelScope.launch {
                    try {
                        _state.value = load()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // Only the class: a request exception can carry a URL, and a v3 key
                        // travels in the URL.
                        Timber.w("TMDB top ten unavailable: %s", ex.javaClass.simpleName)
                        _state.value = State.Hidden
                    }
                }
        }

        /** The focused poster's artwork, so the shell follows the shelf into discovery. */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch { backdropService.submit("tmdb_${item.id}", url) }
        }

        private suspend fun load(): State =
            withContext(WholphinDispatchers.IO) {
                if (!tmdb.hasKey()) return@withContext State.Hidden
                when (val result = tmdb.trending()) {
                    is TmdbResult.Success ->
                        State.Ready(
                            result.value
                                // A ranked card without a poster is a numbered blank: the
                                // number would be the only thing on it.
                                .filter { it.posterPath != null && it.displayTitle.isNotBlank() }
                                .take(TOP_COUNT),
                        )

                    TmdbResult.Rejected,
                    TmdbResult.Unavailable,
                    -> State.Hidden
                }
            }

        companion object {
            const val TOP_COUNT = 10
        }
    }

/**
 * The ranked shelf. Posters carry a number badge; the heading and the required credit sit above
 * them, so scrolling the row into view never separates the two.
 */
@Composable
fun TmdbTopTenShelf(
    state: TmdbTopTenViewModel.State,
    onFindInLibrary: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusItem: ((TmdbItem) -> Unit)? = null,
) {
    val ready = state as? TmdbTopTenViewModel.State.Ready ?: return
    if (ready.items.isEmpty()) return
    var details by remember { mutableStateOf<TmdbItem?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        ItemRow(
            title = stringResource(R.string.tmdb_top_ten),
            items = ready.items,
            onClickItem = { _, item -> details = item },
            onLongClickItem = { _, item -> details = item },
            horizontalPadding = 16.dp,
            showViewMore = false,
            subtitle = { TmdbAttribution(Modifier.padding(top = 2.dp, bottom = 6.dp)) },
            cardContent = { index, item, cardModifier, onClick, onLongClick ->
                if (item != null) {
                    TmdbPosterCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        modifier = cardModifier,
                        width = 115.dp,
                        showCaption = false,
                        rank = index + 1,
                        onFocus = { onFocusItem?.invoke(item) },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    details?.let { item ->
        TmdbDetailDialogHost(
            item = item,
            onDismissRequest = { details = null },
            onFindInLibrary = onFindInLibrary,
        )
    }
}
