package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queries this viewer has actually run, newest first.
 *
 * Search opens on an empty page, so for anyone who has used it before the most useful thing on
 * that page is what they looked for last time. Kept per user — two profiles on one television
 * should not read each other's history — and stored through [KeyValueService] like the excluded
 * search types, because it belongs to the viewer rather than to the device.
 *
 * The writes run on the application's IO scope rather than the page's: leaving Search is exactly
 * when a query is recorded (that is when the viewer has committed to it), and a ViewModel job
 * would be cancelled by the same navigation that triggered the write.
 */
@Singleton
class RecentSearchService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val keyValueService: KeyValueService,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        val searches: StateFlow<List<String>> =
            serverRepository.currentUserFlow
                .flatMapLatest { user ->
                    val userId = user?.id
                    if (userId == null) {
                        flowOf(emptyList())
                    } else {
                        keyValueService.get(userId, KEY, emptyList<String>())
                    }
                }.stateIn(scope, SharingStarted.Eagerly, emptyList())

        /**
         * Remember a query the viewer actually meant.
         *
         * Called when they run the search explicitly and when they leave the page, because this
         * page searches as they type: most viewers never press search, so a history recorded only
         * on that key would stay empty forever. A single character is a keystroke rather than a
         * search, and a repeat moves to the front instead of appearing twice.
         */
        fun record(query: String) {
            val trimmed = query.trim()
            if (trimmed.length < MIN_RECORDED_QUERY_LENGTH) return
            val userId = serverRepository.currentUser?.id ?: return
            scope.launch {
                try {
                    keyValueService.save(userId, KEY, mergeRecentSearches(searches.value, trimmed))
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording recent search")
                }
            }
        }

        fun clear() {
            val userId = serverRepository.currentUser?.id ?: return
            scope.launch {
                try {
                    keyValueService.save(userId, KEY, emptyList<String>())
                } catch (ex: Exception) {
                    Timber.e(ex, "Error clearing recent searches")
                }
            }
        }

        private companion object {
            const val KEY = "recentSearches"
            const val MIN_RECORDED_QUERY_LENGTH = 2
        }
    }

/**
 * Newest first, one entry per query regardless of case, capped so history stays a shortcut.
 *
 * A repeat moves the existing entry to the front but keeps the spelling it was first stored
 * with: retyping the same title in lowercase should not make the chip change case under the
 * viewer's eyes. Six entries fit the empty search page's two chip rows without pushing the
 * discovery shelf away.
 */
internal fun mergeRecentSearches(
    current: List<String>,
    query: String,
    max: Int = MAX_RECENT_SEARCHES,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return current.take(max)
    val canonical = current.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    return (listOf(canonical) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(max)
}

internal const val MAX_RECENT_SEARCHES = 6
