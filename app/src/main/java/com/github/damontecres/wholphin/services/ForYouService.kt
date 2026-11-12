package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the For You row will draw. */
data class ForYouResult(
    val items: List<BaseItem>,
    val reason: ForYouReason,
)

/**
 * The library's own answer to "what should I watch?".
 *
 * Discovery elsewhere in the app looks outward: TMDB recommends films and shows the server does
 * not own, which is useful for building a wishlist but always ends in a request or a search. This
 * row looks inward. Every card is a title the viewer can start tonight, chosen from what they have
 * been watching — the missing half of the recommendation loop, and the reason the home page can
 * open with an answer instead of a list of folders.
 *
 * Two signals are merged:
 * - Jellyfin's similar titles for the last couple of things watched. Metadata neighbours are
 *   specific: "more like Severance", not "more science fiction".
 * - The genres the viewer returns to, from the same [TasteProfileService] that feeds the bar's
 *   Play something button, so the two surfaces agree about taste.
 *
 * The row is allowed to be absent. A new viewer, a library without genres, a server that is slow:
 * the home page stays exactly as it was rather than showing a heading with nothing under it.
 */
@Singleton
class ForYouService
    @Inject
    constructor(
        private val api: ApiClient,
        private val tasteProfile: TasteProfileService,
    ) {
        suspend fun recommendations(
            userId: UUID,
            limit: Int = MAX_ITEMS,
        ): ForYouResult? {
            val profile = tasteProfile.profile(userId)
            if (profile.seeds.isEmpty() || profile.affinity.isEmpty() || profile.topGenres.isEmpty()) {
                return null
            }

            val seedIds = profile.seeds.map { it.id }.toSet()
            val seeds = profile.seeds.take(SIMILAR_SEEDS)
            // The response records are kept alongside the slim ranking keys so the cards draw
            // without a second round trip per row.
            val dtosById = mutableMapOf<UUID, BaseItemDto>()

            // One failing call must not take the whole row with it: a similar list is enrichment,
            // the genre query alone is still a recommendation.
            val similarCandidates =
                seeds.mapNotNull { seed ->
                    quiet("similar titles") {
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userId,
                                    itemId = seed.id,
                                    fields = RECOMMENDATION_FIELDS,
                                    limit = SIMILAR_LIMIT,
                                ),
                            ).content.items
                            .mapIndexedNotNull { index, dto ->
                                dto.toCandidate(index)?.also { dtosById[dto.id] = dto }
                            }
                    }
                }.flatten()

            val genreCandidates =
                quiet("genre titles") {
                    api.itemsApi
                        .getItems(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            genres = profile.topGenres.take(GENRE_FILTERS),
                            filters = listOf(ItemFilter.IS_UNPLAYED),
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = RECOMMENDATION_FIELDS,
                            limit = GENRE_LIMIT,
                        ).content.items
                        .mapNotNull { dto ->
                            dto.toCandidate(null)?.also { dtosById[dto.id] = dto }
                        }
                }.orEmpty()

            val byId = LinkedHashMap<UUID, ForYouCandidate>()
            similarCandidates.forEach { candidate -> byId.mergeCandidate(candidate) }
            genreCandidates.forEach { candidate -> byId.mergeCandidate(candidate) }

            val candidates =
                byId.values.filter { candidate ->
                    candidate.id !in seedIds &&
                        candidate.name.isNotBlank() &&
                        (candidate.similarPosition != null || candidate.genres.isNotEmpty())
                }
            val ranked = ForYouRanker.rank(candidates, profile.affinity, limit)
            if (ranked.size < ForYouRanker.MIN_ITEMS) return null

            val items = ranked.mapNotNull { candidate -> dtosById[candidate.id]?.let { BaseItem(it, true) } }
            if (items.size < ForYouRanker.MIN_ITEMS) return null

            val reason =
                ForYouRanker.reason(
                    seedTitle = profile.seeds.firstOrNull()?.name,
                    similarIds = similarCandidates.map { it.id }.toSet(),
                    items = ranked,
                    topGenres = profile.topGenres,
                )
            Timber.d("For You: %s titles (%s)", items.size, reason.javaClass.simpleName)
            return ForYouResult(items, reason)
        }

        private fun BaseItemDto.toCandidate(similarPosition: Int?): ForYouCandidate? {
            val kind = type ?: return null
            if (kind != BaseItemKind.MOVIE && kind != BaseItemKind.SERIES) return null
            // Already started titles belong in Continue Watching, not in a recommendation row.
            if (userData?.played == true) return null
            if ((userData?.playbackPositionTicks ?: 0L) > 0L) return null
            // A poster wall of grey placeholders is worse than a shorter row: a recommendation
            // has to look like something worth pressing.
            if (imageTags?.get(ImageType.PRIMARY) == null) return null
            return ForYouCandidate(
                id = id,
                name = name.orEmpty(),
                genres = genres.orEmpty(),
                type = kind,
                similarPosition = similarPosition,
                communityRating = communityRating?.toDouble(),
            )
        }

        private fun MutableMap<UUID, ForYouCandidate>.mergeCandidate(candidate: ForYouCandidate) {
            val existing = this[candidate.id]
            if (existing == null) {
                this[candidate.id] = candidate
            } else {
                val bestPosition =
                    listOfNotNull(existing.similarPosition, candidate.similarPosition).minOrNull()
                this[candidate.id] = existing.copy(similarPosition = bestPosition)
            }
        }

        /** Runs an enrichment request, keeping a server hiccup from hiding the whole row. */
        private suspend fun <T> quiet(
            what: String,
            block: suspend () -> T,
        ): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Only the class: an SDK exception can carry a request URL.
                Timber.w("For You could not load %s: %s", what, ex.javaClass.simpleName)
                null
            }

        companion object {
            /** Twelve posters is one comfortable screenful of a home row. */
            const val MAX_ITEMS = 12

            /** Similar-title lookups are per seed; two is enough variety for three requests. */
            private const val SIMILAR_SEEDS = 2

            private const val SIMILAR_LIMIT = 30

            private const val GENRE_FILTERS = 2

            private const val GENRE_LIMIT = 40

            /**
             * The same shape as the home rows' cards plus genres, so the shelf needs no second
             * request or special case to draw its posters.
             */
            private val RECOMMENDATION_FIELDS =
                listOf(
                    ItemFields.GENRES,
                    ItemFields.OVERVIEW,
                    ItemFields.SORT_NAME,
                    ItemFields.MEDIA_SOURCE_COUNT,
                    ItemFields.CAN_DELETE,
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                )
        }
    }
