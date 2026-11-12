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
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.SeerrRequestItem
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.TmdbDetailDialogHost
import com.github.damontecres.wholphin.ui.components.TmdbPosterCard
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * What the viewer asked Seerr for, on the home page.
 *
 * Discovery can already request a title from its dialog, but once the dialog closes the request
 * disappears: the viewer has no way to see whether the server is fetching it, whether an
 * administrator approved it, or whether it quietly failed. That is the half of a request flow that
 * makes it feel finished. This row is per account because Seerr requests are: the same television
 * shows each profile only its own asks.
 */
@HiltViewModel
class SeerrRequestsViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        sealed interface State {
            /** Draw nothing: Seerr is not connected, or there is nothing in flight. */
            data object Hidden : State

            data class Ready(
                val items: List<SeerrRequestItem>,
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
                        val items =
                            withContext(WholphinDispatchers.IO) {
                                seerrService.pendingRequests()
                            }
                        _state.value = if (items.isEmpty()) State.Hidden else State.Ready(items)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w("Seerr requests unavailable: %s", ex.javaClass.simpleName)
                        _state.value = State.Hidden
                    }
                }
        }

        /** The focused poster's artwork, so the shell's backdrop follows the row. */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch { backdropService.submit("tmdb_${item.id}", url) }
        }
    }

/**
 * The requests rail. Cards open the same discovery dialog as every other TMDB surface, so a title
 * that has arrived by the time the viewer presses it offers "Open in library" without a second
 * code path.
 */
@Composable
fun SeerrRequestShelf(
    state: SeerrRequestsViewModel.State,
    onFindInLibrary: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusItem: ((TmdbItem) -> Unit)? = null,
) {
    val ready = state as? SeerrRequestsViewModel.State.Ready ?: return
    if (ready.items.isEmpty()) return
    var details by remember { mutableStateOf<TmdbItem?>(null) }
    val glass = LocalLiquidGlass.current

    Column(modifier = modifier.fillMaxWidth()) {
        ItemRow(
            title = stringResource(R.string.seerr_requests),
            items = ready.items,
            onClickItem = { _, item -> details = item.item },
            onLongClickItem = { _, item -> details = item.item },
            horizontalPadding = 16.dp,
            showViewMore = false,
            subtitle = {
                Text(
                    text = stringResource(R.string.seerr_requests_summary),
                    style = if (glass) GlassType.meta else MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
            },
            cardContent = { _, item, cardModifier, onClick, onLongClick ->
                if (item != null) {
                    TmdbPosterCard(
                        item = item.item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        modifier = cardModifier,
                        width = 115.dp,
                        showCaption = false,
                        badge = stringResource(item.badgeText()),
                        onFocus = { onFocusItem?.invoke(item.item) },
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

private fun SeerrRequestItem.badgeText(): Int =
    when (availability) {
        SeerrAvailability.PENDING -> R.string.seerr_request_badge_pending
        SeerrAvailability.PROCESSING -> R.string.seerr_request_badge_processing
        else -> R.string.seerr_request_badge_partial
    }
