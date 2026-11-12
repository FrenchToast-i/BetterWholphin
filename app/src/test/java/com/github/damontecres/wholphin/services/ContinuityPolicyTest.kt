package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pins what the app is willing to re-open after it was killed: a place, never a moment of
 * playback, never a screen that is a month old, and never a page belonging to another profile.
 */
class ContinuityPolicyTest {
    private val series =
        Destination.SeriesOverview(
            itemId = UUID(0, 1),
            type = BaseItemKind.SERIES,
        )

    private val movie = Destination.MediaItem(UUID(0, 2), BaseItemKind.MOVIE)

    @Test
    fun playbackIsNotAPlaceToReturnTo() {
        val stack =
            listOf(
                Destination.Home(),
                series,
                Destination.Playback(itemId = UUID(0, 9), positionMs = 30_000),
            )

        assertEquals(
            listOf(Destination.Home(), series),
            ContinuityPolicy.toPersist(stack),
        )
    }

    @Test
    fun everyFlavourOfPlayerIsDropped() {
        val players =
            listOf(
                Destination.Playback(UUID(0, 9), 0),
                Destination.PlaybackList(UUID(0, 9)),
                Destination.Slideshow(
                    parentId = null,
                    index = 0,
                    filter = CollectionFolderFilter(),
                    sortAndDirection = SortAndDirection.DEFAULT,
                    recursive = false,
                    startSlideshow = true,
                ),
            )

        players.forEach { player ->
            assertTrue(
                "expected $player to count as playback",
                ContinuityPolicy.isPlayback(player),
            )
            val persisted = ContinuityPolicy.toPersist(listOf(Destination.Home(), player))
            assertEquals(listOf(Destination.Home()), persisted)
        }
    }

    @Test
    fun aStackThatWasOnlyPlaybackFallsBackToHome() {
        val persisted =
            ContinuityPolicy.toPersist(
                listOf(
                    Destination.Playback(UUID(0, 9), 10_000),
                    Destination.PlaybackList(UUID(0, 9)),
                ),
            )

        assertEquals(listOf(Destination.Home()), persisted)
    }

    @Test
    fun aBrowseStackKeepsItsOrder() {
        val stack = listOf(Destination.Home(id = 4), movie, series)

        assertEquals(stack, ContinuityPolicy.toPersist(stack))
    }

    @Test
    fun anEmptyStackIsNotWorthWriting() {
        assertTrue(ContinuityPolicy.toPersist(emptyList()).isEmpty().not())
    }

    @Test
    fun onlyRecentScreensAreRestored() {
        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000

        assertFalse("no timestamp", ContinuityPolicy.isFresh(0L, now))
        assertTrue("just saved", ContinuityPolicy.isFresh(now, now))
        assertTrue("a week old", ContinuityPolicy.isFresh(now - 7 * day, now))
        assertTrue("29 days old", ContinuityPolicy.isFresh(now - 29 * day, now))
        assertFalse("31 days old", ContinuityPolicy.isFresh(now - 31 * day, now))
        // A clock that has moved backwards cannot make a future screen trustworthy.
        assertFalse("saved in the future", ContinuityPolicy.isFresh(now + day, now))
    }
}
