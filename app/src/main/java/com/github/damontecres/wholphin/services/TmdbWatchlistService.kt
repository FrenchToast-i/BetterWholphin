package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The discovery titles this viewer saved to watch later.
 *
 * Discovery had a hole in it: a poster found on the trending shelf or in a recommendation rail
 * was a dialog you closed, and then it was gone. The library rows remember what you own; nothing
 * remembered what you *wanted*. This is that memory. It is deliberately TMDB-only — titles the
 * library already has belong in Continue Watching or Next Up, where they are playable — so the
 * list is the one place that holds the things you have not asked anyone for yet.
 *
 * Per user through [KeyValueService], like recent searches: two profiles on one television should
 * not share a wish list. Writes run on the application's IO scope so closing the dialog cannot
 * cancel the save that the close was caused by.
 *
 * A saved title keeps its own TMDB fields, not just an id: the list has to draw posters and
 * captions with no network at all, and a title that later leaves the trending page should still
 * read correctly in the viewer's list.
 */
@Singleton
class TmdbWatchlistService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val keyValueService: KeyValueService,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        val items: StateFlow<List<TmdbItem>> =
            serverRepository.currentUserFlow
                .flatMapLatest { user ->
                    val userId = user?.id
                    if (userId == null) {
                        flowOf(emptyList())
                    } else {
                        keyValueService.get(userId, KEY, emptyList<TmdbItem>())
                    }
                }.stateIn(scope, SharingStarted.Eagerly, emptyList())

        // Two taps on the same dialog can overlap on a slow remote. The stored list is the source
        // of truth inside the lock, so the second tap sees the first one's save instead of
        // overwriting it with a stale snapshot.
        private val mutex = Mutex()

        /** Add [item] to the front of the list, or remove it if it is already saved. */
        fun toggle(item: TmdbItem) {
            val userId = serverRepository.currentUser?.id ?: return
            scope.launch {
                try {
                    mutex.withLock {
                        val current = keyValueService.get(userId, KEY, emptyList<TmdbItem>()).first()
                        keyValueService.save(userId, KEY, toggleWatchlist(current, item))
                    }
                } catch (ex: Exception) {
                    // Only the class: this is a local write, but the same rule as every other
                    // discovery log keeps titles out of logs.
                    Timber.e("Could not save the watchlist: %s", ex.javaClass.simpleName)
                }
            }
        }

        private companion object {
            const val KEY = "tmdbWatchlist"
        }
    }

/**
 * Whether [list] already holds [item], by TMDB id *and* media type.
 *
 * Ids are only unique within a type: a movie and a show can share one, so matching on the id
 * alone would mark an unowned show as saved because its film namesake is.
 */
internal fun List<TmdbItem>.containsWatchlist(item: TmdbItem): Boolean = any { it.watchlistKey() == item.watchlistKey() }

private fun TmdbItem.watchlistKey(): String = "${if (isMovie) "movie" else "tv"}_$id"

/**
 * Newest first, one entry per title, capped so the row stays a row.
 *
 * The cap drops from the *end* — the title saved longest ago — because a list that silently
 * forgets the thing you just added is worse than one that forgets the oldest enthusiasm.
 */
internal fun toggleWatchlist(
    current: List<TmdbItem>,
    item: TmdbItem,
    max: Int = MAX_WATCHLIST,
): List<TmdbItem> {
    val key = item.watchlistKey()
    val without = current.filterNot { it.watchlistKey() == key }
    if (without.size < current.size) return without
    return (listOf(item) + without).take(max)
}

/** Three poster rows' worth. Beyond that the list wants a page, not a home row. */
internal const val MAX_WATCHLIST = 24
