package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last guide a profile loaded, kept for the other Live TV surface.
 *
 * Live TV and Sports are separate navigation entries, so each one builds its own
 * [com.github.damontecres.wholphin.ui.detail.livetv.LiveTvViewModel] and used to fetch the channel
 * list and 48 hours of programmes again on every visit. It is the same guide on both pages and it
 * changes slowly, so the snapshot is kept here and handed to the next page that opens.
 *
 * The snapshot is per profile: two people sharing a television must not see each other's guide, and
 * a `null` user (still signing in) is its own key rather than a wildcard.
 */
@Singleton
class LiveTvCache
    @Inject
    constructor() {
        private var snapshot: Snapshot? = null

        /**
         * The guide this profile loaded, if it is recent enough to draw.
         *
         * A snapshot older than [LiveTvCachePolicy.MAX_AGE_MS] is a different evening's schedule, so
         * it is dropped rather than drawn over channels that have moved on.
         */
        fun get(
            userId: UUID?,
            now: Long = System.currentTimeMillis(),
        ): Snapshot? =
            synchronized(this) {
                val cached = snapshot ?: return null
                if (cached.userId != userId) return null
                if (!LiveTvCachePolicy.isUsable(cached.ageMs(now))) return null
                cached
            }

        /**
         * @param complete whether the whole channel list has its listings. A snapshot taken at the
         * first frame carries programmes for the first ten channels and filler for the rest: that
         * is worth painting, but it must never be the reason a page skips fetching.
         */
        fun put(
            userId: UUID?,
            state: LiveTvState,
            complete: Boolean = false,
            now: Long = System.currentTimeMillis(),
        ) {
            synchronized(this) {
                snapshot = Snapshot(userId, state, complete, now)
            }
        }

        fun clear() {
            synchronized(this) {
                snapshot = null
            }
        }
    }

    data class Snapshot(
        val userId: UUID?,
        val state: LiveTvState,
        val complete: Boolean,
        val storedAt: Long,
    ) {
        fun ageMs(now: Long): Long = now - storedAt
    }

/**
 * When a held guide is worth drawing, and when it is worth trusting instead of asking the server.
 *
 * Two thresholds on purpose: drawing a few-minutes-old guide is free and removes the spinner, while
 * *skipping* the request is only safe for a guide that was whole when it was stored.
 */
object LiveTvCachePolicy {
    /** A page opening inside this window reuses the guide without a request. */
    const val FRESH_MS = 90_000L

    /** Older than fifteen minutes and the guide is a different evening; do not draw it at all. */
    const val MAX_AGE_MS = 15L * 60L * 1000L

    fun isUsable(ageMs: Long): Boolean = ageMs >= 0 && ageMs < MAX_AGE_MS

    fun isFresh(ageMs: Long): Boolean = ageMs >= 0 && ageMs < FRESH_MS
}
