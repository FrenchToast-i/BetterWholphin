package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the viewer has been watching lately, in the shape a recommender can use.
 *
 * The app has two surfaces that answer "what should I watch?": the bar's Play something button and
 * the home page's For You row. They must agree, or the same evening produces two different answers
 * from the same app — so the history query and the genre weighting live here, once, and both read
 * this profile. [SurprisePicker] remains the pure maths underneath it.
 *
 * [seeds] are the distinct movies and series in the history, newest first. Episodes are mapped to
 * their series because recommending "the next episode of a show you finished" is a job for the
 * watch rows, while a seed is the thing whose company a viewer is in: a show, not a file.
 */
@Singleton
class TasteProfileService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        data class Seed(
            val id: UUID,
            val name: String,
            val isMovie: Boolean,
        )

        data class TasteProfile(
            /** Genre weights in `0..1`, most recent viewing counting most. Keys are lowercase. */
            val affinity: Map<String, Double>,
            /** The strongest genres, spelled the way the library spells them. */
            val topGenres: List<String>,
            /** The newest distinct movies/series in the history, newest first. */
            val seeds: List<Seed>,
        )

        /** Never null: a viewer with no history simply has an empty profile. */
        suspend fun profile(userId: UUID): TasteProfile {
            val history =
                api.itemsApi
                    .getItems(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        filters = listOf(ItemFilter.IS_PLAYED),
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = listOf(ItemFields.GENRES),
                        limit = HISTORY_LIMIT,
                    ).content.items

            val affinity = SurprisePicker.genreAffinity(history.map { it.genres.orEmpty() })

            // The affinity map is lowercase so matching is case-insensitive, but a heading has to
            // read "Science Fiction", not "science fiction".
            val spelling = mutableMapOf<String, String>()
            history.forEach { item ->
                item.genres.orEmpty().forEach { genre ->
                    spelling.putIfAbsent(genre.lowercase(), genre)
                }
            }
            val topGenres =
                affinity.entries
                    .sortedByDescending { it.value }
                    .mapNotNull { spelling[it.key] }
                    .take(MAX_TOP_GENRES)

            val seeds =
                history
                    .mapNotNull { item ->
                        when (item.type) {
                            BaseItemKind.MOVIE ->
                                item.name?.takeIf { it.isNotBlank() }?.let {
                                    Seed(item.id, it, isMovie = true)
                                }
                            BaseItemKind.EPISODE ->
                                item.seriesId?.let { seriesId ->
                                    item.seriesName
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { Seed(seriesId, it, isMovie = false) }
                                }
                            else -> null
                        }
                    }.distinctBy { it.id }
                    .take(MAX_SEEDS)

            return TasteProfile(affinity, topGenres, seeds)
        }

        companion object {
            /** How much history the taste model reads. */
            const val HISTORY_LIMIT = 120

            /** Enough genres for a readable reason line and a useful query. */
            private const val MAX_TOP_GENRES = 4

            /** Similar-title lookups are per seed, so this stays small. */
            private const val MAX_SEEDS = 3
        }
    }
