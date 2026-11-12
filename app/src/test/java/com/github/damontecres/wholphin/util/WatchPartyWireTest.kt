package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.WatchPartyAction
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.SendCommandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frames captured from a live Jellyfin 10.11 server, reduced to the shapes the party acts on.
 *
 * The SDK's own SyncPlay model keeps only the group id and the update type, so the nested payload
 * these tests read was invisible to the app before the socket tap. Every case here is a real frame
 * from the emulator's session at 2026-11-10 19:39.
 */
class WatchPartyWireTest {
    private val groupJoined =
        """
        {"MessageId":"0ceeabd441e34ba5abe5a2d2577113de","Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","GroupName":"Main · Black Panther","State":"Waiting","Participants":["Main"],"LastUpdatedAt":"2026-11-11T01:39:02.1358735Z"},"Type":"GroupJoined"},"MessageType":"SyncPlayGroupUpdate"}
        """.trimIndent()

    private val playQueue =
        """
        {"MessageId":"06234a7df46f40ee89b77c4cc9e2558f","Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Data":{"Reason":"NewPlaylist","LastUpdate":"2026-11-11T01:36:56.6992623Z","Playlist":[{"ItemId":"c6b1a85f-308a-9d0b-da2c-0f80640b7f61","PlaylistItemId":"b6a9f5f0-6a4d-4f6f-8f1f-2c1d1a1b3c4d"}],"PlayingItemIndex":0,"StartPositionTicks":58630000,"IsPlaying":false,"ShuffleMode":"Sorted","RepeatMode":"RepeatNone"},"Type":"PlayQueue"},"MessageType":"SyncPlayGroupUpdate"}
        """.trimIndent()

    @Test
    fun groupJoinedCarriesTheRoom() {
        val frame = WatchPartyProtocol.frameFor(groupJoined)
        assertTrue(frame is WatchPartyFrame.Group)
        val info = (frame as WatchPartyFrame.Group).info
        assertEquals("Main · Black Panther", info.groupName)
        assertEquals(GroupStateType.WAITING, info.state)
        assertEquals(listOf("Main"), info.participants)
    }

    @Test
    fun playQueueCarriesTheTitleAndPosition() {
        val frame = WatchPartyProtocol.frameFor(playQueue)
        assertTrue(frame is WatchPartyFrame.Queue)
        val join = WatchPartyProtocol.joinFor((frame as WatchPartyFrame.Queue).update)
        assertEquals("c6b1a85f-308a-9d0b-da2c-0f80640b7f61", join?.itemId.toString())
        assertEquals(5_863L, join?.positionMs)
    }

    @Test
    fun aCommandBecomesAPlayerAction() {
        val frame =
            WatchPartyProtocol.frameFor(
                """
                {"Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","PlaylistItemId":"b6a9f5f0-6a4d-4f6f-8f1f-2c1d1a1b3c4d","When":"2026-11-11T01:36:56.6992623Z","PositionTicks":58630000,"Command":"Unpause","EmittedAt":"2026-11-11T01:36:56.6992623Z"},"MessageType":"SyncPlayCommand"}
                """.trimIndent(),
            )
        assertTrue(frame is WatchPartyFrame.Command)
        val command = frame as WatchPartyFrame.Command
        assertEquals(SendCommandType.UNPAUSE, command.type)
        assertEquals(WatchPartyAction.Play(5_863L), WatchPartyProtocol.actionFor(command.type, command.positionTicks))
    }

    @Test
    fun theQueueAnnouncementForAnEmptyRoomHasNoTarget() {
        val frame =
            WatchPartyProtocol.frameFor(
                """
                {"Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Data":{"Reason":"NewPlaylist","LastUpdate":"2026-11-11T01:36:56.6992623Z","Playlist":[],"PlayingItemIndex":-1,"StartPositionTicks":0,"IsPlaying":false,"ShuffleMode":"Sorted","RepeatMode":"RepeatNone"},"Type":"PlayQueue"},"MessageType":"SyncPlayGroupUpdate"}
                """.trimIndent(),
            )
        assertTrue(frame is WatchPartyFrame.Queue)
        assertNull(WatchPartyProtocol.joinFor((frame as WatchPartyFrame.Queue).update))
    }

    @Test
    fun someoneJoiningIsAName() {
        val frame =
            WatchPartyProtocol.frameFor(
                """
                {"Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Data":"Family Room TV","Type":"UserJoined"},"MessageType":"SyncPlayGroupUpdate"}
                """.trimIndent(),
            )
        assertEquals(WatchPartyFrame.Member("Family Room TV", joined = true), frame)
    }

    @Test
    fun someoneLeavingIsANameToo() {
        val frame =
            WatchPartyProtocol.frameFor(
                """
                {"Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Data":"Family Room TV","Type":"UserLeft"},"MessageType":"SyncPlayGroupUpdate"}
                """.trimIndent(),
            )
        assertEquals(WatchPartyFrame.Member("Family Room TV", joined = false), frame)
    }

    @Test
    fun theRoomEndingIsItsOwnFrame() {
        val frame =
            WatchPartyProtocol.frameFor(
                """
                {"Data":{"GroupId":"0befab092aa747a4b90ef2934fd03742","Type":"GroupLeft"},"MessageType":"SyncPlayGroupUpdate"}
                """.trimIndent(),
            )
        assertEquals(WatchPartyFrame.Ended, frame)
    }

    @Test
    fun anUnrelatedFrameIsIgnored() {
        assertNull(
            WatchPartyProtocol.frameFor(
                """{"MessageType":"KeepAlive","MessageId":"73b06371e94f4b5698f0750d2eb51ef1"}""",
            ),
        )
        assertNull(WatchPartyProtocol.frameFor("not json at all"))
        assertNull(WatchPartyProtocol.frameFor("""{"MessageType":"SyncPlayGroupUpdate","Data":{"Type":"PlayQueue"}}"""))
    }
}
