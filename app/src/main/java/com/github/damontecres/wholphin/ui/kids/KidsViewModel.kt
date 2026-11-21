package com.github.damontecres.wholphin.ui.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * The kid-friendly half of the library, shaped like the PBS KIDS video app.
 *
 * Every show on the page is a real Jellyfin series and every hero and episode card is a real
 * episode; the official PBS KIDS artwork in [KidsBrand] is only a skin for the shows the viewer
 * already owns. A library the viewer named "Kids" (or "Children") is the first source, because a
 * deliberately separated library is a stronger statement than any rating. Explicit kid ratings
 * are the fallback for a server that keeps children's titles mixed into the main libraries.
 *
 * The show wall is not capped at one screen: series are paged until the server runs out of them,
 * so a large library still shows every show. Episodes are fetched per series rather than by
 * rating because a series may be rated TV-Y while its episodes carry no rating at all (common in
 * older libraries), and those episodes must still be playable from here.
 */
@HiltViewModel
class KidsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(KidsState())
        val state: StateFlow<KidsState> = _state.asStateFlow()

        private var started = false

        fun init() {
            if (started) return
            started = true
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _state.update { it.copy(loading = LoadingState.Loading) }
                try {
                    val loaded = withContext(WholphinDispatchers.IO) { load() }
                    _state.value = loaded
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to load the Kids page")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        private suspend fun load(): KidsState {
            val userId =
                serverRepository.currentUser?.id
                    ?: return KidsState(loading = LoadingState.Error("Not signed in"))

            val views = api.userViewsApi.getUserViews(userId = userId).content.items
            val liveTv =
                views
                    .firstOrNull { it.collectionType == CollectionType.LIVETV }
                    ?.let { BaseItem(it) }
            val kidLibraries =
                views.filter { view ->
                    view.collectionType != CollectionType.LIVETV && isKidsLibrary(view.name)
                }

            // A library that is already only for children needs no rating filter.
            var shows =
                if (kidLibraries.isNotEmpty()) {
                    fetchSeries(userId, parentIds = kidLibraries.map { it.id }, officialRatings = null)
                } else {
                    emptyList()
                }

            // No children's library (or one that holds no series): ask for explicitly kid-rated
            // titles. A rating filter is used rather than Jellyfin's `isKids` flag because an
            // older server that ignores the flag would return the whole library - including
            // adult titles - and this page must never do that.
            if (shows.isEmpty()) {
                shows = fetchSeries(userId, parentIds = null, officialRatings = KID_RATINGS)
            }
            shows = shows.distinctBy { it.id }

            // One recent episode per show gives the hero a stable, recognisable face for every
            // series; the rest fill the "Watch now" rail. A failed episode lookup for one show
            // must not take the whole page down, so each series is fetched independently.
            val episodesByShow =
                shows
                    .take(MAX_SHOWS_WITH_EPISODES)
                    .map { show ->
                        show to
                            runCatching { fetchEpisodes(userId, seriesId = show.id) }
                                .onFailure { Timber.w(it, "Failed to load episodes for ${show.name}") }
                                .getOrDefault(emptyList())
                    }
            val episodes = episodesByShow.flatMap { it.second }.distinctBy { it.id }

            // The hero rotates through real episodes. A library with only series gets its covers
            // instead, and clicking one opens the series rather than pretending there is a video.
            val featured =
                episodesByShow
                    .mapNotNull { it.second.firstOrNull() }
                    .shuffled()
                    .take(HERO_COUNT)
                    .ifEmpty { shows.shuffled().take(HERO_COUNT) }
            val featuredIds = featured.mapTo(mutableSetOf()) { it.id }
            val watchNow = episodes.filterNot { it.id in featuredIds }.take(WATCH_COUNT)

            return KidsState(
                loading = LoadingState.Success,
                shows = shows,
                featured = featured,
                watchNow = watchNow,
                liveTv = liveTv,
            )
        }

        /**
         * Every series the server will return, paged. The limit is a guard against a huge library
         * turning one page opening into hundreds of requests, not a display cap.
         */
        private suspend fun fetchSeries(
            userId: UUID,
            parentIds: List<UUID>?,
            officialRatings: List<String>?,
        ): List<BaseItem> {
            val parents: List<UUID?> = parentIds?.ifEmpty { listOf(null) } ?: listOf(null)
            val result = mutableListOf<BaseItem>()
            for (parentId in parents) {
                var startIndex = 0
                while (true) {
                    val page =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    parentId = parentId,
                                    recursive = true,
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                    officialRatings = officialRatings,
                                    sortBy = listOf(ItemSortBy.SORT_NAME),
                                    sortOrder = listOf(SortOrder.ASCENDING),
                                    fields = KidsItemFields,
                                    imageTypeLimit = 1,
                                    enableImageTypes = KIDS_IMAGE_TYPES,
                                    enableUserData = true,
                                    enableImages = true,
                                    startIndex = startIndex,
                                    limit = SERIES_PAGE_SIZE,
                                ),
                            ).content
                    val items = page.items
                    result += items.map { BaseItem(it) }
                    startIndex += items.size
                    if (items.isEmpty() ||
                        startIndex >= page.totalRecordCount ||
                        result.size >= MAX_SERIES
                    ) {
                        break
                    }
                }
            }
            return result
        }

        private suspend fun fetchEpisodes(
            userId: UUID,
            seriesId: UUID,
        ): List<BaseItem> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        parentId = seriesId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = KidsItemFields,
                        imageTypeLimit = 1,
                        enableImageTypes = KIDS_IMAGE_TYPES,
                        enableUserData = true,
                        enableImages = true,
                        limit = EPISODES_PER_SHOW,
                    ),
                ).content.items
                .map { BaseItem(it, useSeriesForPrimary = true) }

        private fun isKidsLibrary(name: String?): Boolean {
            val lower = name?.lowercase() ?: return false
            return "kid" in lower || "child" in lower
        }

        private companion object {
            val KidsItemFields = HomeItemFields + ItemFields.GENRES
            val KIDS_IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP)

            const val SERIES_PAGE_SIZE = 100
            const val MAX_SERIES = 500
            const val MAX_SHOWS_WITH_EPISODES = 60
            const val EPISODES_PER_SHOW = 8
            const val HERO_COUNT = 8
            const val WATCH_COUNT = 20

            /**
             * The ratings a children's shelf may hold. PG and TV-PG are left out on purpose:
             * parents can still include those titles by putting them in a library named Kids or
             * Children, where the library itself is the statement of intent.
             */
            val KID_RATINGS = listOf("TV-Y", "TV-Y7", "TV-Y7-FV", "G", "TV-G")
        }
    }

data class KidsState(
    val loading: LoadingState = LoadingState.Pending,
    val shows: List<BaseItem> = emptyList(),
    val featured: List<BaseItem> = emptyList(),
    val watchNow: List<BaseItem> = emptyList(),
    val liveTv: BaseItem? = null,
) {
    val isEmpty: Boolean get() = liveTv == null && shows.isEmpty() && featured.isEmpty() && watchNow.isEmpty()
}
