package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.WatchParty
import com.github.damontecres.wholphin.data.model.WatchPartyAction
import com.github.damontecres.wholphin.data.model.WatchPartyEvent
import com.github.damontecres.wholphin.data.model.WatchPartyJoin
import com.github.damontecres.wholphin.data.model.WatchPartyResult
import com.github.damontecres.wholphin.data.model.WatchPartyRoomsState
import com.github.damontecres.wholphin.data.model.WatchPartyState
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.util.WatchPartyFrame
import com.github.damontecres.wholphin.util.WatchPartyProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Watch Together: the app's side of Jellyfin's SyncPlay.
 *
 * The server owns the room. This class only translates: REST calls to open, join or leave a room,
 * the socket's group updates into [WatchParty] state, and its commands into player actions (see
 * [WatchPartyProtocol] for the arithmetic). Nothing here pushes a page: only the page that knows
 * where the viewer is can decide to open a title, so the service hands out a join target and the
 * player or the dialog consumes it.
 *
 * A party is a session, not a preference: state is cleared when the profile changes and when the
 * room ends, so a viewer never inherits someone else's room.
 */
@Singleton
class WatchPartyService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val serverRepository: ServerRepository,
        private val socketTap: WatchPartySocketTap,
    ) {
        /** The profile's name, used to make room names recognisable to the other television. */
        val currentUserName: String?
            get() = serverRepository.currentUser?.name

        private val _party = MutableStateFlow<WatchParty?>(null)
        val party: StateFlow<WatchParty?> = _party.asStateFlow()

        private val _rooms = MutableStateFlow<WatchPartyRoomsState>(WatchPartyRoomsState.Loading)
        val rooms: StateFlow<WatchPartyRoomsState> = _rooms.asStateFlow()

        private val _joinTarget = MutableStateFlow<WatchPartyJoin?>(null)
        val joinTarget: StateFlow<WatchPartyJoin?> = _joinTarget.asStateFlow()

        private val _actions = MutableSharedFlow<WatchPartyAction>(extraBufferCapacity = 16)
        val actions = _actions.asSharedFlow()

        private val _events = MutableSharedFlow<WatchPartyEvent>(extraBufferCapacity = 16)
        val events = _events.asSharedFlow()

        /**
         * True between the create/join request and the server's group update.
         *
         * The REST call returns before the socket confirms the room, and the first group update
         * is the only place the protocol says who the host is. Keeping the flag here means a
         * viewer who presses Play immediately after opening a room still publishes the queue.
         */
        private var creating = false

        /** The title a host started before the socket confirmed the room. */
        private var pendingHostItem: Pair<UUID, Long>? = null

        /**
         * Bumped by every playback page that opens.
         *
         * Leaving the player normally leaves the room, but a guest joining a room *from* a player
         * replaces one playback page with another: the first page's release must not tear down a
         * party the next page is already following. A new session bumps this before the old one's
         * delayed leave fires, which is the difference between the two cases.
         */
        @Volatile
        private var playbackSession = 0
        private var leaveJob: kotlinx.coroutines.Job? = null

        init {
            subscribeToSockets()
            // A room belongs to the signed-in profile. Switching profiles must not leave the new
            // viewer in the previous one's party (Jellyfin would evict us anyway; this is the
            // local half of that).
            scope.launch {
                var userId: UUID? = null
                serverRepository.currentUserFlow.collectLatest { user ->
                    if (user?.id != userId) {
                        userId = user?.id
                        clearParty()
                    }
                }
            }
        }

        /**
         * Follow the server's socket frames.
         *
         * Deliberately not the SDK's typed subscription: its SyncPlay model declares only the
         * group id and the update type, so the members and the queue that arrive nested inside
         * the message never reach the app, and a party could be opened but not followed.
         * [WatchPartySocketTap] mirrors the raw frames; reconnects and keep-alives stay the SDK's
         * business, so there is nothing to retry here.
         */
        private fun subscribeToSockets() {
            scope.launch {
                socketTap.frames.collect { raw ->
                    WatchPartyProtocol.frameFor(raw)?.let(::handleFrame)
                }
            }
        }

        /** Ask the server which rooms are open. An empty list is a real answer. */
        suspend fun refreshRooms() {
            _rooms.value = WatchPartyRoomsState.Loading
            _rooms.value =
                try {
                    val groups = api.syncPlayApi.syncPlayGetGroups().content
                    WatchPartyRoomsState.Available(groups.map(WatchPartyProtocol::roomFor))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Could not list watch parties: %s", ex.javaClass.simpleName)
                    WatchPartyRoomsState.Failed
                }
        }

        /**
         * Open a room and wait for the server to confirm it.
         *
         * The wait is bounded: a server without SyncPlay, or one whose socket is down, must hand
         * the viewer an honest "not available" rather than a dialog that spins forever.
         */
        suspend fun startParty(name: String): WatchPartyResult {
            if (_party.value != null) return WatchPartyResult.Ok
            creating = true
            _joinTarget.value = null
            return try {
                api.syncPlayApi.syncPlayCreateGroup(NewGroupRequestDto(name))
                val created = withTimeoutOrNull(ROOM_TIMEOUT_MS) { _party.first { it != null } }
                if (created != null) {
                    WatchPartyResult.Ok
                } else {
                    creating = false
                    WatchPartyResult.Unavailable
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: InvalidStatusException) {
                creating = false
                if (ex.status == 401 || ex.status == 403) WatchPartyResult.NotAllowed else WatchPartyResult.Unavailable
            } catch (ex: Exception) {
                Timber.w("Could not open a watch party: %s", ex.javaClass.simpleName)
                creating = false
                WatchPartyResult.Unavailable
            }
        }

        /** Join an existing room and wait for its first update. */
        suspend fun join(groupId: UUID): WatchPartyResult {
            if (_party.value?.groupId == groupId) return WatchPartyResult.Ok
            creating = false
            _joinTarget.value = null
            return try {
                api.syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(groupId))
                val joined = withTimeoutOrNull(ROOM_TIMEOUT_MS) {
                    _party.first { it?.groupId == groupId }
                }
                if (joined != null) WatchPartyResult.Ok else WatchPartyResult.Unavailable
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: InvalidStatusException) {
                if (ex.status == 401 || ex.status == 403) WatchPartyResult.NotAllowed else WatchPartyResult.Unavailable
            } catch (ex: Exception) {
                Timber.w("Could not join a watch party: %s", ex.javaClass.simpleName)
                WatchPartyResult.Unavailable
            }
        }

        /**
         * Leave the room, stopping the group first when this client is the host.
         *
         * A host that simply left would leave the other televisions paused on a title nobody can
         * restart, so the group is stopped while there is still someone to stop it.
         */
        suspend fun leave() {
            val current = _party.value ?: return
            if (current.isHost) {
                runCatching { api.syncPlayApi.syncPlayStop() }
            }
            runCatching { api.syncPlayApi.syncPlayLeaveGroup() }
            clearParty()
        }

        /** A playback page opened; cancel any leave scheduled by the page it replaced. */
        fun notePlaybackSessionStart() {
            playbackSession++
            leaveJob?.cancel()
            leaveJob = null
        }

        /**
         * The playback page is being torn down.
         *
         * Leaving runs on the service's own scope (the player's ViewModel scope is about to die)
         * and after a short grace period, so a page that is being replaced by another playback
         * page — a guest opening the host's title — does not leave the room it just joined.
         */
        fun leaveIfSessionUnchanged() {
            val generation = playbackSession
            leaveJob?.cancel()
            leaveJob =
                scope.launch {
                    delay(LEAVE_GRACE_MS)
                    if (generation == playbackSession) leave()
                }
        }

        /** Drops local party state without asking the server. */
        fun clearParty() {
            creating = false
            pendingHostItem = null
            _joinTarget.value = null
            _party.value = null
        }

        /** The dialog/player has acted on the join target; a second consumer must not re-open it. */
        fun consumeJoinTarget() {
            _joinTarget.value = null
        }

        /**
         * Tell the group what this client just started.
         *
         * Only the host publishes a queue: guests follow what the host plays. The queue is one
         * title long on purpose — a party is "watch this together", and a host who moves on to
         * the next episode publishes it the same way.
         */
        fun onLocalPlaybackStarted(
            itemId: UUID,
            positionMs: Long,
        ) {
            val current = _party.value
            if (!creating && current?.isHost != true) return
            if (current == null) {
                // The room is still being confirmed; publish when it is.
                pendingHostItem = itemId to positionMs
                return
            }
            scope.launch { publishQueue(itemId, positionMs) }
        }

        private suspend fun publishQueue(
            itemId: UUID,
            positionMs: Long,
        ) {
            try {
                api.syncPlayApi.syncPlaySetNewQueue(
                    PlayRequestDto(
                        playingQueue = listOf(itemId),
                        playingItemPosition = 0,
                        startPositionTicks = WatchPartyProtocol.ticksFromMs(positionMs),
                    ),
                )
                api.syncPlayApi.syncPlayUnpause()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not publish the watch party queue: %s", ex.javaClass.simpleName)
            }
        }

        fun requestPause() {
            if (_party.value == null) return
            post("pause") { api.syncPlayApi.syncPlayPause() }
        }

        fun requestUnpause() {
            if (_party.value == null) return
            post("unpause") { api.syncPlayApi.syncPlayUnpause() }
        }

        fun requestSeek(positionMs: Long) {
            if (_party.value == null) return
            post("seek") {
                api.syncPlayApi.syncPlaySeek(
                    SeekRequestDto(WatchPartyProtocol.ticksFromMs(positionMs)),
                )
            }
        }

        fun requestStop() {
            if (_party.value == null) return
            post("stop") { api.syncPlayApi.syncPlayStop() }
        }

        private fun post(
            what: String,
            block: suspend () -> Unit,
        ) {
            scope.launch {
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Could not send watch party %s: %s", what, ex.javaClass.simpleName)
                }
            }
        }

        private fun handleFrame(frame: WatchPartyFrame) {
            when (frame) {
                is WatchPartyFrame.Group -> onGroupInfo(frame.info)
                is WatchPartyFrame.State -> onGroupState(frame.state)
                is WatchPartyFrame.Queue -> onQueueUpdate(frame.update)
                is WatchPartyFrame.Member -> {
                    _party.update { it?.copy(members = (it.members + if (frame.joined) 1 else -1).coerceAtLeast(1)) }
                    _events.tryEmit(
                        if (frame.joined) WatchPartyEvent.UserJoined(frame.name) else WatchPartyEvent.UserLeft(frame.name),
                    )
                }

                is WatchPartyFrame.Command ->
                    WatchPartyProtocol
                        .actionFor(frame.type, frame.positionTicks)
                        ?.let { _actions.tryEmit(it) }

                WatchPartyFrame.Ended -> {
                    clearParty()
                    _events.tryEmit(WatchPartyEvent.Ended)
                }

                WatchPartyFrame.NotAllowed -> {
                    creating = false
                    _events.tryEmit(WatchPartyEvent.NotAllowed)
                }
            }
        }

        private fun onGroupInfo(dto: GroupInfoDto) {
            val existing = _party.value
            val isHost = if (creating) true else existing?.isHost ?: false
            creating = false
            _party.value =
                WatchParty(
                    groupId = dto.groupId,
                    name = dto.groupName,
                    isHost = isHost,
                    members = dto.participants.size,
                    state = WatchPartyProtocol.stateOf(dto.state),
                    itemId = existing?.itemId,
                    itemTitle = existing?.itemTitle,
                    positionMs = existing?.positionMs ?: 0L,
                    playing = dto.state == GroupStateType.PLAYING,
                )
            pendingHostItem?.let { (itemId, positionMs) ->
                pendingHostItem = null
                scope.launch { publishQueue(itemId, positionMs) }
            }
        }

        private fun onGroupState(state: GroupStateType) {
            val existing = _party.value ?: return
            _party.value =
                existing.copy(
                    state = WatchPartyProtocol.stateOf(state),
                    playing = state == GroupStateType.PLAYING,
                )
        }

        private fun onQueueUpdate(update: PlayQueueUpdate) {
            val existing = _party.value ?: return
            val target = WatchPartyProtocol.joinFor(update) ?: return
            _party.value =
                existing.copy(
                    itemId = target.itemId,
                    positionMs = target.positionMs,
                    playing = target.playing,
                    state = if (target.playing) WatchPartyState.PLAYING else WatchPartyState.PAUSED,
                )
            // The host already has the title on screen; only a follower has something to open.
            if (!existing.isHost && target != null) {
                _joinTarget.value = target
            }
        }

        private companion object {
            const val ROOM_TIMEOUT_MS = 6_000L

            /** How long a closing playback page waits before it leaves the room. */
            const val LEAVE_GRACE_MS = 2_500L
        }
    }
