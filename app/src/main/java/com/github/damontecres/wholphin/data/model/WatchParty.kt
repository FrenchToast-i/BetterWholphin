package com.github.damontecres.wholphin.data.model

import java.util.UUID

/**
 * The state of a SyncPlay group, as Jellyfin reports it.
 *
 * [WAITING] is the "waiting for everyone to be ready" state between songs/episodes; it is not a
 * pause the viewer chose, so the UI says so instead of pretending playback is paused.
 */
enum class WatchPartyState {
    IDLE,
    WAITING,
    PAUSED,
    PLAYING,
}

/**
 * One watch party the viewer is currently in.
 *
 * [isHost] is the client that created the room. Jellyfin lets any member send commands, so this is
 * used for the queue: only the host publishes what is playing, otherwise every member would fight
 * over the queue.
 */
data class WatchParty(
    val groupId: UUID,
    val name: String,
    val isHost: Boolean,
    val members: Int,
    val state: WatchPartyState,
    /** The item the host is playing, once a queue has been published. */
    val itemId: UUID? = null,
    val itemTitle: String? = null,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
)

/** A room shown in the join list. */
data class WatchPartyRoom(
    val groupId: UUID,
    val name: String,
    val members: Int,
    val state: WatchPartyState,
)

/** What a guest should open when it joins (or when the host moves the party to a new title). */
data class WatchPartyJoin(
    val itemId: UUID,
    val positionMs: Long,
    val playing: Boolean,
)

/**
 * A playback change the group asked this client for.
 *
 * [Play] and [Pause] carry the group's position so a follower lands on the same frame instead of
 * inheriting whatever it was doing before the command arrived.
 */
sealed interface WatchPartyAction {
    data class Play(
        val positionMs: Long?,
    ) : WatchPartyAction

    data class Pause(
        val positionMs: Long?,
    ) : WatchPartyAction

    data class Seek(
        val positionMs: Long,
    ) : WatchPartyAction

    data object Stop : WatchPartyAction
}

/** Something worth a toast while a party is on. */
sealed interface WatchPartyEvent {
    data class UserJoined(
        val name: String,
    ) : WatchPartyEvent

    data class UserLeft(
        val name: String,
    ) : WatchPartyEvent

    /** The room ended (the host left, or the server dropped it). */
    data object Ended : WatchPartyEvent

    data object NotAllowed : WatchPartyEvent

    data object Unavailable : WatchPartyEvent
}

/** The result of opening or joining a room, so the dialog can say what went wrong. */
sealed interface WatchPartyResult {
    data object Ok : WatchPartyResult

    data object NotAllowed : WatchPartyResult

    data object Unavailable : WatchPartyResult
}

/** The join list's state; an empty list is a valid answer, a failure is not. */
sealed interface WatchPartyRoomsState {
    data object Loading : WatchPartyRoomsState

    data class Available(
        val rooms: List<WatchPartyRoom>,
    ) : WatchPartyRoomsState

    data object Failed : WatchPartyRoomsState
}
