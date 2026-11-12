package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Pins the sleep timer's arithmetic: the midnight wrap in the bedtime option and the fade ramp.
 *
 * Both are the kind of off-by-one that is invisible in code review and obvious in a living room —
 * a bedtime offered 24 hours away, or audio cut mid-sentence instead of ramped down.
 */
class SleepTimerPlanTest {
    private fun at(
        hour: Int,
        minute: Int,
        day: Int = 10,
    ) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun bedtimeLaterTonightIsTheMinutesUntilIt() {
        // 22:00 with an 23:30 bedtime is 90 minutes of evening.
        assertEquals(90, SleepTimerPlan.minutesUntilBedtime(at(22, 0), 23 * 60 + 30))
    }

    @Test
    fun bedtimeAfterMidnightWrapsIntoTomorrow() {
        // 23:40 read against a 00:30 bedtime is 50 minutes, not "already past".
        assertEquals(50, SleepTimerPlan.minutesUntilBedtime(at(23, 40), 30))
    }

    @Test
    fun aBedtimeThatIsStillAheadTonightIsOfferedEvenAfterMidnight() {
        // 00:15 with a 00:30 bedtime is fifteen minutes away — the small hours are still a night.
        assertEquals(15, SleepTimerPlan.minutesUntilBedtime(at(0, 15), 30))
    }

    @Test
    fun bedtimeAlreadyPassedTonightIsNotOffered() {
        // 23:00 against a 22:00 bedtime is tomorrow, a day away: that is not tonight's timer.
        assertNull(SleepTimerPlan.minutesUntilBedtime(at(23, 0), 22 * 60))
    }

    @Test
    fun bedtimeTooFarAwayIsNotOffered() {
        // 14:00 against a 23:00 bedtime is not an evening the timer can describe.
        assertNull(SleepTimerPlan.minutesUntilBedtime(at(14, 0), 23 * 60))
    }

    @Test
    fun bedtimeOnlyMinutesAwayIsNotWorthOffering() {
        assertNull(SleepTimerPlan.minutesUntilBedtime(at(22, 59), 23 * 60))
    }

    @Test
    fun unsetBedtimeHasNoOption() {
        assertNull(SleepTimerPlan.minutesUntilBedtime(at(22, 0), 0))
    }

    @Test
    fun volumeIsFullUntilTheFadeAndThenLinear() {
        assertEquals(1f, SleepTimerPlan.fadeVolume(SleepTimerPlan.FADE_SECONDS), 0f)
        assertEquals(1f, SleepTimerPlan.fadeVolume(600), 0f)
        assertEquals(0.5f, SleepTimerPlan.fadeVolume(SleepTimerPlan.FADE_SECONDS / 2), 0f)
        assertEquals(0f, SleepTimerPlan.fadeVolume(0), 0f)
        // A negative countdown (a coroutine that woke late) must not invert the volume.
        assertEquals(0f, SleepTimerPlan.fadeVolume(-3), 0f)
    }

    @Test
    fun countdownRoundsUpSoItNeverReadsZeroWhileStillRunning() {
        assertEquals(1, SleepTimerPlan.remainingMinutes(30))
        assertEquals(2, SleepTimerPlan.remainingMinutes(61))
        assertEquals(30, SleepTimerPlan.remainingMinutes(30 * 60))
        assertEquals(0, SleepTimerPlan.remainingMinutes(0))
    }
}
