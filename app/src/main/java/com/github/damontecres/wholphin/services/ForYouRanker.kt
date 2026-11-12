package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/** Why the For You row is showing what it shows. */
sealed interface ForYouReason {
    /** The rail mostly follows one show or film the viewer just watched. */
    data class BecauseYouWatched(
        val title: String,
    ) : ForYouReason

    /** The rail follows the genres the viewer returns to, with no single clear seed. */
    data class BecauseYouWatch(
        val genres: List<String>,
    ) : ForYouReason
}

/**
 * One thing the library could recommend.
 *
 * Deliberately free of Jellyfin DTOs so the ranking rules can be unit tested without a server.
 */
internal data class ForYouCandidate(
    val id: UUID,
    val name: String,
    val genres: List<String>,
    val type: BaseItemKind,
    /** Position in a seed's "similar" response, when the candidate came from one. */
    val similarPosition: Int? = null,
    val communityRating: Double? = null,
)

/**
 * The rules behind the For You row.
 *
 * A recommendation row only earns its place on the home page if every card is something the
 * viewer might actually start. So the ranker is opinionated:
 *
 * - Taste is the gate, not a bonus. A candidate with no genre the viewer watches and no similar
 *   link to something they just finished is dropped, however highly rated it is; otherwise the
 *   row becomes "popular in your library" wearing a personal heading.
 * - Similarity is a strong prior. Jellyfin's similar titles are metadata-driven neighbours of a
 *   show the viewer just watched, which is a far more specific signal than "also science fiction".
 * - Ratings only break ties. A 9.2 the viewer would never start does not deserve to outrank a 7.8
 *   in the genre they watch every night.
 */
internal object ForYouRanker {
    /** A row shorter than this reads as a failed query rather than a recommendation. */
    const val MIN_ITEMS = 5

    /** Weight of coming first in a seed's similar list. */
    private const val SIMILAR_BOOST = 0.8

    /** How fast similarity decays with position in that list. */
    private const val SIMILAR_DECAY = 4.0

    /** Ratings matter, but only just. */
    private const val RATING_WEIGHT = 0.15

    fun score(
        candidate: ForYouCandidate,
        affinity: Map<String, Double>,
    ): Double {
        val taste = candidate.genres.sumOf { affinity[it.lowercase()] ?: 0.0 }
        val similar =
            candidate.similarPosition?.let { position ->
                SIMILAR_BOOST / (1.0 + position / SIMILAR_DECAY)
            } ?: 0.0
        val rating = (candidate.communityRating ?: 0.0).coerceIn(0.0, 10.0) / 10.0 * RATING_WEIGHT
        return taste + similar + rating
    }

    /**
     * Ranked, deduplicated candidates. Anything with no taste or similarity signal is removed, and
     * the result is capped at [limit].
     */
    fun rank(
        candidates: List<ForYouCandidate>,
        affinity: Map<String, Double>,
        limit: Int,
    ): List<ForYouCandidate> =
        candidates
            .distinctBy { it.id }
            .filter { candidate ->
                candidate.genres.any { (affinity[it.lowercase()] ?: 0.0) > 0.0 } ||
                    candidate.similarPosition != null
            }.sortedWith(
                compareByDescending<ForYouCandidate> { score(it, affinity) }
                    .thenBy { it.name.lowercase() },
            ).take(limit)

    /**
     * The heading's second line.
     *
     * The seed wins when most of the row really did come from its similar list. When the genre
     * query supplied the row, saying "Because you watched Silo" would overclaim a connection the
     * cards do not have.
     */
    fun reason(
        seedTitle: String?,
        similarIds: Set<UUID>,
        items: List<ForYouCandidate>,
        topGenres: List<String>,
    ): ForYouReason {
        val similarCount = items.count { it.id in similarIds }
        return if (seedTitle != null && similarCount > 0 && similarCount * 2 >= items.size) {
            ForYouReason.BecauseYouWatched(seedTitle)
        } else {
            ForYouReason.BecauseYouWatch(topGenres.take(2))
        }
    }
}
