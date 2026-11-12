package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.DestinationCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Which stacks may be re-opened, and which may only be remembered.
 *
 * Kept apart from [ContinuityService] so the rules are testable without Android: the interesting
 * part of continuity is not the storage, it is deciding what it is safe to bring back.
 */
object ContinuityPolicy {
    /**
     * A screen nobody has been on for a month is not "where you left off", it is a surprise.
     */
    val MAX_AGE: Duration = 30.days

    /**
     * Pages that are a moment of playback rather than a place.
     *
     * Re-opening the app into a player is not continuity: the media may be gone, the session may
     * have expired, and a resume prompt on the detail page is exactly where a viewer who was
     * interrupted wants to land. These are dropped when the stack is written, so even a crash in
     * the middle of an episode leaves the page behind it.
     */
    fun isPlayback(destination: Destination): Boolean =
        when (destination) {
            is Destination.Playback,
            is Destination.PlaybackList,
            is Destination.Slideshow,
            -> true

            else -> false
        }

    fun toPersist(stack: List<Destination>): List<Destination> {
        val places = stack.filterNot(::isPlayback)
        // The home page is always a valid place to start from, even if a viewer was watching
        // something straight out of a deep link.
        return places.ifEmpty { listOf(Destination.Home()) }
    }

    fun isFresh(
        savedAt: Long,
        now: Long,
    ): Boolean = savedAt > 0L && now >= savedAt && now - savedAt <= MAX_AGE.inWholeMilliseconds
}

/**
 * Remembers the last screen for each profile, so a cold start re-opens it.
 *
 * Android TV kills background apps without warning. The activity's saved instance state covers a
 * configuration change and a kill that the system later restores, but not the common case: the
 * viewer left the app on a series page, the television reclaimed the memory, and the next launch
 * dropped them back on Home. This is the missing half of that promise.
 *
 * The record is keyed by user id and carries its own timestamp: a profile switch must never
 * restore somebody else's page, and a page from last month is not worth re-opening.
 */
@Singleton
class ContinuityService
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun save(
            userId: String?,
            stack: List<Destination>,
        ) {
            if (userId.isNullOrBlank() || stack.isEmpty()) return
            val encoded = DestinationCodec.encode(ContinuityPolicy.toPersist(stack)) ?: return
            prefs
                .edit()
                .putString(stackKey(userId), encoded)
                .putLong(savedAtKey(userId), System.currentTimeMillis())
                .apply()
        }

        fun load(
            userId: String,
            now: Long = System.currentTimeMillis(),
        ): List<Destination>? {
            if (userId.isBlank()) return null
            val savedAt = prefs.getLong(savedAtKey(userId), 0L)
            if (!ContinuityPolicy.isFresh(savedAt, now)) {
                if (savedAt > 0L) clear(userId)
                return null
            }
            val encoded = prefs.getString(stackKey(userId), null) ?: return null
            return DestinationCodec.decode(encoded)?.takeIf { it.isNotEmpty() }
        }

        fun clear(userId: String) {
            prefs
                .edit()
                .remove(stackKey(userId))
                .remove(savedAtKey(userId))
                .apply()
        }

        private fun stackKey(userId: String) = "stack_$userId"

        private fun savedAtKey(userId: String) = "saved_at_$userId"

        private companion object {
            const val PREFS_NAME = "continuity"
        }
    }
