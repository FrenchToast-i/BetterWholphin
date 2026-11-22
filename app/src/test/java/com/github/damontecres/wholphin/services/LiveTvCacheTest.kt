package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LiveTvCacheTest {
    private val userA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val userB = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun state(name: String) = LiveTvState()

    @Test
    fun `a stored guide is returned for the same profile`() {
        val cache = LiveTvCache()
        cache.put(userA, state("a"), complete = true, now = 1_000L)

        val snapshot = cache.get(userA, now = 2_000L)

        assertEquals(1_000L, snapshot?.storedAt)
        assertEquals(1_000L, snapshot?.ageMs(2_000L))
    }

    @Test
    fun `another profile never sees the guide`() {
        val cache = LiveTvCache()
        cache.put(userA, state("a"), complete = true, now = 1_000L)

        assertNull(cache.get(userB, now = 2_000L))
    }

    @Test
    fun `a signed out reader is its own key, not a wildcard`() {
        val cache = LiveTvCache()
        cache.put(userA, state("a"), complete = true, now = 1_000L)

        assertNull(cache.get(null, now = 2_000L))

        cache.put(null, state("signed out"), complete = true, now = 1_000L)
        assertNull(cache.get(userA, now = 2_000L))
    }

    @Test
    fun `a guide from another evening is dropped rather than drawn`() {
        val cache = LiveTvCache()
        cache.put(userA, state("a"), complete = true, now = 0L)

        assertNull(cache.get(userA, now = LiveTvCachePolicy.MAX_AGE_MS))
        assertEquals(state("a"), cache.get(userA, now = LiveTvCachePolicy.MAX_AGE_MS - 1)?.state)
    }

    @Test
    fun `fresh means the request can be skipped`() {
        assertTrue(LiveTvCachePolicy.isFresh(LiveTvCachePolicy.FRESH_MS - 1))
        assertFalse(LiveTvCachePolicy.isFresh(LiveTvCachePolicy.FRESH_MS))
        assertTrue(LiveTvCachePolicy.isFresh(0L))
    }

    @Test
    fun `a partial snapshot is drawable but only a complete one is skippable`() {
        val cache = LiveTvCache()
        cache.put(userA, state("partial"), complete = false, now = 0L)

        val snapshot = cache.get(userA, now = 1_000L)

        assertEquals(state("partial"), snapshot?.state)
        assertFalse(snapshot!!.complete)

        cache.put(userA, state("whole"), complete = true, now = 0L)
        assertTrue(cache.get(userA, now = 1_000L)!!.complete)
    }

    @Test
    fun `a clock that jumps backwards is not treated as fresh`() {
        assertFalse(LiveTvCachePolicy.isUsable(-1L))
        assertFalse(LiveTvCachePolicy.isFresh(-1L))
    }

    @Test
    fun `clear forgets the guide`() {
        val cache = LiveTvCache()
        cache.put(userA, state("a"), complete = true, now = 0L)
        cache.clear()

        assertNull(cache.get(userA, now = 1_000L))
    }
}
