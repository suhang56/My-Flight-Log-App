package com.flightlog.app.data.local.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.entity.LogbookFlight
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LogbookFlightDaoStatsTest {

    private lateinit var db: FlightDatabase
    private lateinit var dao: LogbookFlightDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.logbookFlightDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun logbookFlight(
        sourceCalendarEventId: Long? = 100L,
        sourceLegIndex: Int? = 0,
        flightNumber: String = "AA11",
        departureCode: String = "ORD",
        arrivalCode: String = "CMH",
        departureTimeUtc: Long = 1_700_000_000_000L,
        arrivalTimeUtc: Long? = 1_700_003_600_000L, // +1 hour
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

    // ── Empty table ──────────────────────────────────────────────────────────

    @Test
    fun `getCount on empty table returns 0`() = runTest {
        assertEquals(0, dao.getCount().first())
    }

    @Test
    fun `getTotalDistanceNm on empty table returns 0`() = runTest {
        assertEquals(0, dao.getTotalDistanceNm().first())
    }

    @Test
    fun `getTotalDurationMinutes on empty table returns null`() = runTest {
        assertNull(dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `getDistinctAirportCodes on empty table returns empty`() = runTest {
        assertTrue(dao.getDistinctAirportCodes().first().isEmpty())
    }

    @Test
    fun `getFlightsPerMonth on empty table returns empty`() = runTest {
        assertTrue(dao.getFlightsPerMonth().first().isEmpty())
    }

    @Test
    fun `getTopDepartureAirports on empty table returns empty`() = runTest {
        assertTrue(dao.getTopDepartureAirports().first().isEmpty())
    }

    @Test
    fun `getTopArrivalAirports on empty table returns empty`() = runTest {
        assertTrue(dao.getTopArrivalAirports().first().isEmpty())
    }

    @Test
    fun `getDistinctAirlinePrefixes on empty table returns empty`() = runTest {
        assertTrue(dao.getDistinctAirlinePrefixes().first().isEmpty())
    }

    @Test
    fun `getSeatClassBreakdown on empty table returns empty`() = runTest {
        assertTrue(dao.getSeatClassBreakdown().first().isEmpty())
    }

    @Test
    fun `getAircraftTypeDistribution on empty table returns empty`() = runTest {
        assertTrue(dao.getAircraftTypeDistribution().first().isEmpty())
    }

    @Test
    fun `getLongestFlightByDistance on empty table returns null`() = runTest {
        assertNull(dao.getLongestFlightByDistance().first())
    }

    // ── Single flight ────────────────────────────────────────────────────────

    @Test
    fun `single flight stats are correct`() = runTest {
        dao.insert(logbookFlight())

        assertEquals(1, dao.getCount().first())
        assertEquals(266, dao.getTotalDistanceNm().first())
        // 1 hour = 60 minutes
        assertEquals(60L, dao.getTotalDurationMinutes().first())
        // ORD + CMH = 2 unique airports
        assertEquals(2, dao.getDistinctAirportCodes().first().size)
    }

    @Test
    fun `single flight appears in departure airports`() = runTest {
        dao.insert(logbookFlight())
        val dep = dao.getTopDepartureAirports().first()
        assertEquals(1, dep.size)
        assertEquals("ORD", dep[0].code)
        assertEquals(1, dep[0].count)
    }

    @Test
    fun `single flight appears in arrival airports`() = runTest {
        dao.insert(logbookFlight())
        val arr = dao.getTopArrivalAirports().first()
        assertEquals(1, arr.size)
        assertEquals("CMH", arr[0].code)
        assertEquals(1, arr[0].count)
    }

    @Test
    fun `single flight is longest flight`() = runTest {
        dao.insert(logbookFlight())
        val longest = dao.getLongestFlightByDistance().first()
        assertNotNull(longest)
        assertEquals(266, longest!!.distanceNm)
    }

    // ── Null values ──────────────────────────────────────────────────────────

    @Test
    fun `flight with null arrivalTimeUtc excluded from duration sum`() = runTest {
        dao.insert(logbookFlight(arrivalTimeUtc = null))
        assertNull(dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `flight with null distanceNm excluded from distance sum`() = runTest {
        dao.insert(logbookFlight(distanceNm = null))
        assertEquals(0, dao.getTotalDistanceNm().first())
    }

    @Test
    fun `flight with null distanceNm excluded from longest flight`() = runTest {
        dao.insert(logbookFlight(distanceNm = null))
        assertNull(dao.getLongestFlightByDistance().first())
    }

    @Test
    fun `mix of null and non-null distances sums only non-null`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, distanceNm = 500))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, distanceNm = null))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, distanceNm = 300))

        assertEquals(800, dao.getTotalDistanceNm().first())
    }

    @Test
    fun `mix of null and non-null arrival times sums only non-null durations`() = runTest {
        val base = 1_700_000_000_000L
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = base, arrivalTimeUtc = base + 3_600_000)) // 60 min
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = base, arrivalTimeUtc = null))

        assertEquals(60L, dao.getTotalDurationMinutes().first())
    }

    // ── Empty strings in airport/airline codes ───────────────────────────────

    @Test
    fun `empty departureCode excluded from distinct airport codes`() = runTest {
        dao.insert(logbookFlight(departureCode = "", arrivalCode = "LAX"))
        val codes = dao.getDistinctAirportCodes().first()
        assertEquals(1, codes.size)
        assertEquals("LAX", codes[0])
    }

    @Test
    fun `empty arrivalCode excluded from distinct airport codes`() = runTest {
        dao.insert(logbookFlight(departureCode = "LAX", arrivalCode = ""))
        val codes = dao.getDistinctAirportCodes().first()
        assertEquals(1, codes.size)
    }

    @Test
    fun `both empty codes result in 0 distinct airports`() = runTest {
        dao.insert(logbookFlight(departureCode = "", arrivalCode = ""))
        assertTrue(dao.getDistinctAirportCodes().first().isEmpty())
    }

    @Test
    fun `empty departureCode excluded from top departure airports`() = runTest {
        dao.insert(logbookFlight(departureCode = ""))
        assertTrue(dao.getTopDepartureAirports().first().isEmpty())
    }

    @Test
    fun `empty arrivalCode excluded from top arrival airports`() = runTest {
        dao.insert(logbookFlight(arrivalCode = ""))
        assertTrue(dao.getTopArrivalAirports().first().isEmpty())
    }

    @Test
    fun `empty flightNumber excluded from airline prefixes`() = runTest {
        dao.insert(logbookFlight(flightNumber = ""))
        assertTrue(dao.getDistinctAirlinePrefixes().first().isEmpty())
    }

    @Test
    fun `single-char flightNumber excluded from airline prefixes`() = runTest {
        dao.insert(logbookFlight(flightNumber = "A"))
        assertTrue(dao.getDistinctAirlinePrefixes().first().isEmpty())
    }

    @Test
    fun `empty seatClass excluded from breakdown`() = runTest {
        dao.insert(logbookFlight(seatClass = ""))
        assertTrue(dao.getSeatClassBreakdown().first().isEmpty())
    }

    @Test
    fun `empty aircraftType excluded from distribution`() = runTest {
        dao.insert(logbookFlight(aircraftType = ""))
        assertTrue(dao.getAircraftTypeDistribution().first().isEmpty())
    }

    // ── Aggregate correctness ────────────────────────────────────────────────

    @Test
    fun `distinct airport codes counts each airport once even if departure and arrival`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureCode = "ORD", arrivalCode = "CMH"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureCode = "CMH", arrivalCode = "ORD"))
        assertEquals(2, dao.getDistinctAirportCodes().first().size)
    }

    @Test
    fun `top departure airports respects limit parameter`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureCode = "ORD"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, departureCode = "SFO"))
        dao.insert(logbookFlight(sourceCalendarEventId = 4, departureCode = "JFK"))

        val top2 = dao.getTopDepartureAirports(limit = 2).first()
        assertEquals(2, top2.size)
    }

    @Test
    fun `top arrival airports respects limit parameter`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, arrivalCode = "ORD"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, arrivalCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, arrivalCode = "SFO"))

        val top2 = dao.getTopArrivalAirports(limit = 2).first()
        assertEquals(2, top2.size)
    }

    @Test
    fun `airline prefixes extracts 2-letter prefix and uppercases`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, flightNumber = "aa100"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, flightNumber = "AA200"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, flightNumber = "NH50"))

        val airlines = dao.getDistinctAirlinePrefixes().first()
        val aaCount = airlines.find { it.airline == "AA" }!!.count
        assertEquals(2, aaCount)
        assertEquals(2, airlines.size) // AA and NH
    }

    @Test
    fun `airline prefixes ordered by count descending`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, flightNumber = "NH10"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, flightNumber = "AA100"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, flightNumber = "AA200"))

        val airlines = dao.getDistinctAirlinePrefixes().first()
        assertEquals("AA", airlines[0].airline)
        assertEquals(2, airlines[0].count)
        assertEquals("NH", airlines[1].airline)
        assertEquals(1, airlines[1].count)
    }

    @Test
    fun `flights per month groups by year-month`() = runTest {
        val jan15 = 1_736_899_200_000L // 2025-01-15 approx
        val feb15 = 1_739_577_600_000L // 2025-02-15 approx
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = jan15))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = jan15 + 86400000))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, departureTimeUtc = feb15))

        val monthly = dao.getFlightsPerMonth().first()
        assertEquals(2, monthly.size)
        assertEquals(2, monthly[0].count)
        assertEquals(1, monthly[1].count)
    }

    @Test
    fun `flights per month ordered chronologically`() = runTest {
        val mar = 1_741_000_000_000L // 2025-03 approx
        val jan = 1_736_000_000_000L // 2025-01 approx
        // Insert out of order
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = mar))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = jan))

        val monthly = dao.getFlightsPerMonth().first()
        assertTrue(monthly[0].yearMonth < monthly[1].yearMonth)
    }

    @Test
    fun `longest flight by distance returns flight with greatest distanceNm`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, distanceNm = 100))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, distanceNm = 9000))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, distanceNm = 500))

        val longest = dao.getLongestFlightByDistance().first()
        assertNotNull(longest)
        assertEquals(9000, longest!!.distanceNm)
    }

    @Test
    fun `seat class breakdown groups and counts`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, seatClass = "Economy"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, seatClass = "Economy"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, seatClass = "Business"))

        val dist = dao.getSeatClassBreakdown().first()
        assertEquals(2, dist.size)
        assertEquals("Economy", dist[0].label)
        assertEquals(2, dist[0].count)
        assertEquals("Business", dist[1].label)
        assertEquals(1, dist[1].count)
    }

    @Test
    fun `seat class breakdown ordered by count descending`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, seatClass = "First"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, seatClass = "Economy"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, seatClass = "Economy"))
        dao.insert(logbookFlight(sourceCalendarEventId = 4, seatClass = "Economy"))

        val dist = dao.getSeatClassBreakdown().first()
        assertEquals("Economy", dist[0].label)
        assertEquals(3, dist[0].count)
        assertEquals("First", dist[1].label)
    }

    @Test
    fun `aircraft type distribution groups and counts`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, aircraftType = "Boeing 737-800"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, aircraftType = "Boeing 737-800"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, aircraftType = "Airbus A320"))

        val dist = dao.getAircraftTypeDistribution().first()
        assertEquals(2, dist.size)
        assertEquals("Boeing 737-800", dist[0].label)
        assertEquals(2, dist[0].count)
    }

    // ── Flight time edge cases ───────────────────────────────────────────────

    @Test
    fun `total duration sums multiple flights correctly`() = runTest {
        val base = 1_700_000_000_000L
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureTimeUtc = base, arrivalTimeUtc = base + 3_600_000)) // 60 min
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureTimeUtc = base, arrivalTimeUtc = base + 7_200_000)) // 120 min

        assertEquals(180L, dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `very short flight duration rounds down to 0 minutes`() = runTest {
        val base = 1_700_000_000_000L
        // 30 seconds = 30000ms, 30000 / 60000 = 0 in integer division
        dao.insert(logbookFlight(departureTimeUtc = base, arrivalTimeUtc = base + 30_000))
        assertEquals(0L, dao.getTotalDurationMinutes().first())
    }

    // ── Delete affects stats ─────────────────────────────────────────────────

    @Test
    fun `deleting flight updates count and distance`() = runTest {
        val id = dao.insert(logbookFlight(distanceNm = 500))

        assertEquals(1, dao.getCount().first())
        assertEquals(500, dao.getTotalDistanceNm().first())

        dao.deleteById(id)

        assertEquals(0, dao.getCount().first())
        assertEquals(0, dao.getTotalDistanceNm().first())
    }

    @Test
    fun `deleting longest flight updates longest flight result`() = runTest {
        val id1 = dao.insert(logbookFlight(sourceCalendarEventId = 1, distanceNm = 9000))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, distanceNm = 500))

        assertEquals(9000, dao.getLongestFlightByDistance().first()!!.distanceNm)

        dao.deleteById(id1)

        assertEquals(500, dao.getLongestFlightByDistance().first()!!.distanceNm)
    }

    // ── Duplicate insert (IGNORE strategy) ───────────────────────────────────

    @Test
    fun `duplicate source insert returns -1 and does not change count`() = runTest {
        val id1 = dao.insert(logbookFlight())
        assertTrue(id1 > 0)

        val id2 = dao.insert(logbookFlight()) // same sourceCalendarEventId + sourceLegIndex
        assertEquals(-1, id2)
        assertEquals(1, dao.getCount().first())
    }

    // ── Departure airport ordering ───────────────────────────────────────────

    @Test
    fun `top departure airports ordered by count descending`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, departureCode = "LAX"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, departureCode = "ORD"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, departureCode = "ORD"))

        val dep = dao.getTopDepartureAirports().first()
        assertEquals("ORD", dep[0].code)
        assertEquals(2, dep[0].count)
        assertEquals("LAX", dep[1].code)
    }

    @Test
    fun `top arrival airports ordered by count descending`() = runTest {
        dao.insert(logbookFlight(sourceCalendarEventId = 1, arrivalCode = "SFO"))
        dao.insert(logbookFlight(sourceCalendarEventId = 2, arrivalCode = "CMH"))
        dao.insert(logbookFlight(sourceCalendarEventId = 3, arrivalCode = "CMH"))

        val arr = dao.getTopArrivalAirports().first()
        assertEquals("CMH", arr[0].code)
        assertEquals(2, arr[0].count)
    }
}
