package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbResult
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.TmdbAttribution
import com.github.damontecres.wholphin.ui.components.TmdbDetailDialogHost
import com.github.damontecres.wholphin.ui.components.TmdbPosterCard
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Seeds a discovery rail from whatever the viewer is part-way through.
 *
 * The home screen is the one place where "what should I watch next?" is already being answered by
 * the library, so the rail answers it from the other side: TMDB's recommendations for the last
 * unfinished title. It follows the continue-watching row, which means it reorders itself as the
 * viewer moves from one show to another without any setting to maintain. When the viewer
 * finished everything and the resume row is empty, the top of the Next Up row seeds it
 * instead, so the rail does not vanish precisely when the viewer needs something to watch.
 *
 * Everything here is best-effort. No key, nothing in progress, an item without a TMDB id, a
 * request that fails: the state stays [State.Hidden] and the home page is exactly as it was.
 * Discovery is not allowed to turn into an error row between two library rows.
 */
@HiltViewModel
class TmdbHomeViewModel
    @Inject
    constructor(
        private val tmdb: TmdbService,
        private val latestNextUpService: LatestNextUpService,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
        private val api: ApiClient,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        sealed interface State {
            /** Draw nothing: no key, nothing in progress, or discovery unavailable. */
            data object Hidden : State

            data class Ready(
                /**
                 * What the viewer is part-way through: the rail's heading and, while the rail
                 * has focus, the home hero. A real [BaseItem] so the hero can offer Play and
                 * its progress survives the jump from the watch row to the rail.
                 */
                val seed: BaseItem,
                val items: List<TmdbItem>,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Hidden)
        val state = _state.asStateFlow()

        private var job: Job? = null

        /** The focused poster's artwork, so the home backdrop follows the rail into discovery. */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch {
                backdropService.submit("tmdb_${item.id}", url)
            }
        }

        fun refresh() {
            job?.cancel()
            job =
                viewModelScope.launch {
                    _state.value = State.Hidden
                    try {
                        _state.value = load()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // Only the class: a request exception can carry a URL, and a v3 key
                        // travels in the URL.
                        Timber.w("TMDB home rail unavailable: %s", ex.javaClass.simpleName)
                        _state.value = State.Hidden
                    }
                }
        }

        private suspend fun load(): State =
            withContext(WholphinDispatchers.IO) {
                if (!tmdb.hasKey()) return@withContext State.Hidden

                // On a cold start the home page can be composed before the signed-in user has
                // been restored. Waiting briefly instead of bailing out is the difference between
                // the rail appearing on first launch and only on the second visit.
                val userId =
                    withTimeoutOrNull(USER_TIMEOUT_MS) {
                        serverRepository.currentUserFlow.filterNotNull().first()
                    }?.id ?: return@withContext State.Hidden

                // Most recently touched unfinished item. The resume endpoint is not ordered by
                // last-played — the Continue Watching row sorts it itself — so ask for a page and
                // pick the newest by timestamp rather than trusting row 0.
                val resume = latestNextUpService.getResume(userId, limit = SEED_LIMIT, includeEpisodes = true)
                // A viewer who finished everything has an empty resume but still has a next
                // episode waiting: the top of the Next Up row is equally good company, and it
                // already excludes series the viewer removed.
                val nextUp =
                    if (resume.isEmpty()) {
                        val homePrefs = preferences.data.first().homePagePreferences
                        latestNextUpService.getNextUp(
                            userId,
                            NEXT_UP_SEED_LIMIT,
                            homePrefs.enableRewatchingNextUp,
                            false,
                            homePrefs.maxDaysNextUp,
                        )
                    } else {
                        emptyList()
                    }
                val seedItem = selectSeed(resume, nextUp) ?: return@withContext State.Hidden
                val targetId = seedItem.data.seriesId ?: seedItem.id

                // Re-fetch the show/movie itself: the resume row's slim fields do not ask for
                // provider ids, and an episode's id would seed the wrong recommendation list.
                val seed =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                ids = listOf(targetId),
                                fields = listOf(ItemFields.PROVIDER_IDS),
                                limit = 1,
                            ),
                        ).content.items
                        .firstOrNull()
                        ?: return@withContext State.Hidden
                val isMovie = seed.type == BaseItemKind.MOVIE
                if (!isMovie && seed.type != BaseItemKind.SERIES) return@withContext State.Hidden
                val title =
                    seed.name?.takeIf { it.isNotBlank() }
                        ?: return@withContext State.Hidden

                // A Jellyfin library scraped by Jellyfin itself carries the provider id; a
                // hand-built one may not, so fall back to a title search rather than skipping
                // the rail for that one show.
                val tmdbId =
                    seed.providerIds
                        ?.get("Tmdb")
                        ?.toIntOrNull()
                        ?: (tmdb.findId(title, seed.productionYear, isMovie) as? TmdbResult.Success<Int?>)?.value
                        ?: return@withContext State.Hidden

                // Every failure is silent here: the Search shelf is where discovery reports
                // whether TMDB is usable.
                val recommendations = tmdb.recommendations(tmdbId, isMovie)
                if (recommendations !is TmdbResult.Success) return@withContext State.Hidden
                val items =
                    recommendations.value
                        // Recommendations often include the seed itself (or a special of it).
                        .filter { it.id != tmdbId && it.displayTitle != title }
                        .take(MAX_ITEMS)
                if (items.isEmpty()) return@withContext State.Hidden

                Timber.d("TMDB home rail: %s recommendations for %s", items.size, title)
                State.Ready(seed = seedItem, items = items)
            }

        companion object {
            private const val MAX_ITEMS = 12
            private const val SEED_LIMIT = 20
            private const val NEXT_UP_SEED_LIMIT = 10
            private const val USER_TIMEOUT_MS = 8_000L

            /**
             * Picks what the rail follows: the most recently touched resume item, or the top of
             * Next Up when the viewer finished everything. Pure so the fallback order is pinned
             * by a test rather than by re-reading the ViewModel.
             */
            internal fun selectSeed(
                resume: List<BaseItem>,
                nextUp: List<BaseItem>,
            ): BaseItem? {
                if (resume.isNotEmpty()) {
                    return resume.maxByOrNull { it.data.userData?.lastPlayedDate ?: LocalDateTime.MIN }
                }
                return nextUp.firstOrNull()
            }
        }
    }

/**
 * "Because you're watching …" — the discovery rail on the home page.
 *
 * Cards open the same summary dialog as the Search shelf, with a shortcut into the library's own
 * search, so a recommendation is one press away from becoming playable.
 */
@Composable
fun TmdbHomeShelf(
    state: TmdbHomeViewModel.State,
    onFindInLibrary: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Focused poster, so the page backdrop follows the rail instead of the row above it. */
    onFocusItem: ((TmdbItem) -> Unit)? = null,
    /**
     * Reports the seed while the rail holds focus, so the home hero describes the show being
     * recommended from. TMDB posters are not library items, so they cannot report themselves.
     */
    onFocusSeed: ((BaseItem?) -> Unit)? = null,
) {
    val ready = state as? TmdbHomeViewModel.State.Ready ?: return
    var details by remember { mutableStateOf<TmdbItem?>(null) }
    // For an episode BaseItem.title is the series name, which is what the rail follows.
    val seedTitle = ready.seed.title ?: ready.seed.name.orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        ItemRow(
            title = stringResource(R.string.tmdb_because_watching, seedTitle),
            items = ready.items,
            onClickItem = { _, item -> details = item },
            // Long-press matches click: a dead long-press on a TV remote reads as broken,
            // and the dialog is the only action this rail has.
            onLongClickItem = { _, item -> details = item },
            horizontalPadding = 16.dp,
            showViewMore = false,
            // TMDB's terms require the notice wherever its data is shown. It sits under the
            // heading, not after the cards: as a footer it is the last thing in the shelf, and
            // the next row scrolling into view left that fragment clipped and floating under
            // the hero with nothing to attach to.
            // The credit hugs the heading: the home page scrolls a row's heading out of view
            // when focus moves into its cards, and a credit with a 14dp trail would be the
            // first thing clipped. Compact keeps heading and notice inside the same margin.
            subtitle = { TmdbAttribution(Modifier.padding(top = 2.dp, bottom = 6.dp)) },
            cardContent = { _, item, cardModifier, onClick, onLongClick ->
                if (item != null) {
                    TmdbPosterCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        modifier = cardModifier,
                        // 2:3 at the home rows' 172dp card height, so this shelf sits on the
                        // same baseline as every library row above and below it.
                        width = 115.dp,
                        // Captions belong on the discovery surfaces with vertical room; a home
                        // row is posters, like every library row beside it.
                        showCaption = false,
                        onFocus = {
                            onFocusItem?.invoke(item)
                            onFocusSeed?.invoke(ready.seed)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    details?.let { item ->
        TmdbDetailDialogHost(
            item = item,
            onDismissRequest = { details = null },
            onFindInLibrary = onFindInLibrary,
        )
    }
}
