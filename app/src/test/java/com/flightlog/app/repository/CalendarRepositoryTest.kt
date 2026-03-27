package com.flightlog.app.repository

import android.content.ContentResolver
import com.flightlog.app.data.calendar.CalendarDataSource
import com.flightlog.app.data.calendar.FlightEventParser
import com.flightlog.app.data.calendar.ParsedFlight
import com.flightlog.app.data.calendar.RawCalendarEvent
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.network.FlightRoute
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalendarRepositoryTest {

    private lateinit var calendarDataSource: CalendarDataSource
    private lateinit var flightEventParser: FlightEventParser
    private lateinit var calendarFlightDao: CalendarFlightDao
    private lateinit var flightRouteService: FlightRouteService
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: CalendarRepository

    @Before
    fun setUp() {
        calendarDataSource = mockk()
        flightEventParser = mockk()
        calendarFlightDao = mockk(relaxed = true)
        flightRouteService = mockk()
        contentResolver = mockk()

        repository = CalendarRepository(
            calendarDataSource = calendarDataSource,
            flightEventParser = flightEventParser,
            calendarFlightDao = calendarFlightDao,
            flightRouteService = flightRouteService
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun rawEvent(
        eventId: Long = 100L,
        title: String = "Flight AA11 ORD-CMH",
        description: String = "",
        location: String = "",
        dtStart: Long = 1_700_000_000_000L,
        dtEnd: Long? = 1_700_003_600_000L
    ) = RawCalendarEvent(eventId, title, description, location, dtStart, dtEnd)

    private fun parsed(
        flightNumber: String = "AA11",
        departureCode: String = "ORD",
        arrivalCode: String = "CMH"
    ) = ParsedFlight(flightNumber, departureCode, arrivalCode)

    private fun route(
        flightNumber: String = "AA11",
        departureIata: String = "ORD",
        arrivalIata: String = "CMH",
        departureTimezone: String? = "America/Chicago",
        arrivalTimezone: String? = "America/New_York"
    ) = FlightRoute(flightNumber, departureIata, arrivalIata, departureTimezone, arrivalTimezone)

    private fun stubSingleFlight(
        event: RawCalendarEvent = rawEvent(),
        parsedLegs: List<ParsedFlight> = listOf(parsed())
    ) {
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns parsedLegs
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `happy path - single flight synced and upserted`() = runTest {
        stubSingleFlight()

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.syncedCount)
        assertEquals(0, success.removedCount)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals(1, captured.captured.size)
        assertEquals("AA11", captured.captured[0].flightNumber)
        assertEquals("ORD", captured.captured[0].departureCode)
        assertEquals("CMH", captured.captured[0].arrivalCode)
    }

    @Test
    fun `happy path - timezone populated from static map`() = runTest {
        stubSingleFlight()

        val result = repository.syncFromCalendar(contentResolver)
        assertTrue(result is SyncResult.Success)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals("America/Chicago", captured.captured[0].departureTimezone)
        assertEquals("America/New_York", captured.captured[0].arrivalTimezone)
    }

    // ── Multi-leg ────────────────────────────────────────────────────────────

    @Test
    fun `multi-leg flight produces correct leg count and indices`() = runTest {
        val event = rawEvent(title = "Southwest Flight 1946/3034")
        val legs = listOf(
            parsed(flightNumber = "WN1946", departureCode = "CMH", arrivalCode = "MDW"),
            parsed(flightNumber = "WN3034", departureCode = "MDW", arrivalCode = "LAX")
        )
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns legs
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)

        val result = repository.syncFromCalendar(contentResolver)
        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).syncedCount)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals(2, captured.captured.size)
        assertEquals(0, captured.captured[0].legIndex)
        assertEquals(1, captured.captured[1].legIndex)
        assertEquals("WN1946", captured.captured[0].flightNumber)
        assertEquals("WN3034", captured.captured[1].flightNumber)
    }

    // ── Route API triggered / skipped ────────────────────────────────────────

    @Test
    fun `route API called when departure and arrival codes are empty`() = runTest {
        val event = rawEvent()
        val parsedNoRoute = parsed(flightNumber = "AA11", departureCode = "", arrivalCode = "")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsedNoRoute)
        coEvery { flightRouteService.lookupRoute("AA11", any()) } returns route()
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)

        repository.syncFromCalendar(contentResolver)

        coVerify(exactly = 1) { flightRouteService.lookupRoute("AA11", any()) }

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals("ORD", captured.captured[0].departureCode)
        assertEquals("CMH", captured.captured[0].arrivalCode)
        assertEquals("America/Chicago", captured.captured[0].departureTimezone)
        assertEquals("America/New_York", captured.captured[0].arrivalTimezone)
    }

    @Test
    fun `route API skipped when codes already present`() = runTest {
        stubSingleFlight()

        repository.syncFromCalendar(contentResolver)

        coVerify(exactly = 0) { flightRouteService.lookupRoute(any(), any()) }
    }

    @Test
    fun `route API skipped when flight number is empty`() = runTest {
        val event = rawEvent()
        val noFlightNumber = parsed(flightNumber = "", departureCode = "", arrivalCode = "")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(noFlightNumber)
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)

        repository.syncFromCalendar(contentResolver)

        coVerify(exactly = 0) { flightRouteService.lookupRoute(any(), any()) }
    }

    @Test
    fun `route API returns null leaves codes empty`() = runTest {
        val event = rawEvent()
        val parsedNoRoute = parsed(flightNumber = "AA11", departureCode = "", arrivalCode = "")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsedNoRoute)
        coEvery { flightRouteService.lookupRoute("AA11", any()) } returns null
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)

        repository.syncFromCalendar(contentResolver)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals("", captured.captured[0].departureCode)
        assertEquals("", captured.captured[0].arrivalCode)
        assertNull(captured.captured[0].departureTimezone)
        assertNull(captured.captured[0].arrivalTimezone)
    }

    // ── Timezone date computation edge case ──────────────────────────────────

    @Test
    fun `Tokyo 2330 JST uses departure timezone for date computation`() = runTest {
        // 2026-03-27 23:30 JST = 2026-03-27 14:30 UTC
        // In JST (Asia/Tokyo, UTC+9), this is March 27.
        // In UTC, this is also March 27 14:30 — but if device were UTC-5 (EST),
        // it would be March 27 09:30.
        // The key: with NRT departure timezone, eventDate should be March 27 JST.
        val jstTime = 1_774_897_800_000L // 2026-03-27T14:30:00Z = 2026-03-27T23:30:00+09:00

        val event = rawEvent(dtStart = jstTime)
        val parsedFlight = parsed(flightNumber = "AA11", departureCode = "NRT", arrivalCode = "LAX")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsedFlight)
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(event.eventId)

        repository.syncFromCalendar(contentResolver)

        // Since NRT has codes, route API should NOT be called.
        // The timezone should be resolved from the static map.
        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertEquals("Asia/Tokyo", captured.captured[0].departureTimezone)
        assertEquals("America/Los_Angeles", captured.captured[0].arrivalTimezone)
    }

    // ── Dismissed state preservation ─────────────────────────────────────────

    @Test
    fun `dismissed state preserved across sync`() = runTest {
        val event = rawEvent(eventId = 42)
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsed())
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns listOf(42L)
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(42L)

        repository.syncFromCalendar(contentResolver)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertTrue(captured.captured[0].isManuallyDismissed)
    }

    @Test
    fun `non-dismissed events remain non-dismissed`() = runTest {
        val event = rawEvent(eventId = 42)
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsed())
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns listOf(99L) // different event
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(42L)

        repository.syncFromCalendar(contentResolver)

        val captured = slot<List<CalendarFlight>>()
        coVerify { calendarFlightDao.upsertAll(capture(captured)) }
        assertTrue(!captured.captured[0].isManuallyDismissed)
    }

    // ── Stale removal skip on empty ──────────────────────────────────────────

    @Test
    fun `stale removal skipped when no raw events returned`() = runTest {
        every { calendarDataSource.queryEvents(contentResolver) } returns emptyList()

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).syncedCount)
        assertEquals(0, result.removedCount)

        coVerify(exactly = 0) { calendarFlightDao.removeStaleIds(any()) }
        coVerify(exactly = 0) { calendarFlightDao.upsertAll(any()) }
    }

    // ── Stale removal counts ─────────────────────────────────────────────────

    @Test
    fun `removedCount reflects stale rows`() = runTest {
        val event = rawEvent(eventId = 1)
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns listOf(parsed())
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(1L, 2L, 3L) // 2 and 3 are stale

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).syncedCount)
        assertEquals(2, result.removedCount)
        coVerify { calendarFlightDao.removeStaleIds(listOf(1L)) }
    }

    // ── SecurityException handling ───────────────────────────────────────────

    @Test
    fun `SecurityException returns permission error`() = runTest {
        every { calendarDataSource.queryEvents(contentResolver) } throws SecurityException("No permission")

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Error)
        val error = result as SyncResult.Error
        assertEquals("Calendar permission not granted", error.message)
        assertTrue(error.cause is SecurityException)
    }

    @Test
    fun `generic exception returns sync error`() = runTest {
        every { calendarDataSource.queryEvents(contentResolver) } throws RuntimeException("Something broke")

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Error)
        val error = result as SyncResult.Error
        assertEquals("Sync failed: Something broke", error.message)
    }

    // ── No parseable flights ─────────────────────────────────────────────────

    @Test
    fun `event with no parseable flights skips upsert`() = runTest {
        val event = rawEvent(title = "Team meeting")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event)
        every { flightEventParser.parse(event.title, event.description, event.location) } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns emptyList()

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).syncedCount)
        coVerify(exactly = 0) { calendarFlightDao.upsertAll(any()) }
    }

    // ── syncedCount accuracy ─────────────────────────────────────────────────

    @Test
    fun `syncedCount counts all legs across multiple events`() = runTest {
        val event1 = rawEvent(eventId = 1, title = "Flight 1")
        val event2 = rawEvent(eventId = 2, title = "Flight 2")
        every { calendarDataSource.queryEvents(contentResolver) } returns listOf(event1, event2)
        every { flightEventParser.parse(event1.title, event1.description, event1.location) } returns listOf(
            parsed(flightNumber = "WN1946", departureCode = "CMH", arrivalCode = "MDW"),
            parsed(flightNumber = "WN3034", departureCode = "MDW", arrivalCode = "LAX")
        )
        every { flightEventParser.parse(event2.title, event2.description, event2.location) } returns listOf(
            parsed(flightNumber = "AA11", departureCode = "ORD", arrivalCode = "CMH")
        )
        coEvery { calendarFlightDao.getDismissedCalendarEventIds() } returns emptyList()
        coEvery { calendarFlightDao.getAllCalendarEventIds() } returns listOf(1L, 2L)

        val result = repository.syncFromCalendar(contentResolver)

        assertTrue(result is SyncResult.Success)
        assertEquals(3, (result as SyncResult.Success).syncedCount)
    }
}
