package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.services.ForYouReason
import com.github.damontecres.wholphin.services.ForYouService
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * The home page's For You rail.
 *
 * Refreshes on every Home entry, because the row should follow what was just watched. It keeps the
 * previous result on screen while recomputing: unlike the TMDB rail, this one is built from the
 * viewer's own library, so a moment of staleness is invisible and a row that blinks out on every
 * return reads as a loading fault.
 */
@HiltViewModel
class ForYouViewModel
    @Inject
    constructor(
        private val service: ForYouService,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        sealed interface State {
            /** Draw nothing: no history to learn from, or nothing suitable left unwatched. */
            data object Hidden : State

            data class Ready(
                val items: List<BaseItem>,
                val reason: ForYouReason,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Hidden)
        val state = _state.asStateFlow()

        private var job: Job? = null
        private var loadedFor: UUID? = null

        fun refresh() {
            job?.cancel()
            job =
                viewModelScope.launch {
                    try {
                        // On a cold start the signed-in user can arrive after the page composes;
                        // waiting briefly is what makes the row appear on the first visit instead
                        // of only on the second.
                        val userId =
                            withTimeoutOrNull(USER_TIMEOUT_MS) {
                                serverRepository.currentUserFlow.filterNotNull().first()
                            }?.id ?: return@launch
                        if (loadedFor != null && loadedFor != userId) {
                            // Another profile signed in: the previous profile's picks must not
                            // sit on screen while the new ones load.
                            _state.value = State.Hidden
                        }
                        val result =
                            withContext(WholphinDispatchers.IO) {
                                service.recommendations(userId)
                            }
                        if (result == null) {
                            _state.value = State.Hidden
                            loadedFor = null
                        } else {
                            _state.value = State.Ready(result.items, result.reason)
                            loadedFor = userId
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // The row is best-effort. A server hiccup leaves the home page as it was
                        // rather than turning into an error between two library rows.
                        Timber.w("For You unavailable: %s", ex.javaClass.simpleName)
                        _state.value = State.Hidden
                        loadedFor = null
                    }
                }
        }

        companion object {
            private const val USER_TIMEOUT_MS = 8_000L
        }
    }

/**
 * "For You" — the library's own recommendation row.
 *
 * Cards are ordinary library items, so they carry the same posters, the same progress bars, and
 * the same detail page as every other row. Tapping opens the item; Play starts it. The reason line
 * says why these titles are here, which is what makes the row feel like an answer rather than a
 * second Recently Added.
 */
@Composable
fun ForYouShelf(
    state: ForYouViewModel.State,
    excludeIds: Set<UUID>,
    onClickItem: (BaseItem) -> Unit,
    onPlayItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusItem: ((BaseItem?) -> Unit)? = null,
) {
    val ready = state as? ForYouViewModel.State.Ready ?: return
    // The row sits under the watch row, and the same show in both places would read as a bug.
    // Excluding is done at draw time so a row loading above never requeries this one.
    val items =
        remember(ready.items, excludeIds) {
            ready.items
                .filter { item ->
                    item.id !in excludeIds && item.data.seriesId?.let { it in excludeIds } != true
                }.take(MAX_VISIBLE)
        }
    // A one- or two-card row looks like a failed query, not a recommendation.
    if (items.size < MIN_VISIBLE) return

    ItemRow(
        title = stringResource(R.string.for_you),
        items = items,
        onClickItem = { _, item -> onClickItem(item) },
        // A dead long-press on a TV remote reads as broken; the detail page is the only action
        // this row has, so both gestures open it.
        onLongClickItem = { _, item -> onClickItem(item) },
        horizontalPadding = 16.dp,
        showViewMore = false,
        subtitle = { ForYouReasonLine(ready.reason, Modifier.padding(top = 2.dp, bottom = 6.dp)) },
        cardContent = { index, item, cardModifier, onClick, onLongClick ->
            if (item != null) {
                val onKey =
                    remember(item) {
                        { event: androidx.compose.ui.input.key.KeyEvent ->
                            if (isPlayKeyUp(event) && item.playable) {
                                onPlayItem(item)
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
                    viewOptions = HomeRowViewOptions(),
                    modifier =
                        cardModifier
                            .onFocusChanged { if (it.isFocused) onFocusItem?.invoke(item) }
                            .onKeyEvent { onKey(it) },
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** The personal half of the heading: why this row exists. */
@Composable
private fun ForYouReasonLine(
    reason: ForYouReason,
    modifier: Modifier = Modifier,
) {
    val text =
        when (reason) {
            is ForYouReason.BecauseYouWatched ->
                stringResource(R.string.for_you_reason_seed, reason.title)
            is ForYouReason.BecauseYouWatch ->
                when (reason.genres.size) {
                    0 -> stringResource(R.string.for_you_reason_history)
                    1 -> stringResource(R.string.for_you_reason_genre, reason.genres[0])
                    else ->
                        stringResource(
                            R.string.for_you_reason_genres,
                            reason.genres[0],
                            reason.genres[1],
                        )
                }
        }
    val glass = LocalLiquidGlass.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (glass) {
                // Readable from a sofa over pale artwork without competing with the heading.
                Color.White.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            },
        modifier = modifier,
    )
}

private const val MAX_VISIBLE = 12

/** Below this the row is not worth drawing. */
private const val MIN_VISIBLE = 4
