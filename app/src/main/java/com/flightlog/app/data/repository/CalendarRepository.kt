package com.flightlog.app.data.repository

import android.content.ContentResolver
import com.flightlog.app.data.calendar.CalendarDataSource
import com.flightlog.app.data.calendar.FlightEventParser
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.network.FlightRouteService
import kotlinx.coroutines.flow.Flow
import com.flightlog.app.data.calendar.ParsedFlight
import com.flightlog.app.data.calendar.RawCalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for calendar-derived flight data.
 *
 * Coordinates between [CalendarDataSource] (raw calendar reads),
 * [FlightEventParser] (title -> flight details), and [CalendarFlightDao] (persistence).
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDataSource: CalendarDataSource,
    private val flightEventParser: FlightEventParser,
    private val calendarFlightDao: CalendarFlightDao,
    private val flightRouteService: FlightRouteService
) {

    fun getAllVisible(): Flow<List<CalendarFlight>> = calendarFlightDao.getAllVisible()

    /** Alias matching the spec API; delegates to the DAO with a current-time snapshot. */
    fun upcomingFlights(now: Long = System.currentTimeMillis()): Flow<List<CalendarFlight>> =
        calendarFlightDao.getUpcoming(now)

    /** Alias matching the spec API; delegates to the DAO with a current-time snapshot. */
    fun pastFlights(now: Long = System.currentTimeMillis()): Flow<List<CalendarFlight>> =
        calendarFlightDao.getPast(now)

    fun getVisibleCount(): Flow<Int> = calendarFlightDao.getVisibleCount()

    suspend fun getById(id: Long): CalendarFlight? = calendarFlightDao.getById(id)

    suspend fun dismiss(id: Long) {
        val flight = calendarFlightDao.getById(id)
        if (flight != null) {
            calendarFlightDao.dismissAllLegsForEvent(flight.calendarEventId)
        } else {
            calendarFlightDao.dismiss(id)
        }
    }

    /**
     * Full sync cycle:
     *  1. Query raw events from the device calendar via [contentResolver].
     *  2. Parse each event title/description/location for flight details.
     *  3. Upsert all recognised flights.
     *  4. Remove rows whose calendar event is no longer present.
     *
     * Guards the empty-list edge case in [CalendarFlightDao.removeStaleIds] — stale
     * removal is skipped when no events came back to prevent accidental data loss.
     */
    suspend fun syncFromCalendar(contentResolver: ContentResolver): SyncResult {
        return try {
            val now = System.currentTimeMillis()

            // 1. Read raw calendar events.
            val rawEvents = calendarDataSource.queryEvents(contentResolver)

            // 2. Parse; discard events that don't contain recognisable flight data.
            //    A single calendar event can produce multiple legs (e.g. "WN1946/3034").
            val flights = rawEvents.flatMap { event ->
                val parsedLegs = flightEventParser.parse(
                    title       = event.title,
                    description = event.description,
                    location    = event.location
                )
                val eventDate = Instant.ofEpochMilli(event.dtStart)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                parsedLegs.mapIndexed { legIndex, parsed ->
                    resolveFlight(event, legIndex, parsed, eventDate, now)
                }
            }

            // 3. Persist recognised flights, preserving dismissed state.
            if (flights.isNotEmpty()) {
                val dismissedIds = calendarFlightDao.getDismissedCalendarEventIds().toSet()
                val flightsWithDismissState = flights.map { flight ->
                    if (flight.calendarEventId in dismissedIds) {
                        flight.copy(isManuallyDismissed = true)
                    } else {
                        flight
                    }
                }
                calendarFlightDao.upsertAll(flightsWithDismissState)
            }

            // 4. Remove stale rows — only safe with a non-empty valid-ID set.
            val removedCount = if (rawEvents.isNotEmpty()) {
                val validIds  = rawEvents.map { it.eventId }
                val storedIds = calendarFlightDao.getAllCalendarEventIds().toSet()
                val stale     = (storedIds - validIds.toSet()).size
                if (validIds.isNotEmpty()) {
                    calendarFlightDao.removeStaleIds(validIds)
                }
                stale
            } else {
                // Empty result could mean no calendar events or a silent permission problem.
                // Skip removal to avoid wiping existing data.
                0
            }

            SyncResult.Success(syncedCount = flights.size, removedCount = removedCount)

        } catch (e: SecurityException) {
            SyncResult.Error("Calendar permission not granted", e)
        } catch (e: Exception) {
            SyncResult.Error("Sync failed: ${e.message}", e)
        }
    }

    /**
     * Resolves a single parsed leg into a [CalendarFlight], using the route API
     * to fill in missing departure/arrival codes when only a flight number is known.
     */
    private suspend fun resolveFlight(
        event: RawCalendarEvent,
        legIndex: Int,
        parsed: ParsedFlight,
        eventDate: LocalDate,
        now: Long
    ): CalendarFlight {
        var depCode = parsed.departureCode
        var arrCode = parsed.arrivalCode

        if (depCode.isEmpty() && arrCode.isEmpty() && parsed.flightNumber.isNotEmpty()) {
            val route = flightRouteService.lookupRoute(parsed.flightNumber, eventDate)
            if (route != null) {
                depCode = route.departureIata
                arrCode = route.arrivalIata
            }
        }

        return CalendarFlight(
            calendarEventId = event.eventId,
            legIndex        = legIndex,
            flightNumber    = parsed.flightNumber,
            departureCode   = depCode,
            arrivalCode     = arrCode,
            rawTitle        = event.title,
            scheduledTime   = event.dtStart,
            endTime         = event.dtEnd,
            syncedAt        = now
        )
    }
}
