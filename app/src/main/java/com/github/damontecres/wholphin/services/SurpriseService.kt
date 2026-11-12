package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks one thing worth watching, from the whole library, in the viewer's taste.
 *
 * The app had a shuffle action per library, which answers "shuffle *this* folder" but not the
 * question a viewer actually has when they sit down: "what should I watch?" Browsing is the
 * expensive part of a media client; this turns the decision into one press from any page.
 *
 * What it returns:
 * - Nothing already finished if there is anything unwatched left, so the button moves the viewer
 *   forward rather than back.
 * - A movie or a **series**, never an episode. A series is what the app already knows how to
 *   resume: `Destination.Playback` on a series asks the server for the next unwatched episode and
 *   queues the rest, so "play something" on a show continues the story where the viewer left it.
 * - Not one of the last [RECENT_LIMIT] picks. A weighted draw over a finished library happily
 *   returns the same title twice in a row, which reads as a broken button.
 *
 * The taste model itself is [SurprisePicker], which is pure and unit tested.
 */
@Singleton
class SurpriseService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val tasteProfile: TasteProfileService,
    ) {
        private val recent = ArrayDeque<UUID>()

        // The bar builds one ViewModel per destination entry, all sharing this singleton, so two
        // picks can overlap on a fast double press. The recency list is the only shared mutable
        // state and it must stay a single sequence.
        private val mutex = Mutex()

        suspend fun pick(): BaseItem? = mutex.withLock { pickLocked() }

        private suspend fun pickLocked(): BaseItem? {
            val userId = serverRepository.currentUser?.id ?: return null
            // The For You row reads the same profile, so the button and the shelf never answer
            // the same evening with two different ideas of the viewer's taste.
            val affinity = tasteProfile.profile(userId).affinity
            val candidates =
                fetchCandidates(userId, ItemFilter.IS_UNPLAYED)
                    .ifEmpty {
                        // Everything is watched: replaying something beats a button that does
                        // nothing, so fall back to the whole library.
                        fetchCandidates(userId, ItemFilter.IS_PLAYED)
                    }
            if (candidates.isEmpty()) return null
            val fresh = candidates.filterNot { it.id in recent }
            val picked =
                SurprisePicker.pickWeighted(
                    items = fresh.ifEmpty { candidates },
                    weightOf = { SurprisePicker.weight(it.genres.orEmpty(), affinity) },
                    random = Random.Default,
                ) ?: return null
            recent.addLast(picked.id)
            while (recent.size > RECENT_LIMIT) {
                recent.removeFirst()
            }
            return BaseItem(picked, false)
        }

        /**
         * A random slice of the library, so the pick does not always come out of the same corner
         * of a large collection, and so the request stays cheap on a big library.
         */
        private suspend fun fetchCandidates(
            userId: UUID,
            filter: ItemFilter,
        ): List<BaseItemDto> =
            api.itemsApi
                .getItems(
                    userId = userId,
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    filters = listOf(filter),
                    sortBy = listOf(ItemSortBy.RANDOM),
                    fields = listOf(ItemFields.GENRES),
                    limit = CANDIDATE_LIMIT,
                ).content.items

        companion object {
            /** Candidate pool size. Larger is more variety, at the cost of one bigger response. */
            const val CANDIDATE_LIMIT = 200

            /** Picks that will not be repeated. */
            const val RECENT_LIMIT = 5
        }
    }
