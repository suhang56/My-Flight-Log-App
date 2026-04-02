package com.flightlog.app.ui.home

import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedFlightItemTest {

    // ── Helper factories ──────────────────────────────────────────────────────

    private fun calendarFlight(
        id: Long = 1,
        calendarEventId: Long = 100,
        legIndex: Int = 0,
        flightNumber: String = "NH847",
        depCode: String = "HND",
        arrCode: String = "LHR",
        scheduledTime: Long = 1_700_000_000_000L
    ) = CalendarFlight(
        id = id,
        calendarEventId = calendarEventId,
        legIndex = legIndex,
        flightNumber = flightNumber,
        departureCode = depCode,
        arrivalCode = arrCode,
        rawTitle = "$flightNumber $depCode-$arrCode",
        scheduledTime = scheduledTime
    )

    private fun logbookFlight(
        id: Long = 1,
        sourceCalendarEventId: Long? = null,
        sourceLegIndex: Int? = null,
        flightNumber: String = "NH847",
        depCode: String = "HND",
        arrCode: String = "LHR",
        departureTimeUtc: Long = 1_700_000_000_000L
    ) = LogbookFlight(
        id = id,
        sourceCalendarEventId = sourceCalendarEventId,
        sourceLegIndex = sourceLegIndex,
        flightNumber = flightNumber,
        departureCode = depCode,
        arrivalCode = arrCode,
        departureTimeUtc = departureTimeUtc
    )

    // ── merge: empty inputs ───────────────────────────────────────────────────

    @Test
    fun `merge with both lists empty returns empty`() {
        val result = UnifiedFlightItem.merge(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `merge with only calendar flights returns all as FromCalendar`() {
        val cal = listOf(calendarFlight(id = 1), calendarFlight(id = 2, calendarEventId = 200))
        val result = UnifiedFlightItem.merge(cal, emptyList())
        assertEquals(2, result.size)
        assertTrue(result.all { it is UnifiedFlightItem.FromCalendar })
    }

    @Test
    fun `merge with only logbook flights returns all as FromLogbook`() {
        val log = listOf(logbookFlight(id = 1), logbookFlight(id = 2))
        val result = UnifiedFlightItem.merge(emptyList(), log)
        assertEquals(2, result.size)
        assertTrue(result.all { it is UnifiedFlightItem.FromLogbook })
    }

    // ── merge: de-duplication ─────────────────────────────────────────────────

    @Test
    fun `merge de-duplicates calendar flight when logbook has matching source`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100, legIndex = 0))
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 100, sourceLegIndex = 0)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(1, result.size)
        assertTrue(result[0] is UnifiedFlightItem.FromLogbook)
    }

    @Test
    fun `merge keeps calendar flight when logbook source doesn't match`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100, legIndex = 0))
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 999, sourceLegIndex = 0)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(2, result.size)
    }

    @Test
    fun `merge handles multi-leg de-duplication correctly`() {
        val cal = listOf(
            calendarFlight(id = 1, calendarEventId = 100, legIndex = 0),
            calendarFlight(id = 2, calendarEventId = 100, legIndex = 1)
        )
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 100, sourceLegIndex = 0)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        // Leg 0 de-duplicated (logbook version), leg 1 kept from calendar
        assertEquals(2, result.size)
        val types = result.map { it::class }
        assertTrue(types.contains(UnifiedFlightItem.FromLogbook::class))
        assertTrue(types.contains(UnifiedFlightItem.FromCalendar::class))
    }

    @Test
    fun `merge with manual logbook flights (null source) keeps all calendar flights`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100))
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = null, sourceLegIndex = null)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(2, result.size)
    }

    // ── merge: sorting ────────────────────────────────────────────────────────

    @Test
    fun `merge returns items sorted by sortKey descending`() {
        val cal = listOf(
            calendarFlight(id = 1, calendarEventId = 100, scheduledTime = 3000L),
            calendarFlight(id = 2, calendarEventId = 200, scheduledTime = 1000L)
        )
        val log = listOf(
            logbookFlight(id = 10, departureTimeUtc = 2000L)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(listOf(3000L, 2000L, 1000L), result.map { it.sortKey })
    }

    // ── sortKey access ────────────────────────────────────────────────────────

    @Test
    fun `FromCalendar sortKey returns scheduledTime`() {
        val item = UnifiedFlightItem.FromCalendar(calendarFlight(scheduledTime = 12345L))
        assertEquals(12345L, item.sortKey)
    }

    @Test
    fun `FromLogbook sortKey returns departureTimeUtc`() {
        val item = UnifiedFlightItem.FromLogbook(logbookFlight(departureTimeUtc = 67890L))
        assertEquals(67890L, item.sortKey)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `merge with empty flight numbers and codes`() {
        val cal = listOf(
            calendarFlight(id = 1, calendarEventId = 100, flightNumber = "", depCode = "", arrCode = "")
        )
        val result = UnifiedFlightItem.merge(cal, emptyList())
        assertEquals(1, result.size)
        assertEquals("", result[0].flightNumber)
        assertEquals("", result[0].departureCode)
        assertEquals("", result[0].arrivalCode)
    }

    @Test
    fun `merge with zero sortKey`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100, scheduledTime = 0L))
        val result = UnifiedFlightItem.merge(cal, emptyList())
        assertEquals(0L, result[0].sortKey)
    }

    @Test
    fun `merge with Long MAX_VALUE sortKey`() {
        val log = listOf(logbookFlight(id = 1, departureTimeUtc = Long.MAX_VALUE))
        val result = UnifiedFlightItem.merge(emptyList(), log)
        assertEquals(Long.MAX_VALUE, result[0].sortKey)
    }

    @Test
    fun `merge with large number of flights`() {
        val cal = (1L..100L).map {
            calendarFlight(id = it, calendarEventId = it + 1000, scheduledTime = it * 1000)
        }
        val log = (1L..50L).map {
            logbookFlight(id = it, departureTimeUtc = it * 2000)
        }
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(150, result.size)
        // Verify sorted descending
        for (i in 0 until result.size - 1) {
            assertTrue(result[i].sortKey >= result[i + 1].sortKey)
        }
    }

    @Test
    fun `merge de-duplication with legIndex mismatch keeps both`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100, legIndex = 1))
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 100, sourceLegIndex = 0)
        )
        val result = UnifiedFlightItem.merge(cal, log)
        // Different legIndex means no de-duplication
        assertEquals(2, result.size)
    }
}
