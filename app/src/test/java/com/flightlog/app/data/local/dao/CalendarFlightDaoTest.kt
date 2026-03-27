package com.flightlog.app.data.local.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.entity.CalendarFlight
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
class CalendarFlightDaoTest {

    private lateinit var db: FlightDatabase
    private lateinit var dao: CalendarFlightDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.calendarFlightDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun flight(
        calendarEventId: Long = 100L,
        legIndex: Int = 0,
        flightNumber: String = "AA11",
        departureCode: String = "ORD",
        arrivalCode: String = "CMH",
        rawTitle: String = "Flight AA11",
        scheduledTime: Long = 1_700_000_000_000L,
        endTime: Long? = 1_700_003_600_000L,
        departureTimezone: String? = null,
        arrivalTimezone: String? = null,
        syncedAt: Long = System.currentTimeMillis(),
        isManuallyDismissed: Boolean = false
    ) = CalendarFlight(
        calendarEventId = calendarEventId,
        legIndex = legIndex,
        flightNumber = flightNumber,
        departureCode = departureCode,
        arrivalCode = arrivalCode,
        rawTitle = rawTitle,
        scheduledTime = scheduledTime,
        endTime = endTime,
        departureTimezone = departureTimezone,
        arrivalTimezone = arrivalTimezone,
        syncedAt = syncedAt,
        isManuallyDismissed = isManuallyDismissed
    )

    // ── Upsert round-trip with timezone columns ──────────────────────────────

    @Test
    fun `upsert and retrieve flight with timezone columns`() = runTest {
        val f = flight(
            departureTimezone = "America/Chicago",
            arrivalTimezone = "America/New_York"
        )
        dao.upsertAll(listOf(f))

        val all = dao.getAllVisible().first()
        assertEquals(1, all.size)
        assertEquals("America/Chicago", all[0].departureTimezone)
        assertEquals("America/New_York", all[0].arrivalTimezone)
        assertEquals("ORD", all[0].departureCode)
        assertEquals("CMH", all[0].arrivalCode)
        assertEquals("AA11", all[0].flightNumber)
    }

    @Test
    fun `null timezone columns round-trip`() = runTest {
        val f = flight(departureTimezone = null, arrivalTimezone = null)
        dao.upsertAll(listOf(f))

        val all = dao.getAllVisible().first()
        assertEquals(1, all.size)
        assertNull(all[0].departureTimezone)
        assertNull(all[0].arrivalTimezone)
    }

    // ── Upsert conflict resolution (second sync updates, not duplicates) ─────

    @Test
    fun `second upsert updates existing row instead of duplicating`() = runTest {
        val f1 = flight(flightNumber = "AA11", departureTimezone = null)
        dao.upsertAll(listOf(f1))

        val firstAll = dao.getAllVisible().first()
        assertEquals(1, firstAll.size)
        val originalId = firstAll[0].id

        // Second sync: same calendarEventId+legIndex, updated fields
        val f2 = flight(
            flightNumber = "AA11",
            departureTimezone = "America/Chicago",
            arrivalTimezone = "America/New_York"
        )
        dao.upsertAll(listOf(f2))

        val secondAll = dao.getAllVisible().first()
        assertEquals(1, secondAll.size)
        assertEquals(originalId, secondAll[0].id)
        assertEquals("America/Chicago", secondAll[0].departureTimezone)
    }

    @Test
    fun `upsert with different legIndex creates separate rows`() = runTest {
        val leg0 = flight(calendarEventId = 100, legIndex = 0, flightNumber = "WN1946")
        val leg1 = flight(calendarEventId = 100, legIndex = 1, flightNumber = "WN3034")
        dao.upsertAll(listOf(leg0, leg1))

        val all = dao.getAllVisible().first()
        assertEquals(2, all.size)
    }

    @Test
    fun `upsert preserves id across multiple syncs`() = runTest {
        dao.upsertAll(listOf(flight()))
        val id1 = dao.getAllVisible().first()[0].id

        dao.upsertAll(listOf(flight(rawTitle = "Updated title")))
        val result = dao.getAllVisible().first()
        assertEquals(1, result.size)
        assertEquals(id1, result[0].id)
        assertEquals("Updated title", result[0].rawTitle)
    }

    // ── Upcoming / past partition ────────────────────────────────────────────

    @Test
    fun `getUpcoming returns only future flights sorted ascending`() = runTest {
        val now = 1_700_000_000_000L
        val past = flight(calendarEventId = 1, scheduledTime = now - 1000)
        val future1 = flight(calendarEventId = 2, scheduledTime = now + 2_000_000)
        val future2 = flight(calendarEventId = 3, scheduledTime = now + 1_000_000)
        dao.upsertAll(listOf(past, future1, future2))

        val upcoming = dao.getUpcoming(now).first()
        assertEquals(2, upcoming.size)
        // Sorted ascending by scheduledTime
        assertTrue(upcoming[0].scheduledTime <= upcoming[1].scheduledTime)
        assertEquals(3L, upcoming[0].calendarEventId) // closer one first
    }

    @Test
    fun `getPast returns only past flights sorted descending`() = runTest {
        val now = 1_700_000_000_000L
        val past1 = flight(calendarEventId = 1, scheduledTime = now - 2_000_000)
        val past2 = flight(calendarEventId = 2, scheduledTime = now - 1_000_000)
        val future = flight(calendarEventId = 3, scheduledTime = now + 1000)
        dao.upsertAll(listOf(past1, past2, future))

        val pastFlights = dao.getPast(now).first()
        assertEquals(2, pastFlights.size)
        // Sorted descending by scheduledTime
        assertTrue(pastFlights[0].scheduledTime >= pastFlights[1].scheduledTime)
        assertEquals(2L, pastFlights[0].calendarEventId) // more recent past first
    }

    @Test
    fun `getUpcoming includes flights at exact now boundary`() = runTest {
        val now = 1_700_000_000_000L
        val atBoundary = flight(calendarEventId = 1, scheduledTime = now)
        dao.upsertAll(listOf(atBoundary))

        val upcoming = dao.getUpcoming(now).first()
        assertEquals(1, upcoming.size)

        val past = dao.getPast(now).first()
        assertEquals(0, past.size)
    }

    // ── Dismiss ──────────────────────────────────────────────────────────────

    @Test
    fun `dismiss hides flight from getAllVisible`() = runTest {
        dao.upsertAll(listOf(flight()))
        val inserted = dao.getAllVisible().first()[0]

        dao.dismiss(inserted.id)

        val visible = dao.getAllVisible().first()
        assertEquals(0, visible.size)

        // Still retrievable by ID
        val byId = dao.getById(inserted.id)
        assertNotNull(byId)
        assertTrue(byId!!.isManuallyDismissed)
    }

    @Test
    fun `dismissed flights excluded from upcoming and past`() = runTest {
        val now = 1_700_000_000_000L
        dao.upsertAll(listOf(
            flight(calendarEventId = 1, scheduledTime = now + 1000),
            flight(calendarEventId = 2, scheduledTime = now - 1000)
        ))

        val all = dao.getAllVisible().first()
        assertEquals(2, all.size)

        // Dismiss both
        all.forEach { dao.dismiss(it.id) }

        assertEquals(0, dao.getUpcoming(now).first().size)
        assertEquals(0, dao.getPast(now).first().size)
    }

    // ── DismissAllLegsForEvent ───────────────────────────────────────────────

    @Test
    fun `dismissAllLegsForEvent dismisses all legs of multi-leg flight`() = runTest {
        val leg0 = flight(calendarEventId = 100, legIndex = 0, flightNumber = "WN1946")
        val leg1 = flight(calendarEventId = 100, legIndex = 1, flightNumber = "WN3034")
        val other = flight(calendarEventId = 200, legIndex = 0, flightNumber = "AA11")
        dao.upsertAll(listOf(leg0, leg1, other))

        dao.dismissAllLegsForEvent(100L)

        val visible = dao.getAllVisible().first()
        assertEquals(1, visible.size)
        assertEquals(200L, visible[0].calendarEventId)
    }

    // ── RemoveStaleIds ───────────────────────────────────────────────────────

    @Test
    fun `removeStaleIds deletes rows not in valid set`() = runTest {
        dao.upsertAll(listOf(
            flight(calendarEventId = 1),
            flight(calendarEventId = 2),
            flight(calendarEventId = 3)
        ))

        dao.removeStaleIds(listOf(1L, 3L))

        val remaining = dao.getAllCalendarEventIds()
        assertEquals(2, remaining.size)
        assertTrue(remaining.contains(1L))
        assertTrue(remaining.contains(3L))
    }

    @Test
    fun `removeStaleIds with all valid keeps everything`() = runTest {
        dao.upsertAll(listOf(
            flight(calendarEventId = 1),
            flight(calendarEventId = 2)
        ))

        dao.removeStaleIds(listOf(1L, 2L))

        assertEquals(2, dao.getAllCalendarEventIds().size)
    }

    @Test
    fun `removeStaleIds removes multi-leg flights together`() = runTest {
        dao.upsertAll(listOf(
            flight(calendarEventId = 100, legIndex = 0),
            flight(calendarEventId = 100, legIndex = 1),
            flight(calendarEventId = 200, legIndex = 0)
        ))

        dao.removeStaleIds(listOf(200L))

        val remaining = dao.getAllVisible().first()
        assertEquals(1, remaining.size)
        assertEquals(200L, remaining[0].calendarEventId)
    }

    // ── GetDismissedCalendarEventIds ─────────────────────────────────────────

    @Test
    fun `getDismissedCalendarEventIds returns only dismissed event ids`() = runTest {
        dao.upsertAll(listOf(
            flight(calendarEventId = 1),
            flight(calendarEventId = 2),
            flight(calendarEventId = 3)
        ))

        val all = dao.getAllVisible().first()
        val id1 = all.first { it.calendarEventId == 1L }.id
        dao.dismiss(id1)

        val dismissed = dao.getDismissedCalendarEventIds()
        assertEquals(1, dismissed.size)
        assertEquals(1L, dismissed[0])
    }

    @Test
    fun `getDismissedCalendarEventIds returns empty when nothing dismissed`() = runTest {
        dao.upsertAll(listOf(flight(calendarEventId = 1)))

        val dismissed = dao.getDismissedCalendarEventIds()
        assertTrue(dismissed.isEmpty())
    }

    // ── GetVisibleCount ──────────────────────────────────────────────────────

    @Test
    fun `getVisibleCount reflects dismiss`() = runTest {
        dao.upsertAll(listOf(
            flight(calendarEventId = 1),
            flight(calendarEventId = 2)
        ))

        assertEquals(2, dao.getVisibleCount().first())

        val first = dao.getAllVisible().first()[0]
        dao.dismiss(first.id)

        assertEquals(1, dao.getVisibleCount().first())
    }

    // ── GetById ──────────────────────────────────────────────────────────────

    @Test
    fun `getById returns null for nonexistent id`() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun `getById returns flight with all fields`() = runTest {
        val f = flight(
            departureTimezone = "Asia/Tokyo",
            arrivalTimezone = "America/Los_Angeles"
        )
        dao.upsertAll(listOf(f))
        val inserted = dao.getAllVisible().first()[0]

        val byId = dao.getById(inserted.id)
        assertNotNull(byId)
        assertEquals("Asia/Tokyo", byId!!.departureTimezone)
        assertEquals("America/Los_Angeles", byId.arrivalTimezone)
        assertEquals("AA11", byId.flightNumber)
    }

    // ── Boundary: empty table ────────────────────────────────────────────────

    @Test
    fun `getAllVisible on empty table returns empty`() = runTest {
        val all = dao.getAllVisible().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `getAllCalendarEventIds on empty table returns empty`() = runTest {
        assertTrue(dao.getAllCalendarEventIds().isEmpty())
    }
}
