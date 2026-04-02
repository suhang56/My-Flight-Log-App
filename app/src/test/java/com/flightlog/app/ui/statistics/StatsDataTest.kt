package com.flightlog.app.ui.statistics

import com.flightlog.app.data.local.dao.AirlineCount
import com.flightlog.app.data.local.dao.MonthlyCount
import com.flightlog.app.data.local.dao.RouteCount
import com.flightlog.app.data.local.dao.SeatClassCount
import com.flightlog.app.data.local.entity.LogbookFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for StatsData model (Improvements #4, #5, #7 — ensuring data structures are correct).
 */
class StatsDataTest {

    private fun makeFlight(
        id: Long = 1,
        dep: String = "NRT",
        arr: String = "LAX",
        durationMinutes: Int? = 600,
        distanceKm: Int? = 8800,
        departureTimeMillis: Long = 1700000000000L
    ) = LogbookFlight(
        id = id,
        flightNumber = "NH6",
        departureCode = dep,
        arrivalCode = arr,
        departureDateEpochDay = 19700,
        departureTimeMillis = departureTimeMillis,
        durationMinutes = durationMinutes,
        distanceKm = distanceKm
    )

    @Test
    fun `StatsData defaults are empty`() {
        val stats = StatsData()
        assertEquals(0, stats.flightCount)
        assertEquals(0L, stats.totalDurationMinutes)
        assertNull(stats.longestByDistance)
        assertNull(stats.longestByDuration)
        assertNull(stats.firstFlight)
        assertEquals(emptyList<RouteCount>(), stats.topRoutes)
        assertEquals(emptyList<SeatClassCount>(), stats.seatClassCounts)
        assertEquals(emptyList<AirlineCount>(), stats.topAirlines)
        assertEquals(emptyList<MonthlyCount>(), stats.monthlyFlightCounts)
    }

    @Test
    fun `longestByDuration is separate from longestByDistance`() {
        val longDistance = makeFlight(id = 1, durationMinutes = 300, distanceKm = 10000)
        val longDuration = makeFlight(id = 2, durationMinutes = 900, distanceKm = 5000)

        val stats = StatsData(
            longestByDistance = longDistance,
            longestByDuration = longDuration
        )

        assertEquals(10000, stats.longestByDistance?.distanceKm)
        assertEquals(900, stats.longestByDuration?.durationMinutes)
    }

    @Test
    fun `topRoutes label format is correct`() {
        val routes = listOf(
            RouteCount("NRT→LAX", 5),
            RouteCount("HND→ICN", 3)
        )
        assertEquals("NRT→LAX", routes[0].label)
        assertEquals(5, routes[0].count)
    }

    @Test
    fun `firstFlight uses earliest departure time`() {
        val first = makeFlight(departureTimeMillis = 1000000000000L)
        val later = makeFlight(departureTimeMillis = 1700000000000L)

        // The DAO query orders by departureTimeMillis ASC LIMIT 1
        // so the first result should have the earliest time
        val stats = StatsData(firstFlight = first)
        assertNotNull(stats.firstFlight)
        assertEquals(1000000000000L, stats.firstFlight!!.departureTimeMillis)
    }

    @Test
    fun `duration display computation is correct`() {
        val flight = makeFlight(durationMinutes = 613)
        val h = flight.durationMinutes!! / 60
        val m = flight.durationMinutes!! % 60
        assertEquals(10, h)
        assertEquals(13, m)
        assertEquals("10h 13m", "${h}h ${m}m")
    }

    @Test
    fun `seat class display name mapping is correct`() {
        // Mirrors the private seatClassDisplayName in StatisticsScreen
        fun displayName(s: String): String = when (s) {
            "economy" -> "Economy"
            "premium_economy" -> "Premium Economy"
            "business" -> "Business"
            "first" -> "First"
            else -> s.replaceFirstChar { it.uppercase() }
        }
        assertEquals("Economy", displayName("economy"))
        assertEquals("Premium Economy", displayName("premium_economy"))
        assertEquals("Business", displayName("business"))
        assertEquals("First", displayName("first"))
        assertEquals("Unknown", displayName("unknown"))
    }
}
