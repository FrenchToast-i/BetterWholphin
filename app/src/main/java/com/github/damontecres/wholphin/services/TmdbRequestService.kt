package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.TmdbRequestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the viewer's Seerr server about a title TMDB showed them.
 *
 * TMDB knows what exists; Seerr knows what this server has or will take. A discovery poster used
 * to be a dead end for anything the library did not own — the dialog could only send the viewer
 * back to search, which by definition finds nothing. This is the other half of that bridge: the
 * same title can be looked up by its TMDB id, requested, and then tracked until it is playable.
 *
 * Nothing here depends on the TMDB key. Seerr carries its own TMDB access, so a viewer who has
 * connected Seerr can request from discovery even with no key configured.
 */
@Singleton
class TmdbRequestService
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        /**
         * What the server says about this title, or null when Seerr is not configured.
         *
         * Null is deliberately different from [SeerrAvailability.UNKNOWN]: one means there is
         * nothing to ask, the other means the title exists and nobody has it yet.
         */
        suspend fun status(item: TmdbItem): TmdbRequestStatus? {
            if (!seerrServerRepository.active.first()) return null
            val current = seerrServerRepository.current.firstOrNull() ?: return null
            val mediaInfo = mediaInfo(item)
            return TmdbRequestStatus(
                availability = SeerrAvailability.from(mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
                canRequest =
                    if (item.isMovie) {
                        current.requestMovieEnabled
                    } else {
                        current.requestTvEnabled
                    },
                jellyfinItemId = mediaInfo?.jellyfinId,
            )
        }

        /**
         * Ask the server for this title with its own defaults.
         *
         * The advanced flows on the Seerr detail pages let the viewer pick a profile and a folder
         * because they are asking "how should this be stored". Discovery asks "would you get this
         * for me", and the server's default profile is the correct answer to that question. A
         * title requested here can still be tuned from the Seerr page afterwards.
         *
         * A television request carries no seasons, which asks Seerr for the whole show rather
         * than silently picking one season.
         */
        suspend fun request(item: TmdbItem): TmdbRequestStatus {
            seerrService.api.requestApi.requestPost(requestPostFor(item))
            return status(item) ?: throw IllegalStateException("Seerr is not configured")
        }

        private suspend fun mediaInfo(item: TmdbItem): MediaInfo? =
            if (item.isMovie) {
                seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id).mediaInfo
            } else {
                seerrService.api.tvApi.tvTvIdGet(tvId = item.id).mediaInfo
            }
    }

/**
 * The request body for a discovered title.
 *
 * Split out of [TmdbRequestService] so the one thing that must be right — the TMDB id and the
 * media type travel together, and a movie is never posted as a show — is a plain function with a
 * test, rather than something only a live server can confirm.
 */
fun requestPostFor(item: TmdbItem): RequestPostRequest =
    RequestPostRequest(
        mediaType =
            if (item.isMovie) {
                RequestPostRequest.MediaType.MOVIE
            } else {
                RequestPostRequest.MediaType.TV
            },
        mediaId = item.id,
        is4k = false,
        tags = emptyList(),
    )
