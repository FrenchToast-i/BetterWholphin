package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.timeRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
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
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The shape of a Tonight shelf, resolved to copy by the page. */
enum class TonightShelfKind {
    /** Everything that can be started now and finished before the deadline. */
    FITS_TONIGHT,

    /** Highly rated and still unwatched. */
    HIDDEN_GEMS,

    /** Metadata neighbours of the last thing the viewer watched. */
    BECAUSE_YOU_WATCHED,

    /** Good titles outside the genres the viewer usually picks. */
    DIFFERENT_LANE,

    /** Something familiar, for a night that should not ask anything of the viewer. */
    COMFORT_REWATCH,

    /** Series one episode from finished: the shortest path to done. */
    FINISH_LINE,
}

data class TonightShelf(
    val kind: TonightShelfKind,
    val items: List<BaseItem>,
    /** For [TonightShelfKind.BECAUSE_YOU_WATCHED]: the show or film that seeded it. */
    val seedTitle: String? = null,
)

/** The one title the page leads with. */
data class TonightPick(
    val item: BaseItem,
    val reason: TonightReason,
    /** Remaining viewing time from here. */
    val minutes: Long,
    val fits: Boolean,
)

data class TonightPlan(
    val window: TonightWindow,
    val pick: TonightPick?,
    val shelves: List<TonightShelf>,
) {
    val isEmpty: Boolean get() = pick == null && shelves.isEmpty()
}

/**
 * Everything fetched for a plan, before dismissals are applied.
 *
 * Held by the page's ViewModel so "Not tonight" can reroll instantly instead of re-querying the
 * server for a pick the viewer just refused.
 */
data class TonightData(
    val window: TonightWindow,
    val candidates: List<TonightCandidate>,
    val items: Map<UUID, BaseItem>,
    val shelves: List<TonightShelf>,
    /** Kept with the data so a reroll ranks exactly the way the load did. */
    val affinity: Map<String, Double>,
)

/**
 * Builds the nightly plan.
 *
 * The queries are the library's own material — resume, next up, unwatched and rewatchable films,
 * and the taste profile the Play something button already uses — so the page works on any server,
 * with no key and no external service. Each source is best-effort: a failed similar lookup costs
 * one shelf, not the evening.
 */
@Singleton
class TonightService
    @Inject
    constructor(
        private val api: ApiClient,
        private val latestNextUpService: LatestNextUpService,
        private val tasteProfileService: TasteProfileService,
        private val keyValueService: KeyValueService,
    ) {
        suspend fun load(
            userId: UUID,
            bedtimeMinutes: Int,
            now: LocalDateTime = LocalDateTime.now(),
        ): TonightData =
            coroutineScope {
                val window = TonightPlanner.window(now, bedtimeMinutes)
                val resumeDeferred = async { quiet("resume") { latestNextUpService.getResume(userId, RESUME_LIMIT, true, false) } }
                val nextUpDeferred = async { quiet("next up") { latestNextUpService.getNextUp(userId, NEXT_UP_LIMIT, false, false, 0, false) } }
                val unplayedMoviesDeferred = async { quiet("unwatched films") { moviePage(userId, ItemFilter.IS_UNPLAYED) } }
                val playedMoviesDeferred = async { quiet("rewatchable films") { moviePage(userId, ItemFilter.IS_PLAYED) } }
                val profileDeferred = async { quiet("taste") { tasteProfileService.profile(userId) } }

                val resume = resumeDeferred.await().orEmpty()
                val nextUp = nextUpDeferred.await().orEmpty()
                val unplayedResult = unplayedMoviesDeferred.await()
                val playedResult = playedMoviesDeferred.await()
                val profile = profileDeferred.await()
                // An empty library is a valid answer (the page invites the viewer to watch
                // something first); two failed film queries are not, and should offer a retry.
                if (unplayedResult == null && playedResult == null) {
                    throw IllegalStateException("Tonight could not reach the film library")
                }
                val unplayedMovies = unplayedResult.orEmpty()
                val playedMovies = playedResult.orEmpty()

                val items = LinkedHashMap<UUID, BaseItem>()
                (resume + nextUp + unplayedMovies + playedMovies).forEach { item -> items.putIfAbsent(item.id, item) }

                val candidates =
                    (resume + nextUp + unplayedMovies)
                        .mapNotNull { item -> item.toCandidate(now) }
                        .distinctBy { it.id }

                // Which of the next-up series are one episode from finished. One request for the
                // series' own watch counts, rather than a lookup per episode.
                val seriesWithOneLeft =
                    quiet("finish line") {
                        val seriesIds = nextUp.mapNotNull { it.data.seriesId }.distinct()
                        if (seriesIds.isEmpty()) {
                            emptySet<UUID>()
                        } else {
                            api.itemsApi
                                .getItems(
                                    userId = userId,
                                    ids = seriesIds,
                                    fields = TONIGHT_FIELDS,
                                ).content.items
                                .filter { it.userData?.unplayedItemCount == 1 }
                                .mapTo(mutableSetOf()) { it.id }
                        }
                    }.orEmpty()
                val finishLine =
                    TonightPlanner
                        .finishLine(
                            nextUp.mapNotNull { it.toCandidate(now) },
                            seriesWithOneLeft,
                            SHELF_SIZE,
                        ).mapNotNull { items[it.id] }

                val seed = resume.firstOrNull() ?: nextUp.firstOrNull()
                val seedTitle = seed?.let(::seedTitle)
                val similar =
                    seed?.let { item ->
                        quiet("similar titles") {
                            api.libraryApi
                                .getSimilarItems(
                                    GetSimilarItemsRequest(
                                        userId = userId,
                                        itemId = item.id,
                                        fields = TONIGHT_FIELDS,
                                        limit = SIMILAR_LIMIT,
                                    ),
                                ).content.items
                                .map { BaseItem(it, false) }
                                .filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.SERIES }
                                .filter { !it.played }
                                .filter { it.id != item.id }
                                .distinctBy { it.id }
                                .also { list -> list.forEach { items.putIfAbsent(it.id, it) } }
                        }
                    }.orEmpty()

                val affinity = profile?.affinity.orEmpty()
                val topGenres = profile?.topGenres.orEmpty().map { it.lowercase() }.toSet()

                val fits =
                    TonightPlanner
                        .rank(candidates, window, affinity)
                        .filter { (it.minutes ?: Long.MAX_VALUE) <= window.minutes }
                        .mapNotNull { items[it.id] }
                val fitIds = fits.map { it.id }.toSet()

                val hiddenGems =
                    unplayedMovies
                        .asSequence()
                        .filter { (it.data.communityRating ?: 0f) >= HIDDEN_GEM_RATING }
                        .filter { it.id !in fitIds }
                        .sortedByDescending { it.data.communityRating }
                        .take(SHELF_SIZE)
                        .toList()

                // A theme night should be a real departure: the genre has to sit outside every
                // genre the viewer has been choosing, not merely be a different mix of the same
                // three.
                val hiddenGemIds = hiddenGems.mapTo(mutableSetOf()) { it.id }
                val differentLane =
                    unplayedMovies
                        .asSequence()
                        .filter { (it.data.communityRating ?: 0f) >= DIFFERENT_LANE_RATING }
                        .filter { it.data.genres.orEmpty().isNotEmpty() }
                        .filter { movie ->
                            topGenres.isEmpty() ||
                                movie.data.genres.orEmpty().none { it.lowercase() in topGenres }
                        }
                        .filter { it.id !in fitIds && it.id !in hiddenGemIds }
                        .sortedByDescending { it.data.communityRating }
                        .take(SHELF_SIZE)
                        .toList()

                val comfort =
                    playedMovies
                        .asSequence()
                        .filter { (it.data.communityRating ?: 0f) >= COMFORT_RATING }
                        .sortedByDescending { item -> item.daysSincePlayed(now) ?: Long.MAX_VALUE }
                        .take(SHELF_SIZE)
                        .toList()

                val shelves =
                    buildList {
                        add(TonightShelf(TonightShelfKind.FITS_TONIGHT, fits.take(SHELF_SIZE)))
                        // Right after the pick: finishing something is the strongest answer to
                        // "what can I do with the evening I have".
                        add(TonightShelf(TonightShelfKind.FINISH_LINE, finishLine))
                        add(TonightShelf(TonightShelfKind.HIDDEN_GEMS, hiddenGems))
                        add(
                            TonightShelf(
                                TonightShelfKind.BECAUSE_YOU_WATCHED,
                                similar.take(SHELF_SIZE),
                                seedTitle,
                            ),
                        )
                        add(TonightShelf(TonightShelfKind.DIFFERENT_LANE, differentLane))
                        add(TonightShelf(TonightShelfKind.COMFORT_REWATCH, comfort))
                    }.filter { it.items.size >= MIN_SHELF_SIZE }

                TonightData(window, candidates, items, shelves, affinity)
            }

        /**
         * Apply the refusals to [data] and produce the plan the page draws.
         *
         * Pure: called once on load and again on every "Not tonight", so a reroll never waits for
         * the network.
         */
        fun buildPlan(
            data: TonightData,
            dismissed: Set<UUID>,
        ): TonightPlan {
            val candidate = TonightPlanner.pick(data.candidates, data.window, data.affinity, dismissed)
            val pick =
                candidate?.let { chosen ->
                    data.items[chosen.id]?.let { item ->
                        TonightPick(
                            item = item,
                            reason = TonightPlanner.reason(chosen, data.affinity),
                            minutes = chosen.minutes ?: 0L,
                            fits = (chosen.minutes ?: Long.MAX_VALUE) <= data.window.minutes,
                        )
                    }
                }
            // A title the viewer just refused must not reappear one shelf lower; that reads as
            // the button having done nothing. The pick itself is excluded for the same reason:
            // it is the card at the top of the page, and "Done before bed" opening with the very
            // poster above it looks like the row forgot which title it was recommending.
            val pickedId = pick?.item?.id
            val shelves =
                data.shelves
                    .map { shelf ->
                        shelf.copy(
                            items =
                                shelf.items.filter {
                                    it.id !in dismissed && it.id != pickedId
                                },
                        )
                    }
                    // Shelves were sized at load; refusals can shrink one below the four that
                    // make a row look deliberate (see MIN_SHELF_SIZE).
                    .filter { it.items.size >= MIN_SHELF_SIZE }
            return TonightPlan(data.window, pick, shelves)
        }

        /** Titles the viewer has refused tonight, per user and per day. */
        suspend fun dismissedIds(userId: UUID): Set<UUID> {
            val record =
                keyValueService
                    .get<TonightDismissalRecord>(userId, DISMISSAL_KEY, TonightDismissalRecord())
                    .first()
            return record.idsOn(LocalDate.now())
        }

        suspend fun dismiss(
            userId: UUID,
            itemId: UUID,
        ) {
            val today = LocalDate.now()
            val existing =
                keyValueService
                    .get<TonightDismissalRecord>(userId, DISMISSAL_KEY, TonightDismissalRecord())
                    .first()
            val ids = existing.idsOn(today) + itemId
            keyValueService.save(userId, DISMISSAL_KEY, TonightDismissalRecord(today.toString(), ids.map { it.toString() }))
        }

        private suspend fun moviePage(
            userId: UUID,
            filter: ItemFilter,
        ): List<BaseItem> =
            api.itemsApi
                .getItems(
                    userId = userId,
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    filters = listOf(filter),
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = TONIGHT_FIELDS,
                    limit = MOVIE_POOL,
                ).content.items
                .map { BaseItem(it, false) }
                .filter { it.data.imageTags?.get(ImageType.PRIMARY) != null }

        private fun BaseItem.toCandidate(now: LocalDateTime): TonightCandidate? {
            if (type != BaseItemKind.MOVIE && type != BaseItemKind.EPISODE) return null
            // `timeRemaining` is the position-aware helper the rest of the app uses; an unstarted
            // title falls back to its full runtime, which is the same question one step earlier.
            val remaining =
                data.timeRemaining?.inWholeMinutes
                    ?: data.runTimeTicks?.ticks?.inWholeMinutes
            return TonightCandidate(
                id = id,
                title = name.orEmpty(),
                seriesName = data.seriesName,
                minutes = remaining,
                progress = data.userData?.playedPercentage ?: 0.0,
                rating = data.communityRating?.toDouble(),
                genres = data.genres.orEmpty(),
                isEpisode = type == BaseItemKind.EPISODE,
                seriesId = data.seriesId,
                lastPlayedDaysAgo = daysSincePlayed(now),
            )
        }

        private fun BaseItem.daysSincePlayed(now: LocalDateTime): Long? =
            data.userData?.lastPlayedDate?.let { played ->
                ChronoUnit.DAYS.between(played, now).coerceAtLeast(0L)
            }

        private fun seedTitle(item: BaseItem): String? =
            if (item.type == BaseItemKind.EPISODE) {
                item.data.seriesName ?: item.name
            } else {
                item.name
            }

        /** Runs an enrichment request, keeping one failed call from hiding the whole page. */
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
                Timber.w("Tonight could not load %s: %s", what, ex.javaClass.simpleName)
                null
            }

        companion object {
            private const val RESUME_LIMIT = 12
            // Wide enough that the finish-line shelf sees every series the viewer is close to
            // finishing, not just the twelve most recent.
            private const val NEXT_UP_LIMIT = 24
            private const val MOVIE_POOL = 80
            private const val SIMILAR_LIMIT = 24
            private const val SHELF_SIZE = 12
            private const val MIN_SHELF_SIZE = 4
            private const val HIDDEN_GEM_RATING = 7.5f
            private const val DIFFERENT_LANE_RATING = 7.0f
            private const val COMFORT_RATING = 6.5f
            private const val DISMISSAL_KEY = "tonightDismissed"

            private val TONIGHT_FIELDS =
                listOf(
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    ItemFields.MEDIA_SOURCE_COUNT,
                    ItemFields.CAN_DELETE,
                    ItemFields.OVERVIEW,
                )
        }
    }

/**
 * Refusals are stored with their date so that "not tonight" lasts exactly one night.
 * A record from yesterday is not a decision about today.
 */
@Serializable
data class TonightDismissalRecord(
    val date: String = "",
    val ids: List<String> = emptyList(),
) {
    fun idsOn(today: LocalDate): Set<UUID> =
        if (date == today.toString()) {
            ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        } else {
            emptySet()
        }
}
