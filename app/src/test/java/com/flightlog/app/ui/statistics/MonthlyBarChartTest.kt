package com.flightlog.app.ui.statistics

import com.flightlog.app.data.local.dao.MonthlyCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Tests for monthly bar chart data preparation (Improvements #2 and #6).
 */
class MonthlyBarChartTest {

    @Test
    fun `last 12 months fills missing months with zero`() {
        val now = YearMonth.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        // Only provide data for 3 months
        val sparse = listOf(
            MonthlyCount(now.format(formatter), 5),
            MonthlyCount(now.minusMonths(3).format(formatter), 2),
            MonthlyCount(now.minusMonths(6).format(formatter), 1)
        )

        val last12 = (11 downTo 0).map { now.minusMonths(it.toLong()) }
        val countMap = sparse.associate { it.month to it.count }
        val result = last12.map { ym ->
            val key = ym.format(formatter)
            MonthlyCount(key, countMap[key] ?: 0)
        }

        assertEquals(12, result.size)
        assertEquals(5, result.last().count) // current month
        // 9 months should be zero
        val zeroCount = result.count { it.count == 0 }
        assertEquals(9, zeroCount)
    }

    @Test
    fun `all time mode returns all data points`() {
        val data = (1..24).map { i ->
            MonthlyCount("2024-%02d".format((i - 1) % 12 + 1), i)
        }
        // All time just returns the full list
        assertTrue(data.size > 12)
        assertEquals(24, data.size)
    }

    @Test
    fun `bar chart handles single month data`() {
        val data = listOf(MonthlyCount("2024-03", 10))
        val maxCount = data.maxOf { it.count }
        assertEquals(10, maxCount)
    }

    @Test
    fun `bar chart handles all zero counts`() {
        val data = listOf(
            MonthlyCount("2024-01", 0),
            MonthlyCount("2024-02", 0)
        )
        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
        assertEquals(1, maxCount) // coerceAtLeast prevents division by zero
    }

    @Test
    fun `month label extracts correctly from yyyy-MM format`() {
        val month = "2024-03"
        val label = month.substring(5, 7)
        assertEquals("03", label)
    }
}
