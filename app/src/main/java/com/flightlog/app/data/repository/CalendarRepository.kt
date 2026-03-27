package com.flightlog.app.data.repository

import android.content.ContentResolver
import com.flightlog.app.data.AirportTimezoneMap
import com.flightlog.app.data.calendar.CalendarDataSource
import com.flightlog.app.data.calendar.FlightEventParser
import com.flightlog.app.data.calendar.ParsedFlight
import com.flightlog.app.data.calendar.RawCalendarEvent
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.network.FlightRouteService
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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
                parsedLegs.mapIndexed { legIndex, parsed ->
                    resolveFlight(event, parsed, legIndex, now)
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
     * Resolves a single parsed leg into a [CalendarFlight] entity.
     *
     * Fills in missing airport codes via [FlightRouteService] and resolves
     * timezones from the API response or the static [AirportTimezoneMap].
     */
    private suspend fun resolveFlight(
        event: RawCalendarEvent,
        parsed: ParsedFlight,
        legIndex: Int,
        syncTimestamp: Long
    ): CalendarFlight {
        var depCode = parsed.departureCode
        var arrCode = parsed.arrivalCode
        var depTz: String? = null
        var arrTz: String? = null

        // Use departure airport timezone for date computation when available.
        val depZone = if (depCode.isNotEmpty()) {
            AirportTimezoneMap.timezoneFor(depCode)?.let { ZoneId.of(it) }
        } else null
        val eventDate = Instant.ofEpochMilli(event.dtStart)
            .atZone(depZone ?: ZoneId.systemDefault())
            .toLocalDate()

        // Resolve routes via API for legs missing airport codes.
        if (depCode.isEmpty() && arrCode.isEmpty() && parsed.flightNumber.isNotEmpty()) {
            val route = flightRouteService.lookupRoute(parsed.flightNumber, eventDate)
            if (route != null) {
                depCode = route.departureIata
                arrCode = route.arrivalIata
                depTz = route.departureTimezone
                arrTz = route.arrivalTimezone
            }
        }

        // Resolve timezones: prefer API response, fall back to static map.
        if (depTz == null && depCode.isNotEmpty()) {
            depTz = AirportTimezoneMap.timezoneFor(depCode)
        }
        if (arrTz == null && arrCode.isNotEmpty()) {
            arrTz = AirportTimezoneMap.timezoneFor(arrCode)
        }

        return CalendarFlight(
            calendarEventId   = event.eventId,
            legIndex          = legIndex,
            flightNumber      = parsed.flightNumber,
            departureCode     = depCode,
            arrivalCode       = arrCode,
            rawTitle          = event.title,
            scheduledTime     = event.dtStart,
            endTime           = event.dtEnd,
            departureTimezone = depTz,
            arrivalTimezone   = arrTz,
            syncedAt          = syncTimestamp
        )
    }
}
