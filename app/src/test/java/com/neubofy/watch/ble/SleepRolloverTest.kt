package com.neubofy.watch.ble

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class SleepRolloverTest {

    /**
     * A testable version of the logic in GoBoultProtocol.
     */
    private fun getSleepDaysAgoOffset(hour: Int): Int {
        return if (hour >= 20) 1 else 0
    }

    private fun calculateAdjustedDaysAgo(rawDaysAgo: Int, hour: Int): Int {
        return rawDaysAgo - getSleepDaysAgoOffset(hour)
    }

    @Test
    fun testSleepOffset_Before8PM() {
        // At 10 AM, offset should be 0
        val offset = getSleepDaysAgoOffset(10)
        assertEquals(0, offset)

        // Today's sleep (raw 0) -> 0
        assertEquals(0, calculateAdjustedDaysAgo(0, 10))
        // Yesterday's sleep (raw 1) -> 1
        assertEquals(1, calculateAdjustedDaysAgo(1, 10))
        // Day before yesterday's sleep (raw 2) -> 2
        assertEquals(2, calculateAdjustedDaysAgo(2, 10))
    }

    @Test
    fun testSleepOffset_After8PM() {
        // At 10 PM (22:00), offset should be 1
        val offset = getSleepDaysAgoOffset(22)
        assertEquals(1, offset)

        // Today's sleep (raw 0) -> -1 (means tonight's/tomorrow's sleep)
        assertEquals(-1, calculateAdjustedDaysAgo(0, 22))
        // Yesterday's sleep (raw 1) -> 0 (Watch says "yesterday", but it's "today" for phone)
        assertEquals(0, calculateAdjustedDaysAgo(1, 22))
        // Day before yesterday's sleep (raw 2) -> 1
        assertEquals(1, calculateAdjustedDaysAgo(2, 22))
    }
}
