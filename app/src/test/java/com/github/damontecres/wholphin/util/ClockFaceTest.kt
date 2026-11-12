package com.github.damontecres.wholphin.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The compact bar clock is a watch face — the time over its day period — and it has to find that
 * split in whatever the locale emits, not in the position an English string happens to use.
 */
class ClockFaceTest {
    @Test
    fun englishSplitsTimeFromDayPeriod() {
        assertEquals("4:16" to "PM", splitClockFace("4:16 PM"))
    }

    @Test
    fun aLeadingDayPeriodIsStillFound() {
        assertEquals("4:16" to "PM", splitClockFace("PM 4:16"))
    }

    @Test
    fun aLocaleWithoutASpaceSplitsToo() {
        assertEquals("4:16" to "下午", splitClockFace("下午4:16"))
    }

    @Test
    fun twentyFourHourTimeHasNoDayPeriod() {
        assertEquals("23:55" to "", splitClockFace("23:55"))
    }

    @Test
    fun dotSeparatedTimeIsRecognised() {
        assertEquals("23.55" to "", splitClockFace("23.55"))
    }

    @Test
    fun aStringWithoutAClockIsLeftWhole() {
        assertEquals("Later" to "", splitClockFace("Later"))
    }
}
