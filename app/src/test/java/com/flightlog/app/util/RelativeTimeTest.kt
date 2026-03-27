package com.flightlog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RelativeTimeTest {

    private val zone = ZoneId.of("UTC")

    /** Helper: epoch millis for a given date at noon UTC. */
    private fun millis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private val today = LocalDate.of(2026, 3, 27)
    private val nowMillis = millis(today)

    // ── toRelativeTimeLabel (LocalDate overload) ─────────────────────────────

    @Test
    fun `LocalDate - same day is Today`() {
        assertEquals("Today", today.toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - one day ahead is Tomorrow`() {
        assertEquals("Tomorrow", today.plusDays(1).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - one day behind is Yesterday`() {
        assertEquals("Yesterday", today.minusDays(1).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 2 days ahead`() {
        assertEquals("In 2 days", today.plusDays(2).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 6 days ahead`() {
        assertEquals("In 6 days", today.plusDays(6).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 3 days behind`() {
        assertEquals("3 days ago", today.minusDays(3).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 6 days behind`() {
        assertEquals("6 days ago", today.minusDays(6).toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 7 days ahead shows absolute date`() {
        val date = today.plusDays(7) // Apr 3
        assertEquals("Apr 3", date.toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - 7 days behind shows absolute date`() {
        val date = today.minusDays(7) // Mar 20
        assertEquals("Mar 20", date.toRelativeTimeLabel(today))
    }

    @Test
    fun `LocalDate - far future shows absolute date`() {
        val date = LocalDate.of(2026, 12, 25)
        assertEquals("Dec 25", date.toRelativeTimeLabel(today))
    }

    // ── toRelativeTimeLabel (Long overload) ──────────────────────────────────

    @Test
    fun `Long - same day is Today`() {
        assertEquals("Today", nowMillis.toRelativeTimeLabel(nowMillis, zone))
    }

    @Test
    fun `Long - tomorrow`() {
        val tomorrow = millis(today.plusDays(1))
        assertEquals("Tomorrow", tomorrow.toRelativeTimeLabel(nowMillis, zone))
    }

    @Test
    fun `Long - yesterday`() {
        val yesterday = millis(today.minusDays(1))
        assertEquals("Yesterday", yesterday.toRelativeTimeLabel(nowMillis, zone))
    }

    @Test
    fun `Long - 4 days ahead`() {
        val future = millis(today.plusDays(4))
        assertEquals("In 4 days", future.toRelativeTimeLabel(nowMillis, zone))
    }

    @Test
    fun `Long - 5 days behind`() {
        val past = millis(today.minusDays(5))
        assertEquals("5 days ago", past.toRelativeTimeLabel(nowMillis, zone))
    }

    @Test
    fun `Long - 10 days ahead shows absolute date`() {
        val future = millis(today.plusDays(10)) // Apr 6
        assertEquals("Apr 6", future.toRelativeTimeLabel(nowMillis, zone))
    }

    // ── toRelativeElapsedLabel ────────────────────────────────────────────────

    @Test
    fun `elapsed - 0 seconds is Just now`() {
        assertEquals("Just now", nowMillis.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 30 seconds is Just now`() {
        val thirtySecsAgo = nowMillis - 30_000
        assertEquals("Just now", thirtySecsAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 1 minute`() {
        val oneMinAgo = nowMillis - 60_000
        assertEquals("1 min ago", oneMinAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 45 minutes`() {
        val fortyFiveMinAgo = nowMillis - 45 * 60_000
        assertEquals("45 min ago", fortyFiveMinAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 59 minutes`() {
        val fiftyNineMinAgo = nowMillis - 59 * 60_000
        assertEquals("59 min ago", fiftyNineMinAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 1 hour`() {
        val oneHrAgo = nowMillis - 60 * 60_000
        assertEquals("1 hr ago", oneHrAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 12 hours`() {
        val twelveHrAgo = nowMillis - 12L * 60 * 60_000
        assertEquals("12 hr ago", twelveHrAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 23 hours`() {
        val twentyThreeHrAgo = nowMillis - 23L * 60 * 60_000
        assertEquals("23 hr ago", twentyThreeHrAgo.toRelativeElapsedLabel(nowMillis))
    }

    @Test
    fun `elapsed - 24 hours falls through to day label`() {
        val twentyFourHrAgo = nowMillis - 24L * 60 * 60_000
        // 24 hours ago from noon = yesterday noon -> "Yesterday"
        val result = twentyFourHrAgo.toRelativeElapsedLabel(nowMillis)
        assertEquals("Yesterday", result)
    }

    @Test
    fun `elapsed - future timestamp treated as Just now`() {
        val futureMillis = nowMillis + 60_000
        assertEquals("Just now", futureMillis.toRelativeElapsedLabel(nowMillis))
    }
}
