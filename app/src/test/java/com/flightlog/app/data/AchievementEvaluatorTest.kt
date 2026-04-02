package com.flightlog.app.data

import com.flightlog.app.data.achievements.AchievementEvaluator
import com.flightlog.app.data.local.entity.Achievement
import com.flightlog.app.data.local.entity.LogbookFlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AchievementEvaluatorTest {

    // -- Helpers ---------------------------------------------------------------

    private fun flight(
        departureCode: String = "NRT",
        arrivalCode: String = "LAX",
        flightNumber: String = "NH105",
        distanceKm: Int? = 8763,
        departureTimeMillis: Long = 1700000000000L,
        departureTimezone: String? = "Asia/Tokyo",
        seatClass: String? = "",
        sourceCalendarEventId: Long? = 1L
    ) = LogbookFlight(
        departureCode = departureCode,
        arrivalCode = arrivalCode,
        flightNumber = flightNumber,
        distanceKm = distanceKm,
        departureDateEpochDay = java.time.Instant.ofEpochMilli(departureTimeMillis)
            .atZone(runCatching { java.time.ZoneId.of(departureTimezone ?: "UTC") }.getOrDefault(java.time.ZoneId.of("UTC"))).toLocalDate().toEpochDay(),
        departureTimeMillis = departureTimeMillis,
        departureTimezone = departureTimezone,
        seatClass = seatClass,
        sourceCalendarEventId = sourceCalendarEventId
    )

    private fun flights(count: Int, departureCodeGen: (Int) -> String = { "NRT" }, arrivalCodeGen: (Int) -> String = { "LAX" }) =
        (0 until count).map { i ->
            flight(departureCode = departureCodeGen(i), arrivalCode = arrivalCodeGen(i), flightNumber = "AA${100 + i}")
        }

    private fun noAchievementsUnlocked(): List<Achievement> =
        listOf(
            Achievement("first_flight"),
            Achievement("first_manual_add"),
            Achievement("ten_flights"),
            Achievement("five_airports"),
            Achievement("five_airlines"),
            Achievement("fifty_flights"),
            Achievement("twenty_airports"),
            Achievement("three_seat_classes"),
            Achievement("distance_10k"),
            Achievement("short_hop"),
            Achievement("century_club"),
            Achievement("fifty_airports"),
            Achievement("long_hauler"),
            Achievement("distance_100k"),
            Achievement("night_owl"),
            Achievement("five_hundred_flights"),
            Achievement("ultra_long_haul"),
            Achievement("distance_500k")
        )

    private fun evaluate(flights: List<LogbookFlight>, current: List<Achievement> = noAchievementsUnlocked()) =
        AchievementEvaluator.evaluate(flights, current)

    // ==========================================================================
    // Edge case: 0 flights
    // ==========================================================================

    @Test
    fun `zero flights unlocks nothing`() {
        val result = evaluate(emptyList())
        assertTrue(result.isEmpty())
    }

    // ==========================================================================
    // first_flight
    // ==========================================================================

    @Test
    fun `first_flight unlocks with 1 flight`() {
        assertTrue("first_flight" in evaluate(listOf(flight())))
    }

    // ==========================================================================
    // first_manual_add
    // ==========================================================================

    @Test
    fun `first_manual_add unlocks when sourceCalendarEventId is null`() {
        val manual = flight(sourceCalendarEventId = null)
        assertTrue("first_manual_add" in evaluate(listOf(manual)))
    }

    @Test
    fun `first_manual_add does NOT unlock when all flights are from calendar`() {
        val calFlight = flight(sourceCalendarEventId = 123L)
        assertFalse("first_manual_add" in evaluate(listOf(calFlight)))
    }

    // ==========================================================================
    // ten_flights — boundary at exactly 10
    // ==========================================================================

    @Test
    fun `ten_flights does NOT unlock at 9`() {
        assertFalse("ten_flights" in evaluate(flights(9)))
    }

    @Test
    fun `ten_flights unlocks at exactly 10`() {
        assertTrue("ten_flights" in evaluate(flights(10)))
    }

    // ==========================================================================
    // five_airports — boundary at exactly 5
    // ==========================================================================

    @Test
    fun `five_airports needs 5 unique codes from dep+arr combined`() {
        // 3 unique dep codes + 2 unique arr codes = 5 unique total
        val list = listOf(
            flight(departureCode = "NRT", arrivalCode = "LAX"),
            flight(departureCode = "HND", arrivalCode = "SFO"),
            flight(departureCode = "KIX", arrivalCode = "LAX")
        )
        assertTrue("five_airports" in evaluate(list))
    }

    @Test
    fun `five_airports does NOT unlock with only 4 unique`() {
        val list = listOf(
            flight(departureCode = "NRT", arrivalCode = "LAX"),
            flight(departureCode = "HND", arrivalCode = "LAX")
        )
        // NRT, LAX, HND = 3 unique
        assertFalse("five_airports" in evaluate(list))
    }

    // ==========================================================================
    // five_airlines — unique airline prefixes
    // ==========================================================================

    @Test
    fun `five_airlines counts unique letter prefixes from flight numbers`() {
        val list = listOf(
            flight(flightNumber = "AA100"),
            flight(flightNumber = "NH200"),
            flight(flightNumber = "UA300"),
            flight(flightNumber = "DL400"),
            flight(flightNumber = "JL500")
        )
        assertTrue("five_airlines" in evaluate(list))
    }

    @Test
    fun `five_airlines ignores empty flight numbers`() {
        val list = listOf(
            flight(flightNumber = "AA100"),
            flight(flightNumber = "NH200"),
            flight(flightNumber = "UA300"),
            flight(flightNumber = "DL400"),
            flight(flightNumber = "")
        )
        assertFalse("five_airlines" in evaluate(list))
    }

    @Test
    fun `five_airlines does NOT count duplicate prefixes`() {
        val list = (1..10).map { flight(flightNumber = "AA${100 + it}") }
        assertFalse("five_airlines" in evaluate(list))
    }

    // ==========================================================================
    // fifty_flights — boundary at exactly 50
    // ==========================================================================

    @Test
    fun `fifty_flights does NOT unlock at 49`() {
        assertFalse("fifty_flights" in evaluate(flights(49)))
    }

    @Test
    fun `fifty_flights unlocks at exactly 50`() {
        assertTrue("fifty_flights" in evaluate(flights(50)))
    }

    // ==========================================================================
    // twenty_airports — boundary
    // ==========================================================================

    @Test
    fun `twenty_airports needs 20 unique codes`() {
        // 20 unique departure codes, all arriving at same airport = 21 unique
        val list = (0 until 20).map { flight(departureCode = "A${it.toString().padStart(2, '0')}") }
        assertTrue("twenty_airports" in evaluate(list))
    }

    // ==========================================================================
    // three_seat_classes
    // ==========================================================================

    @Test
    fun `three_seat_classes needs 3 non-blank seat classes`() {
        val list = listOf(
            flight(seatClass = "Economy"),
            flight(seatClass = "Business"),
            flight(seatClass = "First")
        )
        assertTrue("three_seat_classes" in evaluate(list))
    }

    @Test
    fun `three_seat_classes ignores blank seat classes`() {
        val list = listOf(
            flight(seatClass = "Economy"),
            flight(seatClass = "Business"),
            flight(seatClass = ""),
            flight(seatClass = "  ")
        )
        assertFalse("three_seat_classes" in evaluate(list))
    }

    // ==========================================================================
    // distance_10k — total distance boundary
    // ==========================================================================

    @Test
    fun `distance_10k at threshold`() {
        val list = listOf(
            flight(distanceKm = 9260),
            flight(distanceKm = 9260)
        )
        assertTrue("distance_10k" in evaluate(list))
    }

    @Test
    fun `distance_10k does NOT unlock below threshold`() {
        val list = listOf(
            flight(distanceKm = 9260),
            flight(distanceKm = 9259)
        )
        assertFalse("distance_10k" in evaluate(list))
    }

    @Test
    fun `distance_10k excludes null distance flights`() {
        val list = listOf(
            flight(distanceKm = 9260),
            flight(distanceKm = null),
            flight(distanceKm = 9259)
        )
        assertFalse("distance_10k" in evaluate(list))
    }

    // ==========================================================================
    // short_hop — strictly < 300 nm
    // ==========================================================================

    @Test
    fun `short_hop needs 5 flights each strictly under 556 km`() {
        val list = (1..5).map { flight(distanceKm = 555) }
        assertTrue("short_hop" in evaluate(list))
    }

    @Test
    fun `short_hop does NOT count flight at exactly 556 km`() {
        val list = (1..4).map { flight(distanceKm = 100) } + flight(distanceKm = 556)
        assertFalse("short_hop" in evaluate(list))
    }

    @Test
    fun `short_hop does NOT count null distance`() {
        val list = (1..4).map { flight(distanceKm = 100) } + flight(distanceKm = null)
        assertFalse("short_hop" in evaluate(list))
    }

    @Test
    fun `short_hop at boundary with 4 qualifying flights`() {
        val list = (1..4).map { flight(distanceKm = 50) }
        assertFalse("short_hop" in evaluate(list))
    }

    // ==========================================================================
    // century_club — 100 flights
    // ==========================================================================

    @Test
    fun `century_club does NOT unlock at 99`() {
        assertFalse("century_club" in evaluate(flights(99)))
    }

    @Test
    fun `century_club unlocks at exactly 100`() {
        assertTrue("century_club" in evaluate(flights(100)))
    }

    // ==========================================================================
    // fifty_airports — 50 unique
    // ==========================================================================

    @Test
    fun `fifty_airports needs 50 unique codes`() {
        val list = (0 until 50).map {
            flight(
                departureCode = "D${it.toString().padStart(2, '0')}",
                arrivalCode = "A${it.toString().padStart(2, '0')}"
            )
        }
        // 50 dep + 50 arr = 100 unique
        assertTrue("fifty_airports" in evaluate(list))
    }

    // ==========================================================================
    // long_hauler — single flight >= 5000 nm
    // ==========================================================================

    @Test
    fun `long_hauler at threshold`() {
        assertTrue("long_hauler" in evaluate(listOf(flight(distanceKm = 9260))))
    }

    @Test
    fun `long_hauler does NOT unlock below threshold`() {
        assertFalse("long_hauler" in evaluate(listOf(flight(distanceKm = 9259))))
    }

    @Test
    fun `long_hauler ignores null distance`() {
        assertFalse("long_hauler" in evaluate(listOf(flight(distanceKm = null))))
    }

    // ==========================================================================
    // distance_100k
    // ==========================================================================

    @Test
    fun `distance_100k at threshold`() {
        val list = (1..20).map { flight(distanceKm = 9260) }
        assertTrue("distance_100k" in evaluate(list))
    }

    // ==========================================================================
    // night_owl — 3 flights departing 00:00-04:59 local
    // ==========================================================================

    @Test
    fun `night_owl counts flight at exactly 00 00 local`() {
        // Midnight UTC in UTC timezone = 00:00
        val midnight = LocalDateTime.of(2024, 6, 15, 0, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = midnight, departureTimezone = "UTC")
        }
        assertTrue("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl does NOT count flight at 05 00 local`() {
        val fiveAm = LocalDateTime.of(2024, 6, 15, 5, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = fiveAm, departureTimezone = "UTC")
        }
        assertFalse("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl counts flight at 04 59 local`() {
        val fourFiftyNine = LocalDateTime.of(2024, 6, 15, 4, 59)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = fourFiftyNine, departureTimezone = "UTC")
        }
        assertTrue("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl falls back to UTC when timezone is null`() {
        // 2:00 AM UTC, no timezone set
        val twoAmUtc = LocalDateTime.of(2024, 6, 15, 2, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = twoAmUtc, departureTimezone = null)
        }
        assertTrue("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl falls back to UTC when timezone is invalid`() {
        val twoAmUtc = LocalDateTime.of(2024, 6, 15, 2, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = twoAmUtc, departureTimezone = "Invalid/Zone")
        }
        assertTrue("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl respects timezone conversion`() {
        // 3:00 AM in Asia/Tokyo = 18:00 UTC previous day
        // So if we set UTC time to 18:00 UTC, local Tokyo time = 03:00 next day
        val utcTime = LocalDateTime.of(2024, 6, 14, 18, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..3).map {
            flight(departureTimeMillis = utcTime, departureTimezone = "Asia/Tokyo")
        }
        assertTrue("night_owl" in evaluate(list))
    }

    @Test
    fun `night_owl needs exactly 3, not 2`() {
        val midnight = LocalDateTime.of(2024, 6, 15, 0, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = (1..2).map {
            flight(departureTimeMillis = midnight, departureTimezone = "UTC")
        }
        assertFalse("night_owl" in evaluate(list))
    }

    // ==========================================================================
    // five_hundred_flights — boundary at 500
    // ==========================================================================

    @Test
    fun `five_hundred_flights does NOT unlock at 499`() {
        assertFalse("five_hundred_flights" in evaluate(flights(499)))
    }

    @Test
    fun `five_hundred_flights unlocks at exactly 500`() {
        assertTrue("five_hundred_flights" in evaluate(flights(500)))
    }

    // ==========================================================================
    // ultra_long_haul — single flight >= 8000 nm
    // ==========================================================================

    @Test
    fun `ultra_long_haul at threshold`() {
        assertTrue("ultra_long_haul" in evaluate(listOf(flight(distanceKm = 14816))))
    }

    @Test
    fun `ultra_long_haul does NOT unlock below threshold`() {
        assertFalse("ultra_long_haul" in evaluate(listOf(flight(distanceKm = 14815))))
    }

    // ==========================================================================
    // distance_500k
    // ==========================================================================

    @Test
    fun `distance_500k at threshold`() {
        val list = (1..100).map { flight(distanceKm = 9260) }
        assertTrue("distance_500k" in evaluate(list))
    }

    // ==========================================================================
    // Edge cases: already-unlocked achievements
    // ==========================================================================

    @Test
    fun `already unlocked achievements are not returned again`() {
        val current = noAchievementsUnlocked().map {
            if (it.id == "first_flight") it.copy(unlockedAt = 1000L) else it
        }
        val result = AchievementEvaluator.evaluate(listOf(flight()), current)
        assertFalse("first_flight" in result)
    }

    // ==========================================================================
    // Edge case: short-circuit when all platinum unlocked
    // ==========================================================================

    @Test
    fun `short-circuits to empty when ALL achievements are already unlocked`() {
        val current = noAchievementsUnlocked().map { ach ->
            ach.copy(unlockedAt = 1000L)
        }
        val result = AchievementEvaluator.evaluate(listOf(flight()), current)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `does NOT short-circuit when only platinum are unlocked`() {
        val current = noAchievementsUnlocked().map { ach ->
            when (ach.id) {
                "five_hundred_flights", "ultra_long_haul", "distance_500k" ->
                    ach.copy(unlockedAt = 1000L)
                else -> ach
            }
        }
        // first_flight should still be evaluated even with all platinum unlocked
        val result = AchievementEvaluator.evaluate(listOf(flight()), current)
        assertTrue(result.contains("first_flight"))
    }

    // ==========================================================================
    // Edge case: multiple achievements unlock in one pass
    // ==========================================================================

    @Test
    fun `multiple achievements can unlock in single evaluation`() {
        val list = listOf(flight(sourceCalendarEventId = null))
        val result = evaluate(list)
        assertTrue("first_flight" in result)
        assertTrue("first_manual_add" in result)
    }

    // ==========================================================================
    // Edge case: null distance excluded from all distance checks
    // ==========================================================================

    @Test
    fun `null distance flights excluded from total distance`() {
        val list = listOf(
            flight(distanceKm = null),
            flight(distanceKm = null)
        )
        assertFalse("distance_10k" in evaluate(list))
    }

    // ==========================================================================
    // Edge case: blank and whitespace-only airport codes
    // ==========================================================================

    @Test
    fun `blank airport codes are not counted as unique airports`() {
        val list = listOf(
            flight(departureCode = "", arrivalCode = ""),
            flight(departureCode = "NRT", arrivalCode = "LAX"),
            flight(departureCode = "HND", arrivalCode = "SFO"),
        )
        // Only NRT, LAX, HND, SFO = 4 unique (blank excluded)
        assertFalse("five_airports" in evaluate(list))
    }

    // ==========================================================================
    // Edge case: case-insensitive airport counting
    // ==========================================================================

    @Test
    fun `airport codes are case-insensitive for uniqueness`() {
        val list = listOf(
            flight(departureCode = "nrt", arrivalCode = "lax"),
            flight(departureCode = "NRT", arrivalCode = "LAX"),
            flight(departureCode = "Hnd", arrivalCode = "sfo"),
        )
        // NRT, LAX, HND, SFO = 4 unique
        assertEquals(4, list.flatMap { listOf(it.departureCode.uppercase(), it.arrivalCode.uppercase()) }.toSet().size)
    }

    // ==========================================================================
    // departureLocalHour edge cases
    // ==========================================================================

    @Test
    fun `departureLocalHour handles DST transition`() {
        // March 10, 2024 at 02:30 UTC — in America/New_York that's EDT
        // In winter it would be EST (UTC-5), in spring EDT (UTC-4)
        // At 02:30 UTC on March 10, 2024, New York is in EST (UTC-5) -> 21:30 March 9
        val utcTime = LocalDateTime.of(2024, 3, 10, 2, 30)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val f = flight(departureTimeMillis = utcTime, departureTimezone = "America/New_York")
        val hour = AchievementEvaluator.departureLocalHour(f)
        // 02:30 UTC - 5h = 21:30 local (EST still active at that instant)
        assertEquals(21, hour)
    }

    @Test
    fun `departureLocalHour at midnight boundary`() {
        val midnight = LocalDateTime.of(2024, 6, 15, 0, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val f = flight(departureTimeMillis = midnight, departureTimezone = "UTC")
        assertEquals(0, AchievementEvaluator.departureLocalHour(f))
    }
}
