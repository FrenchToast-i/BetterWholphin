package com.github.damontecres.wholphin.services

import org.junit.Assert
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class OnAirPlannerTest {
    private val entries = (1..10).map { OnAirEntry(UUID.randomUUID(), it * 10) }

    @Test
    fun `Playlist is deterministic and a permutation`() {
        val first = OnAirPlanner.playlist("movies", entries, 42L)
        val second = OnAirPlanner.playlist("movies", entries, 42L)
        Assert.assertEquals(first, second)
        Assert.assertEquals(entries.map { it.id }.toSet(), first.map { it.id }.toSet())
    }

    @Test
    fun `Playlist differs by channel and week`() {
        val movies = OnAirPlanner.playlist("movies", entries, 42L)
        val binge = OnAirPlanner.playlist("binge", entries, 42L)
        val nextWeek = OnAirPlanner.playlist("movies", entries, 43L)
        Assert.assertNotEquals(movies, binge)
        Assert.assertNotEquals(movies, nextWeek)
    }

    @Test
    fun `Schedule starts at the anchor`() {
        val playlist =
            listOf(
                OnAirEntry(UUID.randomUUID(), 10),
                OnAirEntry(UUID.randomUUID(), 20),
                OnAirEntry(UUID.randomUUID(), 30),
                OnAirEntry(UUID.randomUUID(), 40),
            )
        val programs = OnAirPlanner.schedule(playlist, anchorMs = 1_000L, nowMs = 1_000L, count = 2)
        Assert.assertEquals(2, programs.size)
        Assert.assertEquals(1_000L, programs[0].startMs)
        Assert.assertEquals(1_000L + 10 * 60_000L, programs[0].endMs)
        Assert.assertEquals(programs[0].endMs, programs[1].startMs)
    }

    @Test
    fun `Schedule joins a program in progress`() {
        val playlist =
            listOf(
                OnAirEntry(UUID.randomUUID(), 10),
                OnAirEntry(UUID.randomUUID(), 20),
                OnAirEntry(UUID.randomUUID(), 30),
                OnAirEntry(UUID.randomUUID(), 40),
            )
        val anchor = 0L
        // 15 minutes in: the second (20 minute) program started at minute 10.
        val programs = OnAirPlanner.schedule(playlist, anchor, 15 * 60_000L, count = 1)
        Assert.assertEquals(playlist[1], programs[0].entry)
        Assert.assertEquals(10 * 60_000L, programs[0].startMs)
        Assert.assertEquals(5 * 60_000L, programs[0].elapsedMs(15 * 60_000L))
        Assert.assertEquals(0.25f, programs[0].progress(15 * 60_000L), 0.001f)
    }

    @Test
    fun `Schedule wraps into the next loop`() {
        val playlist =
            listOf(
                OnAirEntry(UUID.randomUUID(), 10),
                OnAirEntry(UUID.randomUUID(), 20),
                OnAirEntry(UUID.randomUUID(), 30),
                OnAirEntry(UUID.randomUUID(), 40),
            )
        // The loop is 100 minutes; 95 minutes in is the last program, and the next one is the
        // first entry of the following loop.
        val programs = OnAirPlanner.schedule(playlist, 0L, 95 * 60_000L, count = 2)
        Assert.assertEquals(playlist[3], programs[0].entry)
        Assert.assertEquals(60 * 60_000L, programs[0].startMs)
        Assert.assertEquals(playlist[0], programs[1].entry)
        Assert.assertEquals(100 * 60_000L, programs[1].startMs)
    }

    @Test
    fun `Tune in returns the program that is airing`() {
        val playlist =
            listOf(
                OnAirEntry(UUID.randomUUID(), 10),
                OnAirEntry(UUID.randomUUID(), 20),
                OnAirEntry(UUID.randomUUID(), 30),
                OnAirEntry(UUID.randomUUID(), 40),
            )
        val programs = OnAirPlanner.schedule(playlist, 0L, 40 * 60_000L, count = 3)
        Assert.assertEquals(programs[0], OnAirPlanner.tuneIn(programs, 40 * 60_000L))
    }

    @Test
    fun `Runtime is clamped so a bad entry cannot stop the channel`() {
        val playlist =
            listOf(
                OnAirEntry(UUID.randomUUID(), 0),
                OnAirEntry(UUID.randomUUID(), 5_000),
                OnAirEntry(UUID.randomUUID(), 30),
                OnAirEntry(UUID.randomUUID(), 30),
            )
        val programs = OnAirPlanner.schedule(playlist, 0L, 0L, count = 2)
        Assert.assertEquals(OnAirPlanner.MIN_RUNTIME_MINUTES * 60_000L, programs[0].endMs - programs[0].startMs)
        Assert.assertEquals(OnAirPlanner.MAX_RUNTIME_MINUTES * 60_000L, programs[1].endMs - programs[1].startMs)
    }

    @Test
    fun `Week anchor is Monday midnight`() {
        // Wednesday 2026-11-11 15:00 UTC.
        val now = Instant.parse("2026-11-11T15:00:00Z").toEpochMilli()
        val anchor = OnAirPlanner.weekAnchor(now, ZoneOffset.UTC)
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchor), ZoneOffset.UTC)
        Assert.assertEquals(2026, date.year)
        Assert.assertEquals(11, date.monthValue)
        Assert.assertEquals(9, date.dayOfMonth)
        Assert.assertEquals(0, date.hour)
        Assert.assertEquals(0, date.minute)
    }

    @Test
    fun `Empty playlist yields no programs`() {
        Assert.assertTrue(OnAirPlanner.schedule(emptyList(), 0L, 0L, 5).isEmpty())
        Assert.assertNull(OnAirPlanner.tuneIn(emptyList(), 0L))
    }
}
