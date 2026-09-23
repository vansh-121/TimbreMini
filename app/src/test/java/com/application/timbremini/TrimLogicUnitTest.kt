package com.application.timbremini

import com.application.timbremini.data.formatTimeMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimLogicUnitTest {

    @Test
    fun testFormatTimeMs_shortDuration() {
        // 5 seconds
        assertEquals("00:05", formatTimeMs(5000L, includeMillis = false))
        assertEquals("00:05.0", formatTimeMs(5000L, includeMillis = true))

        // 12.35 seconds
        assertEquals("00:12.3", formatTimeMs(12350L, includeMillis = true))
    }

    @Test
    fun testFormatTimeMs_minutesDuration() {
        // 2 minutes 35 seconds
        val ms = (2 * 60 + 35) * 1000L + 700L
        assertEquals("02:35", formatTimeMs(ms, includeMillis = false))
        assertEquals("02:35.7", formatTimeMs(ms, includeMillis = true))
    }

    @Test
    fun testFormatTimeMs_hoursDuration() {
        // 1 hour 15 minutes 42 seconds
        val ms = (1 * 3600 + 15 * 60 + 42) * 1000L
        assertEquals("01:15:42", formatTimeMs(ms, includeMillis = false))
    }

    @Test
    fun testFormatTimeMs_zeroAndNegative() {
        assertEquals("00:00", formatTimeMs(0L))
        assertEquals("00:00", formatTimeMs(-500L))
    }

    @Test
    fun testTrimBoundsCalculation() {
        val totalMs = 60000L // 60s
        val startMs = 5000L
        val endMs = 25000L

        val trimDuration = endMs - startMs
        assertEquals(20000L, trimDuration)
        assertTrue("Trim duration must be at least 500ms", trimDuration >= 500L)
        assertTrue("Start must be >= 0", startMs >= 0L)
        assertTrue("End must be <= total", endMs <= totalMs)
    }
}
