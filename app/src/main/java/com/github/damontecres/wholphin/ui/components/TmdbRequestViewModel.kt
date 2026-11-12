package com.github.damontecres.wholphin.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.TmdbRequestStatus
import com.github.damontecres.wholphin.data.model.TmdbRequestUiState
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbRequestService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the one request control on a discovery dialog.
 *
 * The dialog itself is stateless — it is drawn by three different shelves — so the state lives
 * here and the host composable feeds it in. The status is read when the dialog opens rather than
 * for every poster: a shelf of twenty posters would otherwise be twenty requests to Seerr just to
 * find out which badges to draw.
 */
@HiltViewModel
class TmdbRequestViewModel
    @Inject
    constructor(
        private val requestService: TmdbRequestService,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TmdbRequestUiState())
        val state: StateFlow<TmdbRequestUiState> = _state

        private var loadJob: Job? = null

        /**
         * Read the server's answer for [item].
         *
         * Restarted rather than skipped when a second dialog opens: the previous title's status
         * is not an answer about this one, and a failed read should be retried the next time the
         * viewer opens the title rather than cached for the session.
         */
        fun load(item: TmdbItem) {
            loadJob?.cancel()
            _state.value = TmdbRequestUiState(loading = true)
            loadJob =
                viewModelScope.launchIO {
                    try {
                        val status = requestService.status(item)
                        // A null status means Seerr is not configured; clearing the loading flag
                        // is what tells the dialog to draw exactly what it drew before.
                        _state.value = TmdbRequestUiState(status = status)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // No URL, message, or body: a Seerr error message can carry the request.
                        Timber.w("Could not read Seerr request status: %s", ex.javaClass.simpleName)
                        _state.value = TmdbRequestUiState(failed = true)
                    }
                }
        }

        /**
         * Ask for [item], then show what the server did with it.
         *
         * Re-reading the status instead of assuming "requested" is what makes an auto-approved
         * request appear as downloading, and a moderated one appear as waiting for approval —
         * the viewer sees the answer their server actually gave.
         */
        fun request(item: TmdbItem) {
            if (_state.value.submitting) return
            _state.update { it.copy(submitting = true, failed = false) }
            viewModelScope.launchIO {
                try {
                    val status = requestService.request(item)
                    _state.update { it.copy(status = status, submitting = false) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Seerr request failed: %s", ex.javaClass.simpleName)
                    // A request that failed may still have been accepted — a second press, or a
                    // 409 for a title already asked for — so ask for the current truth before
                    // reporting failure. Only an unchanged, unrequested title is a real failure.
                    val refreshed = readStatusQuietly(item)
                    _state.update { current ->
                        if (refreshed != null && refreshed.availability != SeerrAvailability.UNKNOWN) {
                            current.copy(status = refreshed, submitting = false)
                        } else {
                            current.copy(submitting = false, failed = true)
                        }
                    }
                }
            }
        }

        /** Open a title the server already has, in the library rather than in TMDB. */
        fun openInLibrary(item: TmdbItem) {
            val itemId = _state.value.status?.jellyfinItemId ?: return
            navigationManager.navigateTo(
                Destination.MediaItem(
                    itemId = itemId,
                    type = if (item.isMovie) BaseItemKind.MOVIE else BaseItemKind.SERIES,
                ),
            )
        }

        private suspend fun readStatusQuietly(item: TmdbItem): TmdbRequestStatus? =
            try {
                requestService.status(item)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }
    }
