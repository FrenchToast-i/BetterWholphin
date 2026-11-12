package com.github.damontecres.wholphin.util

/**
 * Splits a formatted time into its numeric face and its day period, for the compact watch face.
 *
 * The split cannot be "before/after the last space": locales disagree about the order and the
 * separator ("4:16 PM", "PM 4:16", "下午4:16", "23:55"), and a 24-hour locale has no day period at
 * all. Looking for the clock itself and taking whatever surrounds it labels every one of those
 * correctly, and leaves the face narrow enough to sit in a 50dp disc.
 *
 * @return the clock ("4:16") and the day period ("PM"), which is blank when there is none
 */
fun splitClockFace(time: String): Pair<String, String> {
    val clock = ClockPattern.find(time) ?: return time to ""
    val meridiem = time.removeRange(clock.range).trim()
    return clock.value to meridiem
}

private val ClockPattern = Regex("""\d{1,2}[:.]\d{2}""")
