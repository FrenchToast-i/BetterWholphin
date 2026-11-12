package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pins the taste model behind "Play something": recent viewing decides the draw, unfamiliar genres
 * stay reachable, and the weighting never dead-ends on an empty or zero-weight pool.
 */
class SurprisePickerTest {
    @Test
    fun mostRecentGenreOutweighsOlderOnes() {
        // Newest first: the two newest titles are comedies, the oldest is a drama.
        val affinity =
            SurprisePicker.genreAffinity(
                listOf(
                    listOf("Comedy"),
                    listOf("Comedy"),
                    listOf("Drama"),
                ),
            )
        assertEquals(1.0, affinity.getValue("comedy"), 0.0001)
        assertTrue(affinity.getValue("drama") < affinity.getValue("comedy"))
    }

    @Test
    fun genreMatchingIsCaseInsensitive() {
        val affinity = SurprisePicker.genreAffinity(listOf(listOf("Science Fiction")))
        assertEquals(1.0, affinity.getValue("science fiction"), 0.0001)
        assertEquals(1.25, SurprisePicker.weight(listOf("science fiction"), affinity), 0.0001)
    }

    @Test
    fun emptyHistoryLeavesEveryCandidateEqual() {
        val affinity = SurprisePicker.genreAffinity(emptyList())
        assertTrue(affinity.isEmpty())
        assertEquals(0.25, SurprisePicker.weight(listOf("anything"), affinity), 0.0001)
        assertEquals(0.25, SurprisePicker.weight(listOf("drama"), affinity), 0.0001)
    }

    @Test
    fun unfamiliarGenresKeepTheBaseWeight() {
        val affinity = SurprisePicker.genreAffinity(listOf(listOf("Comedy")))
        assertEquals(0.25, SurprisePicker.weight(listOf("Horror"), affinity), 0.0001)
        assertTrue(SurprisePicker.weight(emptyList(), affinity) > 0.0)
    }

    @Test
    fun weightedDrawNeverPicksAZeroWeightCandidate() {
        val items = listOf("never", "always")
        repeat(200) { seed ->
            assertEquals(
                "always",
                SurprisePicker.pickWeighted(
                    items = items,
                    weightOf = { if (it == "always") 1.0 else 0.0 },
                    random = Random(seed),
                ),
            )
        }
    }

    @Test
    fun favouriteIsDrawnMoreOftenThanAnUnfamiliarOne() {
        val items = listOf("favourite", "stranger")
        val seed = 7
        val random = Random(seed)
        var favourite = 0
        repeat(1_000) {
            if (SurprisePicker.pickWeighted(items, { if (it == "favourite") 1.0 else 0.25 }, random) == "favourite") {
                favourite++
            }
        }
        // Weights are 1.0 vs 0.25, so the favourite should win roughly four times in five. The
        // bound is loose because the point is "leans on taste", not an exact distribution.
        assertTrue("favourite drawn $favourite/1000", favourite > 700)
    }

    @Test
    fun emptyPoolIsNullAndSingleItemIsThatItem() {
        assertNull(SurprisePicker.pickWeighted(emptyList<String>(), { 1.0 }, Random(1)))
        assertEquals("only", SurprisePicker.pickWeighted(listOf("only"), { 1.0 }, Random(1)))
    }
}
