package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one rule the discovery list has to get right: a title is either saved or not, and
 * pressing the same control twice returns it to where it started.
 */
class TmdbWatchlistTest {
    private fun movie(
        id: Int,
        title: String = "Movie $id",
    ) = TmdbItem(id = id, mediaType = "movie", title = title)

    private fun show(id: Int) = TmdbItem(id = id, mediaType = "tv", name = "Show $id")

    @Test
    fun savingPutsTheTitleFirst() {
        assertEquals(
            listOf(movie(2), movie(1)),
            toggleWatchlist(listOf(movie(1)), movie(2)),
        )
    }

    @Test
    fun savingTheSameTitleAgainRemovesIt() {
        assertEquals(
            emptyList<TmdbItem>(),
            toggleWatchlist(listOf(movie(1)), movie(1)),
        )
    }

    @Test
    fun aMovieAndAShowWithTheSameIdAreDifferentTitles() {
        val current = toggleWatchlist(emptyList(), movie(7))
        assertFalse(current.containsWatchlist(show(7)))
        assertTrue(current.containsWatchlist(movie(7)))

        // Saving the show must not remove the film that happens to share its TMDB id.
        val both = toggleWatchlist(current, show(7))
        assertEquals(2, both.size)
        assertTrue(both.containsWatchlist(movie(7)))
        assertTrue(both.containsWatchlist(show(7)))
    }

    @Test
    fun theOldestEnthusiasmIsDroppedWhenFull() {
        val full = (1..MAX_WATCHLIST).map { movie(it) }
        val saved = toggleWatchlist(full, movie(99))
        assertEquals(MAX_WATCHLIST, saved.size)
        assertEquals(99, saved.first().id)
        assertFalse(saved.containsWatchlist(movie(MAX_WATCHLIST)))
        assertTrue(saved.containsWatchlist(movie(1)))
    }

    @Test
    fun theListDrawsFromTheStoredFields() {
        // The row has to draw with no network at all, so a saved title carries its poster and
        // caption and is not reduced to an id to be re-fetched later.
        val saved = toggleWatchlist(emptyList(), movie(3, title = "Heat")).single()
        assertEquals("Heat", saved.displayTitle)
        assertTrue(saved.isMovie)
    }
}
