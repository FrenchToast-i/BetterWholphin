package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.WatchPartyAction
import com.github.damontecres.wholphin.data.model.WatchPartyState
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.PlayQueueUpdateReason
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * A watch party is only as good as its translations: ticks to milliseconds, commands to player
 * actions, queue updates to the title a guest should open. Each one is pinned here because a
 * wrong conversion is a party that drifts a second per minute or opens the wrong episode.
 */
class WatchPartyProtocolTest {
    private val itemId: UUID = UUID.randomUUID()

    private fun queue(
        itemIds: List<UUID>,
        index: Int,
        startPositionTicks: Long,
        playing: Boolean = true,
    ) = PlayQueueUpdate(
        reason = PlayQueueUpdateReason.NEW_PLAYLIST,
        lastUpdate = LocalDateTime.now(),
        playlist = itemIds.map { SyncPlayQueueItem(itemId = it, playlistItemId = UUID.randomUUID()) },
        playingItemIndex = index,
        startPositionTicks = startPositionTicks,
        isPlaying = playing,
        shuffleMode = GroupShuffleMode.SHUFFLE,
        repeatMode = GroupRepeatMode.REPEAT_ALL,
    )

    @Test
    fun ticksBecomeMilliseconds() {
        assertEquals(1_500L, WatchPartyProtocol.positionMs(15_000_000L))
        assertNull(WatchPartyProtocol.positionMs(null))
    }

    @Test
    fun aNegativeOrSubTickPositionIsZero() {
        assertEquals(0L, WatchPartyProtocol.positionMs(-5_000L))
        assertEquals(0L, WatchPartyProtocol.positionMs(9_999L))
    }

    @Test
    fun millisecondsBecomeTicks() {
        assertEquals(30_000_000L, WatchPartyProtocol.ticksFromMs(3_000L))
        assertEquals(0L, WatchPartyProtocol.ticksFromMs(-1L))
    }

    @Test
    fun pauseCarriesTheGroupsPosition() {
        assertEquals(
            WatchPartyAction.Pause(42_000L),
            WatchPartyProtocol.actionFor(SendCommandType.PAUSE, 420_000_000L),
        )
    }

    @Test
    fun unpauseWithoutAPositionStillPlays() {
        assertEquals(
            WatchPartyAction.Play(null),
            WatchPartyProtocol.actionFor(SendCommandType.UNPAUSE, null),
        )
    }

    @Test
    fun seekWithoutAPositionIsIgnored() {
        assertNull(WatchPartyProtocol.actionFor(SendCommandType.SEEK, null))
    }

    @Test
    fun stopIsStop() {
        assertEquals(
            WatchPartyAction.Stop,
            WatchPartyProtocol.actionFor(SendCommandType.STOP, null),
        )
    }

    @Test
    fun aPlayerWithinToleranceDoesNotSeek() {
        assertFalse(WatchPartyProtocol.needsSeek(10_000L, 11_000L))
        assertTrue(WatchPartyProtocol.needsSeek(10_000L, 11_600L))
    }

    @Test
    fun aPlayerWithNoPositionYetDoesNotSeek() {
        assertFalse(WatchPartyProtocol.needsSeek(null, 5_000L))
        assertFalse(WatchPartyProtocol.needsSeek(5_000L, null))
    }

    @Test
    fun theQueueUpdateNamesTheTitleToOpen() {
        val join =
            WatchPartyProtocol.joinFor(
                queue(listOf(UUID.randomUUID(), itemId), index = 1, startPositionTicks = 12_000_000L),
            )

        assertEquals(itemId, join?.itemId)
        assertEquals(1_200L, join?.positionMs)
        assertEquals(true, join?.playing)
    }

    @Test
    fun aQueueUpdateWithNoCurrentItemSaysNothing() {
        assertNull(WatchPartyProtocol.joinFor(queue(listOf(itemId), index = 3, startPositionTicks = 0L)))
        assertNull(WatchPartyProtocol.joinFor(queue(emptyList(), index = 0, startPositionTicks = 0L)))
    }

    @Test
    fun groupStatesMapOneToOne() {
        assertEquals(WatchPartyState.PLAYING, WatchPartyProtocol.stateOf(GroupStateType.PLAYING))
        assertEquals(WatchPartyState.PAUSED, WatchPartyProtocol.stateOf(GroupStateType.PAUSED))
        assertEquals(WatchPartyState.WAITING, WatchPartyProtocol.stateOf(GroupStateType.WAITING))
        assertEquals(WatchPartyState.IDLE, WatchPartyProtocol.stateOf(GroupStateType.IDLE))
    }

    @Test
    fun aRoomCarriesItsMemberCount() {
        val dto =
            GroupInfoDto(
                groupId = UUID.randomUUID(),
                groupName = "Movie night",
                state = GroupStateType.PAUSED,
                participants = listOf("session-a", "session-b"),
                lastUpdatedAt = LocalDateTime.now(),
            )

        val room = WatchPartyProtocol.roomFor(dto)

        assertEquals("Movie night", room.name)
        assertEquals(2, room.members)
        assertEquals(WatchPartyState.PAUSED, room.state)
    }

    @Test
    fun theDefaultRoomNameNamesThePersonAndTheTitle() {
        assertEquals("Main · The Bear", WatchPartyProtocol.defaultRoomName("Main", "The Bear"))
        assertEquals("The Bear", WatchPartyProtocol.defaultRoomName(" ", "The Bear"))
        assertEquals("Main's watch party", WatchPartyProtocol.defaultRoomName("Main", " "))
        assertEquals("Watch party", WatchPartyProtocol.defaultRoomName(null, null))
    }

    @Test
    fun theZeroItemIdIsNotARealItem() {
        assertNull(WatchPartyProtocol.playlistItemIdOrNull(UUID(0L, 0L)))
        assertEquals(itemId, WatchPartyProtocol.playlistItemIdOrNull(itemId))
    }
}
