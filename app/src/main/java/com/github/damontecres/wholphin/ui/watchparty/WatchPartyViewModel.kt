package com.github.damontecres.wholphin.ui.watchparty

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.WatchPartyJoin
import com.github.damontecres.wholphin.data.model.WatchPartyResult
import com.github.damontecres.wholphin.services.WatchPartyService
import com.github.damontecres.wholphin.util.WatchPartyProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

/** What the dialog should do once a room request succeeds. */
sealed interface WatchPartyOutcome {
    /** The room exists; the page that opened it should start its title. */
    data object Started : WatchPartyOutcome

    /** The viewer joined; open [target] when there is one. */
    data class Joined(
        val target: WatchPartyJoin?,
    ) : WatchPartyOutcome
}

/**
 * The dialog's state.
 *
 * The party itself lives in the singleton [WatchPartyService] because the player follows it too;
 * this view model only owns the dialog's own request lifecycle (busy, error, outcome) so a slow
 * server cannot leave the dialog spinning.
 */
@HiltViewModel
class WatchPartyViewModel
    @Inject
    constructor(
        private val service: WatchPartyService,
    ) : ViewModel() {
        val party = service.party
        val rooms = service.rooms

        private val _outcome = MutableStateFlow<WatchPartyOutcome?>(null)
        val outcome = _outcome.asStateFlow()

        var busy by mutableStateOf(false)
            private set

        var error by mutableStateOf<WatchPartyResult?>(null)
            private set

        /** The default room name names the profile and the title being watched. */
        fun roomName(title: String?): String =
            WatchPartyProtocol.defaultRoomName(service.currentUserName, title)

        fun refresh() {
            viewModelScope.launch { service.refreshRooms() }
        }

        fun start(title: String?) {
            if (busy) return
            busy = true
            error = null
            viewModelScope.launch {
                val result = service.startParty(roomName(title))
                busy = false
                when (result) {
                    WatchPartyResult.Ok -> _outcome.value = WatchPartyOutcome.Started
                    else -> error = result
                }
            }
        }

        fun join(groupId: UUID) {
            if (busy) return
            busy = true
            error = null
            viewModelScope.launch {
                val result = service.join(groupId)
                if (result == WatchPartyResult.Ok) {
                    // The group update and the queue update are separate messages; waiting a
                    // moment for the queue is what lets the guest open the host's title instead
                    // of landing on a room with nothing to play.
                    val target = withTimeoutOrNull(1_500L) { service.joinTarget.first { it != null } }
                    busy = false
                    _outcome.value = WatchPartyOutcome.Joined(target)
                } else {
                    busy = false
                    error = result
                }
            }
        }

        fun leave() {
            if (busy) return
            busy = true
            viewModelScope.launch {
                service.leave()
                busy = false
            }
        }

        fun consumeOutcome() {
            _outcome.value = null
            service.consumeJoinTarget()
        }
    }
