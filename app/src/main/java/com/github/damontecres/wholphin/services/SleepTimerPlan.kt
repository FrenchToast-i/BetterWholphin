package com.github.damontecres.wholphin.services

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * "Stop after this one."
 *
 * Tonight answers "can I finish this before bed?"; this is the other half of the same promise. A
 * viewer who presses Resume at 11:52 with a 42-minute episode still needs the television to stop at
 * bedtime, or the plan was only advice. The timer is intentionally dumb — it counts wall-clock time
 * from the moment it is set — because a TV that keeps playing while a viewer falls asleep is the
 * thing the feature exists to prevent.
 *
 * Kept pure and separate from the player so the arithmetic is testable without media3 or Android:
 * the interesting parts are the midnight wrap in [minutesUntilBedtime] and the ramp in
 * [fadeVolume].
 */
object SleepTimerPlan {
    /**
     * Offered lengths, in minutes. A television sleep timer is for the end of an evening, so the
     * list starts at a quarter of an hour and ends at a film: "in 5 minutes" belongs to a phone
     * alarm, and every extra row makes the sheet taller than the picture it hangs over.
     */
    val OPTIONS = listOf(15, 30, 45, 60, 90)

    /** The last [FADE_SECONDS] of the timer ramp the volume down instead of cutting the sound. */
    const val FADE_SECONDS = 20

    /** Below this the bedtime is effectively now; there is nothing to plan. */
    const val MIN_BEDTIME_MINUTES = 2

    /** Above this the bedtime is another day away, not tonight's. */
    const val MAX_BEDTIME_MINUTES = 6 * 60

    /**
     * Minutes from [now] to the next occurrence of the bedtime clock time, or null when that is
     * either already here or more than [MAX_BEDTIME_MINUTES] away.
     *
     * A bedtime of 00:30 read at 23:40 is tomorrow morning (50 minutes), while the same bedtime
     * read at 00:15 is already past — wrapping it would offer a 24-hour timer.
     */
    fun minutesUntilBedtime(
        now: LocalDateTime,
        bedtimeMinutes: Int,
    ): Int? {
        if (bedtimeMinutes <= 0) return null
        val bedtime = LocalTime.of((bedtimeMinutes / 60) % 24, bedtimeMinutes % 60)
        val target =
            if (bedtime.isAfter(now.toLocalTime())) {
                now.toLocalDate().atTime(bedtime)
            } else {
                now.toLocalDate().atTime(bedtime).plusDays(1)
            }
        return Duration
            .between(now, target)
            .toMinutes()
            .toInt()
            .takeIf { it in MIN_BEDTIME_MINUTES..MAX_BEDTIME_MINUTES }
    }

    /**
     * Player volume for [remainingSeconds] left on the timer: full until the fade begins, then a
     * linear ramp to silence at zero.
     */
    fun fadeVolume(remainingSeconds: Int): Float =
        if (remainingSeconds >= FADE_SECONDS) {
            1f
        } else {
            (remainingSeconds.coerceAtLeast(0).toFloat() / FADE_SECONDS).coerceIn(0f, 1f)
        }

    /** Whole minutes a viewer should see: a timer with 61 seconds left says "2m", not "1m". */
    fun remainingMinutes(remainingSeconds: Int): Int = (remainingSeconds.coerceAtLeast(0) + 59) / 60
}
