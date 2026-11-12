package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * Pins the order the home discovery rail is seeded in: the most recently touched resume item
 * wins, the top of Next Up only seeds the rail when the viewer finished everything, and two
 * empty rows stay hidden rather than seeding from nothing.
 */
class TmdbHomeSeedTest {
    private fun item(lastPlayed: LocalDateTime?): BaseItem =
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 0L,
                        playCount = 0,
                        isFavorite = false,
                        lastPlayedDate = lastPlayed,
                        played = false,
                        key = "",
                        itemId = UUID.randomUUID(),
                    ),
            ),
        )

    @Test
    fun newestResumeItemWins() {
        val older = item(LocalDateTime.now().minusDays(3))
        val newer = item(LocalDateTime.now().minusHours(2))
        val nextUp = item(null)

        assertEquals(newer.id, TmdbHomeViewModel.selectSeed(listOf(older, newer), listOf(nextUp))?.id)
    }

    @Test
    fun resumeBeatsNextUp() {
        val resume = item(LocalDateTime.now().minusDays(30))
        val nextUp = item(null)

        assertEquals(resume.id, TmdbHomeViewModel.selectSeed(listOf(resume), listOf(nextUp))?.id)
    }

    @Test
    fun emptyResumeFallsBackToTopOfNextUp() {
        val first = item(null)
        val second = item(null)

        assertEquals(first.id, TmdbHomeViewModel.selectSeed(emptyList(), listOf(first, second))?.id)
    }

    @Test
    fun bothEmptyStaysHidden() {
        assertNull(TmdbHomeViewModel.selectSeed(emptyList(), emptyList()))
    }
}
