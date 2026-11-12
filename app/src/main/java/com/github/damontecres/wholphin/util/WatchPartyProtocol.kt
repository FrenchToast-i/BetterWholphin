package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.WatchPartyAction
import com.github.damontecres.wholphin.data.model.WatchPartyJoin
import com.github.damontecres.wholphin.data.model.WatchPartyRoom
import com.github.damontecres.wholphin.data.model.WatchPartyState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupUpdateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import java.util.UUID

/**
 * One socket message, reduced to the part a party acts on.
 *
 * The SDK's own subscription hands over a `GroupUpdate`, which declares only the group id and the
 * type: the nested payload (members, state, queue) is lost on the way in. Frames are therefore
 * decoded from the socket's raw text, and this is what survives the translation.
 */
sealed interface WatchPartyFrame {
    /** The room itself: its name, state and who is in it. */
    data class Group(
        val info: GroupInfoDto,
    ) : WatchPartyFrame

    /** Only the playing/paused state changed. */
    data class State(
        val state: GroupStateType,
    ) : WatchPartyFrame

    /** The party moved to a title (or to a new position in it). */
    data class Queue(
        val update: PlayQueueUpdate,
    ) : WatchPartyFrame

    /** Someone joined or left the room. */
    data class Member(
        val name: String,
        val joined: Boolean,
    ) : WatchPartyFrame

    /** A transport command from the group (pause, unpause, seek, stop). */
    data class Command(
        val type: SendCommandType?,
        val positionTicks: Long?,
    ) : WatchPartyFrame

    /** The room is gone. */
    data object Ended : WatchPartyFrame

    /** The server refused to open or join a room. */
    data object NotAllowed : WatchPartyFrame
}

/**
 * The arithmetic of following a watch party, kept apart from the service and the player.
 *
 * Jellyfin's SyncPlay protocol is a stream of small messages: a state update says the group is
 * playing, a command says what changed and where, and a queue update says which title and which
 * position the group is on. Every interesting bug in a party client is a translation bug between
 * those three shapes and this app's player, so the translations live here where a test can pin
 * them instead of in a socket handler.
 */
object WatchPartyProtocol {
    /**
     * The wire shape of a socket message: a type and an untyped payload.
     *
     * Nothing here is read by name from the SDK models, because the payload is exactly what those
     * models drop. [ApiSerializer] is the SDK's own JSON configuration, so the nested payload can
     * then be decoded into the SDK's models ([GroupInfoDto], [PlayQueueUpdate], [SendCommand]).
     */
    @Serializable
    private data class SocketEnvelope(
        val MessageType: String,
        val Data: JsonElement? = null,
    )

    @Serializable
    private data class GroupUpdateEnvelope(
        val Type: GroupUpdateType,
        val Data: JsonElement? = null,
    )

    /**
     * Decode one socket frame, or null when it is not a party message.
     *
     * A malformed frame is ignored rather than thrown: one bad message from a server update must
     * not end the subscription and take the party down with it.
     */
    fun frameFor(raw: String): WatchPartyFrame? {
        val envelope = decode<SocketEnvelope>(raw) ?: return null
        val payload = envelope.Data ?: return null
        return when (envelope.MessageType) {
            "SyncPlayGroupUpdate" -> groupFrame(payload)
            "SyncPlayCommand" ->
                decode<SendCommand>(payload)?.let {
                    WatchPartyFrame.Command(it.command, it.positionTicks)
                }

            else -> null
        }
    }

    private fun groupFrame(payload: JsonElement): WatchPartyFrame? {
        val update = decode<GroupUpdateEnvelope>(payload) ?: return null
        val data = update.Data
        return when (update.Type) {
            GroupUpdateType.GROUP_JOINED -> data?.let { decode<GroupInfoDto>(it) }?.let(WatchPartyFrame::Group)
            GroupUpdateType.STATE_UPDATE ->
                data?.let { decode<org.jellyfin.sdk.model.api.GroupStateUpdate>(it) }
                    ?.let { WatchPartyFrame.State(it.state) }

            GroupUpdateType.PLAY_QUEUE -> data?.let { decode<PlayQueueUpdate>(it) }?.let(WatchPartyFrame::Queue)
            GroupUpdateType.USER_JOINED -> data?.name()?.let { WatchPartyFrame.Member(it, joined = true) }
            GroupUpdateType.USER_LEFT -> data?.name()?.let { WatchPartyFrame.Member(it, joined = false) }
            GroupUpdateType.GROUP_LEFT,
            GroupUpdateType.NOT_IN_GROUP,
            GroupUpdateType.GROUP_DOES_NOT_EXIST,
            -> WatchPartyFrame.Ended

            GroupUpdateType.CREATE_GROUP_DENIED,
            GroupUpdateType.JOIN_GROUP_DENIED,
            GroupUpdateType.LIBRARY_ACCESS_DENIED,
            -> WatchPartyFrame.NotAllowed
        }
    }

    /** User joined/left frames carry the name as a bare JSON string. */
    private fun JsonElement.name(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private inline fun <reified T> decode(text: String): T? =
        runCatching { ApiSerializer.json.decodeFromString<T>(text) }.getOrNull()

    private inline fun <reified T> decode(element: JsonElement): T? =
        runCatching { ApiSerializer.json.decodeFromJsonElement<T>(element) }.getOrNull()

    /**
     * How far apart two players may be before the follower corrects itself.
     *
     * A second and a half is under a spoken sentence but well above normal drift: on a LAN the
     * clients report positions within a few hundred milliseconds, and a smaller tolerance would
     * seek every few seconds, which is audible as a stutter.
     */
    const val DRIFT_TOLERANCE_MS: Long = 1_500L

    /** Jellyfin counts a millisecond as 10,000 ticks. */
    private const val TICKS_PER_MS: Long = 10_000L

    /** A position in Jellyfin ticks, or null when the message does not carry one. */
    fun positionMs(ticks: Long?): Long? = ticks?.let { (it / TICKS_PER_MS).coerceAtLeast(0L) }

    /** Milliseconds to the ticks a request DTO expects. */
    fun ticksFromMs(positionMs: Long): Long = positionMs.coerceAtLeast(0L) * TICKS_PER_MS

    /**
     * The player action a SyncPlay command asks for.
     *
     * A pause and an unpause carry a position; seeking to it first is what makes two televisions
     * line up on the same frame rather than resuming wherever they happened to be. A seek without
     * a position is meaningless and is ignored.
     */
    fun actionFor(
        type: SendCommandType?,
        positionTicks: Long?,
    ): WatchPartyAction? =
        when (type) {
            SendCommandType.PAUSE -> WatchPartyAction.Pause(positionMs(positionTicks))
            SendCommandType.UNPAUSE -> WatchPartyAction.Play(positionMs(positionTicks))
            SendCommandType.SEEK -> positionMs(positionTicks)?.let { WatchPartyAction.Seek(it) }
            SendCommandType.STOP -> WatchPartyAction.Stop
            else -> null
        }

    /**
     * Whether this player is far enough from the group to need a corrective seek.
     *
     * A null [localMs] means the player has no position yet (still loading), so there is nothing
     * to correct.
     */
    fun needsSeek(
        localMs: Long?,
        targetMs: Long?,
        toleranceMs: Long = DRIFT_TOLERANCE_MS,
    ): Boolean {
        if (localMs == null || targetMs == null) return false
        return kotlin.math.abs(localMs - targetMs) > toleranceMs
    }

    /**
     * The title and position a guest should open for a queue update.
     *
     * The queue is authoritative when it arrives: it is the only message that carries the item
     * id, so a guest that missed the unpause still knows what the group is watching. An unusable
     * index means the update says nothing about the current title.
     */
    fun joinFor(update: PlayQueueUpdate): WatchPartyJoin? {
        val item = update.playlist.getOrNull(update.playingItemIndex) ?: return null
        return WatchPartyJoin(
            itemId = item.itemId,
            positionMs = positionMs(update.startPositionTicks) ?: 0L,
            playing = update.isPlaying,
        )
    }

    fun stateOf(type: GroupStateType): WatchPartyState =
        when (type) {
            GroupStateType.IDLE -> WatchPartyState.IDLE
            GroupStateType.WAITING -> WatchPartyState.WAITING
            GroupStateType.PAUSED -> WatchPartyState.PAUSED
            GroupStateType.PLAYING -> WatchPartyState.PLAYING
        }

    fun roomFor(dto: GroupInfoDto): WatchPartyRoom =
        WatchPartyRoom(
            groupId = dto.groupId,
            name = dto.groupName,
            members = dto.participants.size,
            state = stateOf(dto.state),
        )

    /**
     * A default room name that a second television can recognise.
     *
     * The server only carries a name, not a poster and not the item, so the name is the whole
     * identity of a room in the join list. It names the title being watched, falling back to the
     * viewer when a player has no title (an intro, or a live channel).
     */
    fun defaultRoomName(
        user: String?,
        title: String?,
    ): String {
        val who = user?.takeIf { it.isNotBlank() }
        val what = title?.takeIf { it.isNotBlank() }
        return when {
            who != null && what != null -> "$who · $what"
            what != null -> what
            who != null -> "$who's watch party"
            else -> "Watch party"
        }
    }

    /** The item id a command refers to, when the message carries one. */
    fun playlistItemIdOrNull(id: UUID?): UUID? = id?.takeIf { it != UUID(0L, 0L) }
}
