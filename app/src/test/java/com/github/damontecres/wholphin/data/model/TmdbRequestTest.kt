package com.github.damontecres.wholphin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The dialog's whole job is to turn one server answer into one honest action, so every branch is
 * pinned here: a request button on a title that is already downloading, or a disabled one on a
 * title the viewer is allowed to ask for, would both be visible product bugs.
 */
class TmdbRequestTest {
    private fun state(
        availability: SeerrAvailability,
        canRequest: Boolean = true,
        submitting: Boolean = false,
        failed: Boolean = false,
        loading: Boolean = false,
        itemId: UUID? = null,
    ) = TmdbRequestUiState(
        status = TmdbRequestStatus(availability, canRequest, itemId),
        loading = loading,
        submitting = submitting,
        failed = failed,
    )

    @Test
    fun `a title the server does not have asks to be requested`() {
        val state = state(SeerrAvailability.UNKNOWN)
        assertEquals(TmdbRequestPhase.REQUESTABLE, state.phase)
        assertTrue(state.canRequestNow)
        assertFalse(state.canOpenInLibrary)
    }

    @Test
    fun `a title deleted from the server can be requested again`() {
        val state = state(SeerrAvailability.DELETED)
        assertEquals(TmdbRequestPhase.REQUESTABLE, state.phase)
        assertTrue(state.canRequestNow)
    }

    @Test
    fun `a pending request waits rather than offering a second one`() {
        val state = state(SeerrAvailability.PENDING)
        assertEquals(TmdbRequestPhase.REQUESTED, state.phase)
        assertFalse(state.canRequestNow)
    }

    @Test
    fun `a processing request shows what the server is doing`() {
        assertEquals(
            TmdbRequestPhase.PROCESSING,
            state(SeerrAvailability.PROCESSING).phase,
        )
    }

    @Test
    fun `an available title is opened, not requested`() {
        val state = state(SeerrAvailability.AVAILABLE)
        assertEquals(TmdbRequestPhase.AVAILABLE, state.phase)
        assertFalse(state.canRequestNow)
        assertFalse(state.canOpenInLibrary)
    }

    @Test
    fun `an available title with a library id can be opened`() {
        val state = state(SeerrAvailability.AVAILABLE, itemId = UUID.randomUUID())
        assertTrue(state.canOpenInLibrary)
    }

    @Test
    fun `a partially available title says so without claiming it is whole`() {
        val state = state(SeerrAvailability.PARTIALLY_AVAILABLE)
        assertEquals(TmdbRequestPhase.PARTIAL, state.phase)
        assertFalse(state.canRequestNow)
    }

    @Test
    fun `a viewer without request permission is told why`() {
        val state = state(SeerrAvailability.UNKNOWN, canRequest = false)
        assertEquals(TmdbRequestPhase.DISABLED, state.phase)
        assertFalse(state.canRequestNow)
    }

    @Test
    fun `a blocked title is not requestable`() {
        assertEquals(
            TmdbRequestPhase.BLOCKED,
            state(SeerrAvailability.BLOCKLISTED).phase,
        )
    }

    @Test
    fun `no seerr means no request line at all`() {
        assertEquals(TmdbRequestPhase.HIDDEN, TmdbRequestUiState().phase)
        assertFalse(TmdbRequestUiState().canRequestNow)
    }

    @Test
    fun `a request in flight holds the control`() {
        val state = state(SeerrAvailability.UNKNOWN, submitting = true)
        assertEquals(TmdbRequestPhase.SUBMITTING, state.phase)
        assertFalse(state.canRequestNow)
    }

    @Test
    fun `a failed request leaves the button live so it can be tried again`() {
        val state = state(SeerrAvailability.UNKNOWN, failed = true)
        assertEquals(TmdbRequestPhase.FAILED, state.phase)
        assertTrue(state.canRequestNow)
    }

    @Test
    fun `a failure before the status is known shows the failure and no action`() {
        val state = TmdbRequestUiState(failed = true)
        assertEquals(TmdbRequestPhase.FAILED, state.phase)
        assertFalse(state.canRequestNow)
    }
}
