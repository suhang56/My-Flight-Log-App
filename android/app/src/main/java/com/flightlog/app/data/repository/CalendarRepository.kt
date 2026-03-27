package com.flightlog.app.data.repository

import android.content.ContentResolver
import com.flightlog.app.data.calendar.CalendarDataSource
import com.flightlog.app.data.calendar.FlightEventParser
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a [CalendarRepository.syncFromCalendar] call. */
sealed class SyncResult {
    data class Success(val syncedCount: Int, val removedCount: Int) : SyncResult()
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}

/**
 * Single source of truth for calendar-derived flight data.
 *
 * Coordinates between [CalendarDataSource] (raw calendar reads),
 * [FlightEventParser] (title → flight details), and [CalendarFlightDao] (persistence).
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDataSource: CalendarDataSource,
    private val flightEventParser: FlightEventParser,
    private val calendarFlightDao: CalendarFlightDao
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

    suspend fun dismiss(id: Long) = calendarFlightDao.dismiss(id)

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
            val flights = rawEvents.mapNotNull { event ->
                val parsed = flightEventParser.parse(
                    title       = event.title,
                    description = event.description,
                    location    = event.location
                ) ?: return@mapNotNull null

                CalendarFlight(
                    calendarEventId = event.eventId,
                    flightNumber    = parsed.flightNumber,
                    departureCode   = parsed.departureCode,
                    arrivalCode     = parsed.arrivalCode,
                    rawTitle        = event.title,
                    scheduledTime   = event.dtStart,
                    endTime         = event.dtEnd,
                    syncedAt        = now
                )
            }

            // 3. Persist recognised flights, preserving dismissed state.
            if (flights.isNotEmpty()) {
                val dismissedIds = calendarFlightDao.getDismissedCalendarEventIds()
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
}
