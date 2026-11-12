package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A request body is the only thing this feature sends to Seerr, and a wrong id or a movie posted
 * as a show would fetch the wrong title. The service call itself needs a server; the body does
 * not, so it is pinned here for both media types.
 */
class TmdbRequestServiceTest {
    @Test
    fun `a movie travels as a movie with its TMDB id`() {
        val body = requestPostFor(TmdbItem(id = 603, mediaType = "movie", title = "The Matrix"))

        assertEquals(RequestPostRequest.MediaType.MOVIE, body.mediaType)
        assertEquals(603, body.mediaId)
        assertEquals(false, body.is4k)
    }

    @Test
    fun `a show travels as television with its TMDB id`() {
        val body = requestPostFor(TmdbItem(id = 1396, mediaType = "tv", name = "Breaking Bad"))

        assertEquals(RequestPostRequest.MediaType.TV, body.mediaType)
        assertEquals(1396, body.mediaId)
    }

    @Test
    fun `a show requests every season rather than one`() {
        // Null seasons is Seerr's "the whole show". A concrete list here would quietly ask for
        // season one of a series the viewer chose as a whole.
        assertNull(requestPostFor(TmdbItem(id = 1396, mediaType = "tv")).seasons)
    }

    @Test
    fun `a request takes the server's defaults rather than guessing a folder`() {
        val body = requestPostFor(TmdbItem(id = 603, mediaType = "movie"))

        assertNull(body.profileId)
        assertNull(body.serverId)
        assertNull(body.rootFolder)
    }
}
