package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the empty search page's history: newest first, one entry per query, and never longer than
 * the two chip rows it is drawn in.
 */
class RecentSearchServiceTest {
    @Test
    fun newestQueryGoesFirst() {
        assertEquals(
            listOf("Silo", "Andor"),
            mergeRecentSearches(listOf("Andor"), "Silo"),
        )
    }

    @Test
    fun repeatedQueryMovesToFrontInsteadOfDuplicating() {
        assertEquals(
            listOf("Andor", "Silo", "Severance"),
            mergeRecentSearches(listOf("Silo", "Andor", "Severance"), "andor"),
        )
    }

    @Test
    fun historyIsCapped() {
        val current = listOf("1st", "2nd", "3rd", "4th", "5th", "6th")
        val merged = mergeRecentSearches(current, "7th")
        assertEquals(MAX_RECENT_SEARCHES, merged.size)
        assertEquals("7th", merged.first())
        assertEquals("5th", merged.last())
    }

    @Test
    fun surroundingSpaceIsTrimmed() {
        assertEquals(
            listOf("Silo"),
            mergeRecentSearches(emptyList(), "  Silo "),
        )
    }
}
