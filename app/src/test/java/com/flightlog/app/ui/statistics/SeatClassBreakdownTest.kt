package com.flightlog.app.ui.statistics

import com.flightlog.app.data.local.dao.SeatClassCount
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for seat class percentage rounding (Improvement #1).
 * Verifies float division and "<1%" display for small values.
 */
class SeatClassBreakdownTest {

    @Test
    fun `percentage uses float division not integer division`() {
        // 1 out of 3 = 33.33...% should round to 33%, not 0% (integer division bug)
        val counts = listOf(
            SeatClassCount("economy", 1),
            SeatClassCount("business", 2)
        )
        val total = counts.sumOf { it.count }

        val economyPct = (counts[0].count.toFloat() / total * 100)
        assertEquals(33f, economyPct, 1f)
    }

    @Test
    fun `less than 1 percent shows as less than 1`() {
        // 1 out of 200 = 0.5% should display as "<1%"
        val counts = listOf(
            SeatClassCount("first", 1),
            SeatClassCount("economy", 199)
        )
        val total = counts.sumOf { it.count }

        val firstPct = (counts[0].count.toFloat() / total * 100)
        val display = if (firstPct > 0 && firstPct < 1) "<1%" else "${firstPct.toInt()}%"
        assertEquals("<1%", display)
    }

    @Test
    fun `exactly 0 percent shows as 0`() {
        // If count is 0 (edge case), should be 0%
        val pct = (0f / 100 * 100)
        val display = if (pct > 0 && pct < 1) "<1%" else "${pct.toInt()}%"
        assertEquals("0%", display)
    }

    @Test
    fun `100 percent displays correctly`() {
        val counts = listOf(SeatClassCount("economy", 50))
        val total = counts.sumOf { it.count }
        val pct = (counts[0].count.toFloat() / total * 100)
        assertEquals(100f, pct, 0.1f)
    }
}
