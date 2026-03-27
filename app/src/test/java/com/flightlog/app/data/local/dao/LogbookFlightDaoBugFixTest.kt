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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-case tests for the three logbook bug fixes:
 * 1. Cross-midnight: negative durations excluded from getTotalDurationMinutes
 * 2. Upsert: preserves original ID on undo-delete
 * 3. General DAO correctness for the new guard clause
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LogbookFlightDaoBugFixTest {

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

    private fun logbookFlight(
        id: Long = 0,
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
        id = id,
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

    // ── Bug 1: Cross-midnight / negative duration guard ──────────────────────

    @Test
    fun `negative duration flight excluded from total duration`() = runTest {
        val base = 1_700_000_000_000L
        // arrivalTimeUtc BEFORE departureTimeUtc — bad data
        dao.insert(logbookFlight(
            departureTimeUtc = base,
            arrivalTimeUtc = base - 3_600_000 // -1 hour
        ))
        // Should return null (no valid durations), not -60
        assertNull(dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `arrival equals departure excluded from total duration`() = runTest {
        val base = 1_700_000_000_000L
        // arrival == departure — zero duration, but guard is strictly greater-than
        dao.insert(logbookFlight(
            departureTimeUtc = base,
            arrivalTimeUtc = base // 0 minutes
        ))
        assertNull(dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `negative duration excluded while valid flights still counted`() = runTest {
        val base = 1_700_000_000_000L
        // Valid: +2 hours = 120 min
        dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 7_200_000
        ))
        // Invalid: arrival before departure
        dao.insert(logbookFlight(
            sourceCalendarEventId = 2,
            departureTimeUtc = base,
            arrivalTimeUtc = base - 1_800_000 // -30 min
        ))
        // Valid: +1 hour = 60 min
        dao.insert(logbookFlight(
            sourceCalendarEventId = 3,
            departureTimeUtc = base,
            arrivalTimeUtc = base + 3_600_000
        ))

        // Should be 120 + 60 = 180, not 120 + (-30) + 60 = 150
        assertEquals(180L, dao.getTotalDurationMinutes().first())
    }

    @Test
    fun `all flights have negative durations returns null`() = runTest {
        val base = 1_700_000_000_000L
        dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            departureTimeUtc = base,
            arrivalTimeUtc = base - 1000
        ))
        dao.insert(logbookFlight(
            sourceCalendarEventId = 2,
            departureTimeUtc = base,
            arrivalTimeUtc = base - 5000
        ))
        assertNull(dao.getTotalDurationMinutes().first())
    }

    // ── Bug 3: Upsert preserves original ID ─────────────────────────────────

    @Test
    fun `upsert inserts new flight when id does not exist`() = runTest {
        val id = dao.upsert(logbookFlight(sourceCalendarEventId = 50))
        assertEquals(1, dao.getCount().first())
        val flight = dao.getById(id)
        assertNotNull(flight)
        assertEquals("AA11", flight!!.flightNumber)
    }

    @Test
    fun `upsert replaces existing flight preserving original id`() = runTest {
        // Insert original
        val originalId = dao.insert(logbookFlight(
            sourceCalendarEventId = 1,
            flightNumber = "AA11",
            notes = "original"
        ))

        // Delete it
        dao.deleteById(originalId)
        assertEquals(0, dao.getCount().first())

        // Upsert with the original ID to simulate undo-delete
        dao.upsert(logbookFlight(
            id = originalId,
            sourceCalendarEventId = 1,
            flightNumber = "AA11",
            notes = "original"
        ))

        assertEquals(1, dao.getCount().first())
        val restored = dao.getById(originalId)
        assertNotNull(restored)
        assertEquals(originalId, restored!!.id)
        assertEquals("original", restored.notes)
    }

    @Test
    fun `upsert with same source key replaces existing row`() = runTest {
        // Insert first version
        val id1 = dao.insert(logbookFlight(
            sourceCalendarEventId = 42,
            sourceLegIndex = 0,
            notes = "v1"
        ))

        // Upsert with same unique index (sourceCalendarEventId + sourceLegIndex)
        dao.upsert(logbookFlight(
            id = id1,
            sourceCalendarEventId = 42,
            sourceLegIndex = 0,
            notes = "v2"
        ))

        assertEquals(1, dao.getCount().first())
        val updated = dao.getById(id1)
        assertNotNull(updated)
        assertEquals("v2", updated!!.notes)
    }

    @Test
    fun `upsert for manual flight with null source fields`() = runTest {
        // Manual flights have null sourceCalendarEventId
        val id = dao.upsert(logbookFlight(
            sourceCalendarEventId = null,
            sourceLegIndex = null,
            notes = "manual flight"
        ))

        assertEquals(1, dao.getCount().first())
        val flight = dao.getById(id)
        assertNotNull(flight)
        assertNull(flight!!.sourceCalendarEventId)
        assertEquals("manual flight", flight.notes)
    }

    @Test
    fun `undo-delete simulation preserves all fields`() = runTest {
        // Simulate the full undo-delete flow: insert -> delete -> upsert
        val original = logbookFlight(
            sourceCalendarEventId = 99,
            sourceLegIndex = 0,
            flightNumber = "NH100",
            departureCode = "NRT",
            arrivalCode = "LAX",
            distanceNm = 4723,
            aircraftType = "Boeing 777-300ER",
            seatClass = "Business",
            seatNumber = "5A",
            notes = "Great flight"
        )
        val originalId = dao.insert(original)
        val savedFlight = dao.getById(originalId)!!

        // Delete
        dao.deleteById(originalId)
        assertEquals(0, dao.getCount().first())

        // Undo — upsert the saved copy (preserving original ID)
        dao.upsert(savedFlight)

        val restored = dao.getById(originalId)
        assertNotNull(restored)
        assertEquals(originalId, restored!!.id)
        assertEquals("NH100", restored.flightNumber)
        assertEquals("NRT", restored.departureCode)
        assertEquals("LAX", restored.arrivalCode)
        assertEquals(4723, restored.distanceNm)
        assertEquals("Boeing 777-300ER", restored.aircraftType)
        assertEquals("Business", restored.seatClass)
        assertEquals("5A", restored.seatNumber)
        assertEquals("Great flight", restored.notes)
        assertEquals(99L, restored.sourceCalendarEventId)
    }
}
