package com.flightlog.app.data

import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.trips.TripGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripGrouperTest {

    // Helpers to build test flights quickly
    private fun flight(
        id: Long,
        dep: String,
        arr: String,
        departureUtc: Long,
        arrivalUtc: Long? = null,
        distanceNm: Int? = null
    ) = LogbookFlight(
        id = id,
        departureCode = dep,
        arrivalCode = arr,
        departureTimeUtc = departureUtc,
        arrivalTimeUtc = arrivalUtc,
        distanceNm = distanceNm
    )

    private val H = 60L * 60 * 1000 // 1 hour in millis

    // =========================================================================
    // Edge case 1: 0 flights → empty list
    // =========================================================================

    @Test
    fun `empty list returns empty trip groups`() {
        val result = TripGrouper.group(emptyList())
        assertTrue(result.isEmpty())
    }

    // =========================================================================
    // Edge case 2: 1 flight → 1 trip with 1 leg, label = DEP → ARR
    // =========================================================================

    @Test
    fun `single flight produces one trip`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H, 5500)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertEquals("1", trips[0].id)
        assertEquals(1, trips[0].legs.size)
        assertEquals("NRT \u2192 ORD", trips[0].label)
        assertEquals(5500, trips[0].totalDistanceNm)
    }

    // =========================================================================
    // Edge case 3: 2 flights gap < 48h → grouped into 1 trip
    // =========================================================================

    @Test
    fun `two flights within 48h gap grouped into one trip`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),
            flight(2, "ORD", "LAX", 1036 * H, 1040 * H)  // 24h after arrival
        )
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertEquals(2, trips[0].legs.size)
        assertEquals("NRT \u2192 ORD \u2192 LAX", trips[0].label)
    }

    // =========================================================================
    // Edge case 4: 2 flights gap exactly 48h → boundary: new trip (> GAP_MS, not >=)
    // =========================================================================

    @Test
    fun `gap exactly 48h stays in same trip`() {
        val depTime2 = 1012 * H + 48 * H  // exactly 48h after arrival of flight 1
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),
            flight(2, "ORD", "LAX", depTime2, depTime2 + 4 * H)
        )
        val trips = TripGrouper.group(flights)
        // Gap == 48h is NOT > 48h, so same trip
        assertEquals(1, trips.size)
        assertEquals(2, trips[0].legs.size)
    }

    // =========================================================================
    // Edge case 5: 2 flights gap > 48h → 2 separate trips
    // =========================================================================

    @Test
    fun `gap over 48h splits into two trips`() {
        val depTime2 = 1012 * H + 48 * H + 1  // 48h + 1ms after arrival
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),
            flight(2, "LAX", "SFO", depTime2, depTime2 + 2 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(2, trips.size)
        assertEquals("NRT \u2192 ORD", trips[0].label)
        assertEquals("LAX \u2192 SFO", trips[1].label)
    }

    // =========================================================================
    // Edge case 6: Grouper sorts internally (unsorted input — architect override)
    // =========================================================================

    @Test
    fun `unsorted input is sorted before grouping`() {
        val flights = listOf(
            flight(3, "LAX", "SFO", 1060 * H, 1062 * H),  // last chronologically
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),  // first
            flight(2, "ORD", "LAX", 1036 * H, 1040 * H)   // middle
        )
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertEquals("NRT \u2192 ORD \u2192 LAX \u2192 SFO", trips[0].label)
        // Verify legs are in chronological order
        assertEquals(1, trips[0].legs[0].id)
        assertEquals(2, trips[0].legs[1].id)
        assertEquals(3, trips[0].legs[2].id)
    }

    // =========================================================================
    // Edge case 7: LONGEST_DISTANCE sort active — irrelevant to grouper
    // (grouper always sorts ascending; display order handled by ViewModel)
    // =========================================================================

    @Test
    fun `grouper output is always in chronological order`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H, 5500),
            flight(2, "SFO", "LAX", 2000 * H, 2002 * H, 300)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(2, trips.size)
        // First trip should be the earlier one regardless of distance
        assertEquals("1", trips[0].id)
        assertEquals("2", trips[1].id)
    }

    // =========================================================================
    // Edge case 8: Flight with null arrivalTimeUtc → gap from departure
    // =========================================================================

    @Test
    fun `null arrivalTime uses departureTime for gap calculation`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, arrivalUtc = null),  // no arrival
            flight(2, "ORD", "LAX", 1000 * H + 24 * H)  // 24h after dep of flight 1
        )
        val trips = TripGrouper.group(flights)
        // Gap = 24h (from dep to dep since arr is null), < 48h → same trip
        assertEquals(1, trips.size)
    }

    @Test
    fun `null arrivalTime with gap over 48h from departure splits trips`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, arrivalUtc = null),
            flight(2, "LAX", "SFO", 1000 * H + 49 * H)  // 49h after dep
        )
        val trips = TripGrouper.group(flights)
        assertEquals(2, trips.size)
    }

    // =========================================================================
    // Edge case 9: Multi-leg: ORD→LAX, LAX→ORD — non-consecutive repeats kept
    // =========================================================================

    @Test
    fun `round trip keeps non-consecutive duplicates`() {
        val flights = listOf(
            flight(1, "ORD", "LAX", 1000 * H, 1004 * H),
            flight(2, "LAX", "ORD", 1024 * H, 1028 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertEquals("ORD \u2192 LAX \u2192 ORD", trips[0].label)
    }

    // =========================================================================
    // Edge case 10: All flights in one trip
    // =========================================================================

    @Test
    fun `all flights in one trip when gaps are small`() {
        val flights = (1L..10L).map { i ->
            flight(i, "A${i}", "A${i + 1}", 1000 * H + i * 12 * H, 1000 * H + i * 12 * H + 4 * H)
        }
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertEquals(10, trips[0].legs.size)
    }

    // =========================================================================
    // Edge case 11: filterState.isActive — tested at ViewModel level, not grouper
    // (grouper is a pure function that doesn't know about filters)
    // =========================================================================

    // =========================================================================
    // Edge case 12: Collapsed state preserved when new flight added
    // (tested at ViewModel level — TripGroup.id is stable based on first flight ID)
    // =========================================================================

    @Test
    fun `trip id is stable based on first flight id`() {
        val flights = listOf(
            flight(5, "NRT", "ORD", 1000 * H, 1012 * H),
            flight(6, "ORD", "LAX", 1036 * H, 1040 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals("5", trips[0].id)
    }

    // =========================================================================
    // Edge case 13: City name lookup returns null → IATA code in label
    // (TripGrouper uses IATA codes directly; city names would be resolved
    //  at a higher layer if needed)
    // =========================================================================

    @Test
    fun `label uses airport codes directly`() {
        val flights = listOf(
            flight(1, "ZZZ", "YYY", 1000 * H, 1004 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals("ZZZ \u2192 YYY", trips[0].label)
    }

    // =========================================================================
    // Edge case 14: Total duration null when any leg missing arrivalTimeUtc
    // =========================================================================

    @Test
    fun `total duration null when one leg has null arrival`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H, 5500),
            flight(2, "ORD", "LAX", 1036 * H, arrivalUtc = null, distanceNm = 1500)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(1, trips.size)
        assertNull(trips[0].totalDurationMin)
        // Distance should still be summed
        assertEquals(7000, trips[0].totalDistanceNm)
    }

    @Test
    fun `total duration computed when all legs have arrival`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),    // 12h = 720 min
            flight(2, "ORD", "LAX", 1036 * H, 1040 * H)      // 4h = 240 min
        )
        val trips = TripGrouper.group(flights)
        assertEquals(960L, trips[0].totalDurationMin)  // 720 + 240
    }

    // =========================================================================
    // buildRouteLabel — additional edge cases
    // =========================================================================

    @Test
    fun `consecutive duplicate airports are deduplicated`() {
        // NRT→ORD, ORD→ORD (rare same-airport turnaround) → "NRT → ORD"
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H),
            flight(2, "ORD", "ORD", 1036 * H, 1040 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals("NRT \u2192 ORD", trips[0].label)
    }

    @Test
    fun `blank airport codes are skipped in label`() {
        val flights = listOf(
            flight(1, "", "ORD", 1000 * H, 1012 * H),
            flight(2, "ORD", "", 1036 * H, 1040 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals("ORD", trips[0].label)
    }

    // =========================================================================
    // Date range formatting
    // =========================================================================

    @Test
    fun `single day trip shows one date`() {
        // Both flights on same UTC day
        val base = 500000 * H  // some large offset
        val flights = listOf(
            flight(1, "NRT", "ORD", base, base + 4 * H)
        )
        val trips = TripGrouper.group(flights)
        // Should be a single date like "Mar 20, 2026" — just verify not a range
        assertTrue(!trips[0].dateRange.contains("\u2013"))
    }

    @Test
    fun `multi-day trip shows date range`() {
        val base = 500000 * H
        val flights = listOf(
            flight(1, "NRT", "ORD", base, base + 12 * H),
            flight(2, "ORD", "LAX", base + 36 * H, base + 40 * H)
        )
        val trips = TripGrouper.group(flights)
        // Should contain an en-dash for range
        assertTrue(trips[0].dateRange.contains("\u2013"))
    }

    // =========================================================================
    // Distance aggregation
    // =========================================================================

    @Test
    fun `total distance sums non-null distances only`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H, 5500),
            flight(2, "ORD", "LAX", 1036 * H, 1040 * H, distanceNm = null),
            flight(3, "LAX", "SFO", 1060 * H, 1062 * H, 300)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(5800, trips[0].totalDistanceNm)
    }

    @Test
    fun `zero distance flights contribute zero`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 1000 * H, 1012 * H, 0)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(0, trips[0].totalDistanceNm)
    }

    // =========================================================================
    // Three separate trips
    // =========================================================================

    @Test
    fun `three distinct trips are separated correctly`() {
        val flights = listOf(
            flight(1, "NRT", "ORD", 100 * H, 112 * H),
            flight(2, "ORD", "LAX", 136 * H, 140 * H),
            // 100h gap
            flight(3, "LAX", "SFO", 300 * H, 302 * H),
            // 100h gap
            flight(4, "SFO", "SEA", 500 * H, 502 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(3, trips.size)
        assertEquals(2, trips[0].legs.size)
        assertEquals(1, trips[1].legs.size)
        assertEquals(1, trips[2].legs.size)
    }

    // =========================================================================
    // Deliberately unsorted input (architect override — must test)
    // =========================================================================

    @Test
    fun `reverse-sorted input produces correct groups`() {
        val flights = listOf(
            flight(4, "SFO", "SEA", 500 * H, 502 * H),
            flight(3, "LAX", "SFO", 300 * H, 302 * H),
            flight(2, "ORD", "LAX", 136 * H, 140 * H),
            flight(1, "NRT", "ORD", 100 * H, 112 * H)
        )
        val trips = TripGrouper.group(flights)
        assertEquals(3, trips.size)
        // First trip should be NRT→ORD→LAX (chronologically first)
        assertEquals("NRT \u2192 ORD \u2192 LAX", trips[0].label)
        assertEquals("LAX \u2192 SFO", trips[1].label)
        assertEquals("SFO \u2192 SEA", trips[2].label)
    }

    @Test
    fun `randomly shuffled input produces same result as sorted`() {
        val sorted = listOf(
            flight(1, "A", "B", 100 * H, 110 * H),
            flight(2, "B", "C", 120 * H, 130 * H),
            flight(3, "D", "E", 300 * H, 310 * H)
        )
        val shuffled = listOf(sorted[2], sorted[0], sorted[1])
        val tripsFromSorted = TripGrouper.group(sorted)
        val tripsFromShuffled = TripGrouper.group(shuffled)
        assertEquals(tripsFromSorted.size, tripsFromShuffled.size)
        for (i in tripsFromSorted.indices) {
            assertEquals(tripsFromSorted[i].label, tripsFromShuffled[i].label)
            assertEquals(tripsFromSorted[i].legs.map { it.id }, tripsFromShuffled[i].legs.map { it.id })
        }
    }
}
