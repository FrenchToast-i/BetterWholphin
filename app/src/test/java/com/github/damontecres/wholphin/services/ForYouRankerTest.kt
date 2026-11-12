package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pins the rules behind For You: taste gates the row, similarity is a strong prior, ratings only
 * break ties, and a row that mostly follows one show says so.
 */
class ForYouRankerTest {
    private val affinity =
        mapOf(
            "science fiction" to 1.0,
            "drama" to 0.6,
        )

    private fun candidate(
        id: Int,
        name: String = "Item $id",
        genres: List<String> = listOf("Science Fiction"),
        similarPosition: Int? = null,
        rating: Double? = null,
    ) = ForYouCandidate(
        id = UUID(0, id.toLong()),
        name = name,
        genres = genres,
        type = BaseItemKind.MOVIE,
        similarPosition = similarPosition,
        communityRating = rating,
    )

    @Test
    fun highRatedTitlesOutsideTheViewersTasteAreNotRecommended() {
        val ranked =
            ForYouRanker.rank(
                candidates =
                    listOf(
                        candidate(1, "Matches", genres = listOf("Drama")),
                        candidate(2, "Unfamiliar", genres = listOf("Horror"), rating = 9.9),
                        candidate(3, "No genres", genres = emptyList(), rating = 9.5),
                    ),
                affinity = affinity,
                limit = 10,
            )
        assertEquals(listOf("Matches"), ranked.map { it.name })
    }

    @Test
    fun similarTitleWithoutAMatchingGenreIsStillWorthRecommending() {
        val ranked =
            ForYouRanker.rank(
                candidates =
                    listOf(
                        candidate(1, "Neighbour", genres = emptyList(), similarPosition = 0),
                    ),
                affinity = affinity,
                limit = 10,
            )
        assertEquals(listOf("Neighbour"), ranked.map { it.name })
    }

    @Test
    fun similarityDecaysWithPositionInTheSimilarList() {
        val ranked =
            ForYouRanker.rank(
                candidates =
                    listOf(
                        candidate(1, "Later", similarPosition = 12),
                        candidate(2, "First", similarPosition = 0),
                    ),
                affinity = emptyMap(),
                limit = 10,
            )
        assertEquals(listOf("First", "Later"), ranked.map { it.name })
    }

    @Test
    fun ratingOnlyBreaksTiesWithinTheSameTaste() {
        val ranked =
            ForYouRanker.rank(
                candidates =
                    listOf(
                        candidate(1, "Seven", rating = 7.0),
                        candidate(2, "Nine", rating = 9.0),
                        candidate(3, "TwoGenres", genres = listOf("Science Fiction", "Drama"), rating = 1.0),
                    ),
                affinity = affinity,
                limit = 10,
            )
        assertEquals(listOf("TwoGenres", "Nine", "Seven"), ranked.map { it.name })
    }

    @Test
    fun duplicatesAreRemovedAndTheLimitIsRespected() {
        val ranked =
            ForYouRanker.rank(
                candidates =
                    listOf(
                        candidate(1, "One"),
                        candidate(1, "One again"),
                        candidate(2, "Two"),
                        candidate(3, "Three"),
                    ),
                affinity = affinity,
                limit = 2,
            )
        // Equal scores fall back to the name, which keeps the row stable between loads.
        assertEquals(listOf("One", "Three"), ranked.map { it.name })
        assertEquals(2, ranked.size)
    }

    @Test
    fun seedReasonWinsWhenMostOfTheRowIsSimilar() {
        val items = listOf(candidate(1, similarPosition = 0), candidate(2, similarPosition = 3), candidate(3))
        val reason =
            ForYouRanker.reason(
                seedTitle = "Severance",
                similarIds = setOf(items[0].id, items[1].id),
                items = items,
                topGenres = listOf("Science Fiction"),
            )
        assertEquals(ForYouReason.BecauseYouWatched("Severance"), reason)
    }

    @Test
    fun genreReasonIsUsedWhenTheRowMostlyFollowsGenres() {
        val items = listOf(candidate(1, similarPosition = 0), candidate(2), candidate(3), candidate(4))
        val reason =
            ForYouRanker.reason(
                seedTitle = "Severance",
                similarIds = setOf(items[0].id),
                items = items,
                topGenres = listOf("Science Fiction", "Drama", "Mystery"),
            )
        assertEquals(
            ForYouReason.BecauseYouWatch(listOf("Science Fiction", "Drama")),
            reason,
        )
    }

    @Test
    fun withoutASeedTheRowAlwaysExplainsItselfByGenre() {
        val items = listOf(candidate(1, similarPosition = 0), candidate(2, similarPosition = 1))
        val reason =
            ForYouRanker.reason(
                seedTitle = null,
                similarIds = items.map { it.id }.toSet(),
                items = items,
                topGenres = listOf("Drama"),
            )
        assertTrue(reason is ForYouReason.BecauseYouWatch)
        assertFalse(reason is ForYouReason.BecauseYouWatched)
    }
}
