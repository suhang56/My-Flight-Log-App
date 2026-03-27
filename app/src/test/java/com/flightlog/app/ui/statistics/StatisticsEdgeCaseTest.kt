package com.flightlog.app.ui.statistics

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flightlog.app.data.calendar.AirlineIataMap
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.RouteCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge case tests for the 7 Statistics screen improvements.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StatisticsEdgeCaseTest {

    private lateinit var db: FlightDatabase
    private lateinit var dao: LogbookFlightDao
    private lateinit var airlineIataMap: AirlineIataMap

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.logbookFlightDao()
        airlineIataMap = AirlineIataMap()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun logbookFlight(
        sourceCalendarEventId: Long? = 100L,
        sourceLegIndex: Int? = 0,
        flightNumber: String = "AA11",
        departureCode: String = "ORD",
        arrivalCode: String = "CMH",
        departureTimeUtc: Long = 1_700_000_000_000L,
        arrivalTimeUtc: Long? = 1_700_003_600_000L,
        departureTimezone: String? = "America/Chicago",
        arrivalTimezone: String? = "America/New_York",
        distanceNm: Int? = 266,
        aircraftType: String = "",
        seatClass: String = "",
        seatNumber: String = "",
        notes: String = ""
    ) = LogbookFlight(
        sourceCalendarEventId = sourceCalendarEventId,
        sourceLegIndex = sourceLegIndex,
        flightNumber = flightNumber,
        departureCode = departureCode,
        arrivalCode = arrivalCode,
        departureTimeUtc = departureTimeUtc,
        arrivalTimeUtc = arrivalTimeUtc,
        departureTimezone = departureTimezone,
        arrivalTimezone = arrivalTimezone,
        distanceNm = distanceNm,
        aircraftType = aircraftType,
        seatClass = seatClass,
        seatNumber = seatNumber,
        notes = notes
    )

    // ── Improvement 1: Bar chart - all zeros ─────────────────────────────────

    @Test
    fun `bar chart data with all zero counts`() = runTest {
        // Empty table means no monthly data at all
        val monthly = dao.getFlightsPerMonth().first()
        assertTrue(monthly.isEmpty())
    }

    @Test
    fun `bar chart with single month of data`() = runTest {
        dao.insert(logbookFlight())
        val monthly = dao.getFlightsPerMonth().first()
        assertEquals(1, monthly.size)
        assertEquals(1, monthly[0].count)
    }

    // ── Improvement 2: Longest flight by duration ────────────────────────────

    @Test
    fun `longest flight by duration on empty table returns null`() = runTest {
        assertNull(dao.getLongestFlightByDuration().first())
    }

    @Test
    fun `longest flight by duration with null arrival times returns null`() = runTest {
        dao.insert(logbookFlight(arrivalTimeUtc = null))
        assertNull(dao.getLongestFlightByDuration().first())
    }

    @Test
    fun `longest flight by duration excludes arrival before departure`() = runTest {
        val base = 1_700_000_000_000L
        // arrival before departure — should be excluded
        dao.insert(logbookFlight(departureTimeUtc = base, arrivalTimeUtc = base - 1000))
        assertNull(dao.getLongestFlightByDuration().first())
    }

    @Test
    fun `longest flight by duration picks correct flight`() = runTest {
        val base = 1_700_000_000_000L
        dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 3_600_000 // 1h
        ))
        dao.insert(logbookFlight(
            sourceCalendarEventId = 2,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 50_400_000 // 14h
        ))
        dao.insert(logbookFlight(
            sourceCalendarEventId = 3,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 7_200_000 // 2h
        ))

        val longest = dao.getLongestFlightByDuration().first()
        assertNotNull(longest)
        val durationMs = longest!!.arrivalTimeUtc!! - longest.departureTimeUtc
        assertEquals(50_400_000L, durationMs)
    }

    @Test
    fun `longest flight by duration with equal departure and arrival returns null`() = runTest {
        val base = 1_700_000_000_000L
        dao.insert(logbookFlight(departureTimeUtc = base, arrivalTimeUtc = base))
        assertNull(dao.getLongestFlightByDuration().first())
    }

    // ── Improvement 3: Airline full name lookup ──────────────────────────────

    @Test
    fun `known IATA code returns full name`() {
        assertEquals("American Airlines", airlineIataMap.getFullName("AA"))
        assertEquals("ANA", airlineIataMap.getFullName("NH"))
        assertEquals("Japan Airlines", airlineIataMap.getFullName("JL"))
        assertEquals("Emirates", airlineIataMap.getFullName("EK"))
    }

    @Test
    fun `unknown IATA code falls back to uppercase code`() {
        assertEquals("XY", airlineIataMap.getFullName("xy"))
        assertEquals("ZZ", airlineIataMap.getFullName("ZZ"))
    }

    @Test
    fun `getFullName is case insensitive`() {
        assertEquals("American Airlines", airlineIataMap.getFullName("aa"))
        assertEquals("American Airlines", airlineIataMap.getFullName("Aa"))
        assertEquals("American Airlines", airlineIataMap.getFullName("AA"))
    }

    @Test
    fun `empty string IATA code returns empty string`() {
        assertEquals("", airlineIataMap.getFullName(""))
    }

    @Test
    fun `single character IATA code falls back`() {
        assertEquals("A", airlineIataMap.getFullName("a"))
    }

    // ── Improvement 4: Top routes ────────────────────────────────────────────

    @Test
    fun `top routes on empty table returns empty`() = runTest {
        assertTrue(dao.getTopRoutes().first().isEmpty())
    }

    @Test
    fun `top routes excludes flights with empty departure code`() = runTest {
        dao.insert(logbookFlight(departureCode = "", arrivalCode = "LAX"))
        assertTrue(dao.getTopRoutes().first().isEmpty())
    }

    @Test
    fun `top routes excludes flights with empty arrival code`() = runTest {
        dao.insert(logbookFlight(departureCode = "ORD", arrivalCode = ""))
        assertTrue(dao.getTopRoutes().first().isEmpty())
    }

    @Test
    fun `top routes excludes flights with both empty codes`() = runTest {
        dao.insert(logbookFlight(departureCode = "", arrivalCode = ""))
        assertTrue(dao.getTopRoutes().first().isEmpty())
    }

    @Test
    fun `top routes groups by route pair`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureCode = "ORD", arrivalCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureCode = "ORD", arrivalCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, departureCode = "LAX", arrivalCode = "ORD"))

        val routes = dao.getTopRoutes().first()
        assertEquals(2, routes.size)
        // ORD->LAX has count 2, LAX->ORD has count 1
        assertEquals("ORD", routes[0].departureCode)
        assertEquals("LAX", routes[0].arrivalCode)
        assertEquals(2, routes[0].count)
        assertEquals(1, routes[1].count)
    }

    @Test
    fun `top routes respects limit`() = runTest {
        // Insert 6 different routes
        for (i in 1..6) {
            dao.insert(logbookFlight(
                sourceCalendarEventId = i.toLong(),
                departureCode = "A$i",
                arrivalCode = "B$i"
            ))
        }
        val routes = dao.getTopRoutes(limit = 3).first()
        assertEquals(3, routes.size)
    }

    @Test
    fun `top routes treats direction as distinct`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureCode = "NRT", arrivalCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureCode = "LAX", arrivalCode = "NRT"))

        val routes = dao.getTopRoutes().first()
        assertEquals(2, routes.size)
    }

    // ── Improvement 5: All-time toggle (data-level tests) ────────────────────

    @Test
    fun `flights per month returns all months when data spans over 12 months`() = runTest {
        val jan2024 = 1_704_067_200_000L // 2024-01-01 UTC
        val dec2025 = 1_733_011_200_000L // 2025-12-01 UTC

        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = jan2024))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = dec2025))

        val monthly = dao.getFlightsPerMonth().first()
        assertEquals(2, monthly.size)
        assertTrue(monthly[0].yearMonth < monthly[1].yearMonth)
    }

    @Test
    fun `flights per month with single month data`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = 1_700_000_000_000L + 86400000))

        val monthly = dao.getFlightsPerMonth().first()
        assertEquals(1, monthly.size)
        assertEquals(2, monthly[0].count)
    }

    // ── Improvement 6: First flight ──────────────────────────────────────────

    @Test
    fun `first flight on empty table returns null`() = runTest {
        assertNull(dao.getFirstFlight().first())
    }

    @Test
    fun `first flight returns earliest by departure time`() = runTest {
        val earliest = 1_500_000_000_000L
        val middle = 1_600_000_000_000L
        val latest = 1_700_000_000_000L

        // Insert out of order
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = middle))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = latest))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, departureTimeUtc = earliest))

        val first = dao.getFirstFlight().first()
        assertNotNull(first)
        assertEquals(earliest, first!!.departureTimeUtc)
    }

    @Test
    fun `first flight with null timezone does not crash`() = runTest {
        dao.insert(logbookFlight(departureTimezone = null, arrivalTimezone = null))
        val first = dao.getFirstFlight().first()
        assertNotNull(first)
        assertNull(first!!.departureTimezone)
    }

    @Test
    fun `first flight with invalid timezone falls back gracefully`() = runTest {
        dao.insert(logbookFlight(departureTimezone = "Invalid/Timezone"))
        val first = dao.getFirstFlight().first()
        assertNotNull(first)
        assertEquals("Invalid/Timezone", first!!.departureTimezone)
        // The UI code uses runCatching to handle this — testing data layer here
    }

    // ── Improvement 7: Seat class % rounding ─────────────────────────────────

    @Test
    fun `seat class rounding sums to exactly 100 with 3 equal items`() {
        // 3 items each with count 1 -> 33.33% each. Largest remainder: 34+33+33 = 100
        val data = listOf(
            LabelCount("Economy", 1),
            LabelCount("Business", 1),
            LabelCount("First", 1)
        )
        val total = data.sumOf { it.count }.coerceAtLeast(1)
        val pcts = largestRemainderMethod(data.map { it.count }, total)
        assertEquals(100, pcts.sum())
    }

    @Test
    fun `seat class rounding sums to exactly 100 with uneven split`() {
        // 7 items with count 1 each -> 14.28% each. 7*14=98, deficit=2
        val data = (1..7).map { LabelCount("Class$it", 1) }
        val total = data.sumOf { it.count }
        val pcts = largestRemainderMethod(data.map { it.count }, total)
        assertEquals(100, pcts.sum())
        // Each should be 14 or 15
        assertTrue(pcts.all { it in 14..15 })
    }

    @Test
    fun `seat class rounding with single item gives 100 percent`() {
        val data = listOf(LabelCount("Economy", 5))
        val total = 5
        val pcts = largestRemainderMethod(data.map { it.count }, total)
        assertEquals(listOf(100), pcts)
    }

    @Test
    fun `seat class rounding with two items 2 and 1`() {
        // 2/3 = 66.67%, 1/3 = 33.33% -> should be 67+33=100
        val pcts = largestRemainderMethod(listOf(2, 1), 3)
        assertEquals(100, pcts.sum())
        assertEquals(67, pcts[0])
        assertEquals(33, pcts[1])
    }

    @Test
    fun `seat class rounding with heavily skewed distribution`() {
        // 99 + 1 = 100 total -> 99% and 1%
        val pcts = largestRemainderMethod(listOf(99, 1), 100)
        assertEquals(100, pcts.sum())
        assertEquals(99, pcts[0])
        assertEquals(1, pcts[1])
    }

    @Test
    fun `seat class rounding with all equal counts`() {
        // 4 items with count 1: 25% each -> exactly 100
        val pcts = largestRemainderMethod(listOf(1, 1, 1, 1), 4)
        assertEquals(100, pcts.sum())
        assertTrue(pcts.all { it == 25 })
    }

    /**
     * Pure implementation of the Largest Remainder Method, extracted
     * to be testable outside of Compose.
     */
    private fun largestRemainderMethod(counts: List<Int>, total: Int): List<Int> {
        val rawPcts = counts.map { (it * 100.0) / total }
        val floored = rawPcts.map { it.toInt() }
        val remainders = rawPcts.mapIndexed { i, raw -> raw - floored[i] }
        val deficit = 100 - floored.sum()
        val sortedIndices = remainders.indices.sortedByDescending { remainders[it] }
        val result = floored.toMutableList()
        sortedIndices.take(deficit).forEach { result[it]++ }
        return result
    }

    // ── Cross-cutting: delete updates new stats ──────────────────────────────

    @Test
    fun `deleting only flight clears first flight`() = runTest {
        val id = dao.insert(logbookFlight())
        assertNotNull(dao.getFirstFlight().first())

        dao.deleteById(id)
        assertNull(dao.getFirstFlight().first())
    }

    @Test
    fun `deleting longest duration flight updates result`() = runTest {
        val base = 1_700_000_000_000L
        val id1 = dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 50_400_000 // 14h
        ))
        dao.insert(logbookFlight(
            sourceCalendarEventId = 2,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 3_600_000 // 1h
        ))

        assertEquals(50_400_000L, dao.getLongestFlightByDuration().first()!!.let {
            it.arrivalTimeUtc!! - it.departureTimeUtc
        })

        dao.deleteById(id1)

        assertEquals(3_600_000L, dao.getLongestFlightByDuration().first()!!.let {
            it.arrivalTimeUtc!! - it.departureTimeUtc
        })
    }

    @Test
    fun `deleting route flight updates top routes`() = runTest {
        val id1 = dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            departureCode = "ORD",
            arrivalCode = "LAX"
        ))
        dao.insert(logbookFlight(
            sourceCalendarEventId = 2,
            departureCode = "ORD",
            arrivalCode = "LAX"
        ))

        assertEquals(2, dao.getTopRoutes().first()[0].count)

        dao.deleteById(id1)

        assertEquals(1, dao.getTopRoutes().first()[0].count)
    }
}
