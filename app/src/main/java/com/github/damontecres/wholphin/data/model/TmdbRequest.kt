package com.github.damontecres.wholphin.data.model

import java.util.UUID

/**
 * What the viewer can do about a discovered TMDB title on their own server.
 *
 * Discovery and acquisition are two different services — TMDB knows what exists, Seerr knows what
 * the server has, is fetching, or will accept. This is the one place the two vocabularies meet:
 * [availability] is Seerr's answer, [canRequest] is whether this viewer is allowed to ask, and
 * [jellyfinItemId] is how a title that has already landed becomes something to press play on.
 */
data class TmdbRequestStatus(
    val availability: SeerrAvailability,
    val canRequest: Boolean,
    val jellyfinItemId: UUID?,
) {
    /** Whether the title is watchable on this server right now, in whole or in part. */
    val isAvailable: Boolean
        get() =
            availability == SeerrAvailability.AVAILABLE ||
                availability == SeerrAvailability.PARTIALLY_AVAILABLE
}

/**
 * The one action line a discovery dialog can offer a title that is not in the library.
 *
 * Kept separate from [TmdbRequestStatus] because the same status reads differently depending on
 * what is happening around it: a title is requestable while it is idle, but the phase has to say
 * that the request is in flight the moment the viewer presses the button.
 */
enum class TmdbRequestPhase {
    /** Seerr is not configured, so the dialog stays exactly as it was before. */
    HIDDEN,

    /** The status is being fetched; the dialog draws no line yet. */
    LOADING,

    /** A request is in flight, so its control is held rather than pressed twice. */
    SUBMITTING,

    /** Seerr is configured but the status could not be read. */
    FAILED,

    /** Not on the server, and this viewer may ask for it. */
    REQUESTABLE,

    /** Not on the server, and this viewer may not ask for it. */
    DISABLED,

    /** Asked for, waiting for an administrator to approve it. */
    REQUESTED,

    /** Approved, and the server is fetching it now. */
    PROCESSING,

    /** Some of it is on the server already; the rest is not. */
    PARTIAL,

    /** On the server and playable. */
    AVAILABLE,

    /** The server refuses to fetch this title. */
    BLOCKED,
}

/**
 * The request state a dialog draws, including the fact that a request is in flight.
 *
 * Every field exists because the dialog has to answer three questions independently: is Seerr
 * there, what does it say, and is something happening right now.
 */
data class TmdbRequestUiState(
    val status: TmdbRequestStatus? = null,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val failed: Boolean = false,
) {
    val phase: TmdbRequestPhase
        get() =
            when {
                loading -> TmdbRequestPhase.LOADING
                failed -> TmdbRequestPhase.FAILED
                status == null -> TmdbRequestPhase.HIDDEN
                submitting -> TmdbRequestPhase.SUBMITTING
                else ->
                    when (status.availability) {
                        SeerrAvailability.PENDING -> TmdbRequestPhase.REQUESTED
                        SeerrAvailability.PROCESSING -> TmdbRequestPhase.PROCESSING
                        SeerrAvailability.PARTIALLY_AVAILABLE -> TmdbRequestPhase.PARTIAL
                        SeerrAvailability.AVAILABLE -> TmdbRequestPhase.AVAILABLE
                        SeerrAvailability.BLOCKLISTED -> TmdbRequestPhase.BLOCKED
                        SeerrAvailability.UNKNOWN,
                        SeerrAvailability.DELETED,
                        ->
                            if (status.canRequest) {
                                TmdbRequestPhase.REQUESTABLE
                            } else {
                                TmdbRequestPhase.DISABLED
                            }
                    }
            }

    /**
     * Whether pressing the one action would create a request.
     *
     * A failed submit keeps the status from before it, so the button has to stay live: the
     * failure is worth reporting, but making the viewer dismiss the dialog and reopen it before
     * they can retry would be a worse answer than the failure itself.
     */
    val canRequestNow: Boolean
        get() {
            val status = status ?: return false
            return status.canRequest &&
                !submitting &&
                (
                    status.availability == SeerrAvailability.UNKNOWN ||
                        status.availability == SeerrAvailability.DELETED
                )
        }

    /** Whether the dialog can open the title in the library instead of requesting it. */
    val canOpenInLibrary: Boolean get() = status?.isAvailable == true && status.jellyfinItemId != null
}
