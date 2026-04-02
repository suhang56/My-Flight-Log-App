package com.flightlog.app.ui.home

import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ── merge: empty inputs (SOUL RULE: zero flights in both sources) ────────

    @Test
    fun `merge with both lists empty returns empty - no crash`() {
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

    // ── combine() emission: one repo empty, other has N items ────────────────

    @Test
    fun `merge with empty calendar and N logbook items returns N items`() {
        val log = (1L..5L).map { logbookFlight(id = it, departureTimeUtc = it * 1000) }
        val result = UnifiedFlightItem.merge(emptyList(), log)
        assertEquals(5, result.size)
        assertTrue(result.all { it is UnifiedFlightItem.FromLogbook })
    }

    @Test
    fun `merge with N calendar items and empty logbook returns N items`() {
        val cal = (1L..5L).map {
            calendarFlight(id = it, calendarEventId = it + 100, scheduledTime = it * 1000)
        }
        val result = UnifiedFlightItem.merge(cal, emptyList())
        assertEquals(5, result.size)
        assertTrue(result.all { it is UnifiedFlightItem.FromCalendar })
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

    @Test
    fun `merge de-duplication with legIndex mismatch keeps both`() {
        val cal = listOf(calendarFlight(id = 1, calendarEventId = 100, legIndex = 1))
        val log = listOf(
            logbookFlight(id = 10, sourceCalendarEventId = 100, sourceLegIndex = 0)
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

    // ── Edge cases: boundary values ──────────────────────────────────────────

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

    // ── SOUL RULE: >200 flights must not cause issues ────────────────────────

    @Test
    fun `merge with 200+ flights completes without error and maintains sort order`() {
        val cal = (1L..120L).map {
            calendarFlight(id = it, calendarEventId = it + 1000, scheduledTime = it * 1000)
        }
        val log = (1L..120L).map {
            logbookFlight(id = it, departureTimeUtc = it * 2000)
        }
        val result = UnifiedFlightItem.merge(cal, log)
        assertEquals(240, result.size)
        for (i in 0 until result.size - 1) {
            assertTrue(
                "Items at index $i and ${i + 1} not in descending order",
                result[i].sortKey >= result[i + 1].sortKey
            )
        }
    }

    // ── SOUL RULE: single airport (dep == arr) renders correctly ─────────────

    @Test
    fun `merge with same departure and arrival code produces valid item`() {
        val cal = listOf(
            calendarFlight(id = 1, calendarEventId = 100, depCode = "NRT", arrCode = "NRT")
        )
        val result = UnifiedFlightItem.merge(cal, emptyList())
        assertEquals(1, result.size)
        assertEquals("NRT", result[0].departureCode)
        assertEquals("NRT", result[0].arrivalCode)
    }

    @Test
    fun `logbook flight with same departure and arrival code`() {
        val log = listOf(
            logbookFlight(id = 1, depCode = "HND", arrCode = "HND")
        )
        val result = UnifiedFlightItem.merge(emptyList(), log)
        assertEquals(1, result.size)
        assertEquals("HND", result[0].departureCode)
        assertEquals("HND", result[0].arrivalCode)
    }

    // ── HomeUiState: routeSegments ───────────────────────────────────────────

    @Test
    fun `routeSegments empty when no items`() {
        val state = HomeUiState()
        assertTrue(state.routeSegments.isEmpty())
    }

    @Test
    fun `routeSegments skips items with blank codes`() {
        val items = listOf(
            UnifiedFlightItem.FromCalendar(
                calendarFlight(id = 1, calendarEventId = 100, depCode = "", arrCode = "")
            )
        )
        val state = HomeUiState(upcomingItems = items)
        assertTrue(state.routeSegments.isEmpty())
    }

    @Test
    fun `routeSegments de-duplicates same route`() {
        val items = listOf(
            UnifiedFlightItem.FromCalendar(
                calendarFlight(id = 1, calendarEventId = 100, depCode = "HND", arrCode = "LHR", scheduledTime = 2000L)
            ),
            UnifiedFlightItem.FromLogbook(
                logbookFlight(id = 2, depCode = "HND", arrCode = "LHR", departureTimeUtc = 1000L)
            )
        )
        val state = HomeUiState(upcomingItems = items)
        assertEquals(1, state.routeSegments.size)
        assertEquals("HND", state.routeSegments[0].departureCode)
        assertEquals("LHR", state.routeSegments[0].arrivalCode)
    }

    @Test
    fun `routeSegments highlights next upcoming route`() {
        val upcoming = listOf(
            UnifiedFlightItem.FromCalendar(
                calendarFlight(id = 1, calendarEventId = 100, depCode = "NRT", arrCode = "SFO", scheduledTime = 2000L)
            ),
            UnifiedFlightItem.FromCalendar(
                calendarFlight(id = 2, calendarEventId = 200, depCode = "SFO", arrCode = "LAX", scheduledTime = 3000L)
            )
        )
        val past = listOf(
            UnifiedFlightItem.FromLogbook(
                logbookFlight(id = 3, depCode = "HND", arrCode = "NRT", departureTimeUtc = 500L)
            )
        )
        val state = HomeUiState(upcomingItems = upcoming, pastItems = past)
        val segments = state.routeSegments
        assertEquals(3, segments.size)
        // NRT->SFO is earliest upcoming, should be highlighted
        val highlighted = segments.filter { it.isHighlighted }
        assertEquals(1, highlighted.size)
        assertEquals("NRT", highlighted[0].departureCode)
        assertEquals("SFO", highlighted[0].arrivalCode)
    }

    @Test
    fun `routeSegments no highlight when no upcoming items`() {
        val past = listOf(
            UnifiedFlightItem.FromLogbook(
                logbookFlight(id = 1, depCode = "HND", arrCode = "LHR", departureTimeUtc = 500L)
            )
        )
        val state = HomeUiState(pastItems = past)
        val segments = state.routeSegments
        assertEquals(1, segments.size)
        assertTrue(segments.none { it.isHighlighted })
    }
}
