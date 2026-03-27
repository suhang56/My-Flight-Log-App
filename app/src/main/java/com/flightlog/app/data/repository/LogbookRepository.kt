package com.flightlog.app.data.repository

import com.flightlog.app.data.AirportCoordinatesMap
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogbookRepository @Inject constructor(
    private val logbookFlightDao: LogbookFlightDao
) {

    fun getAll(): Flow<List<LogbookFlight>> = logbookFlightDao.getAll()

    suspend fun getById(id: Long): LogbookFlight? = logbookFlightDao.getById(id)

    fun getCount(): Flow<Int> = logbookFlightDao.getCount()

    fun getTotalDistanceNm(): Flow<Int> = logbookFlightDao.getTotalDistanceNm()

    /**
     * Checks whether a [CalendarFlight] has already been added to the logbook.
     * Returns true if a matching (sourceCalendarEventId, sourceLegIndex) row exists.
     */
    suspend fun isAlreadyLogged(calendarFlight: CalendarFlight): Boolean {
        return logbookFlightDao.existsBySource(
            calendarEventId = calendarFlight.calendarEventId,
            legIndex = calendarFlight.legIndex
        )
    }

    /**
     * Creates a [LogbookFlight] from a [CalendarFlight] and inserts it.
     * Computes great-circle distance when both airport codes are known.
     *
     * Returns the new row ID, or -1 if the insert was ignored (duplicate source).
     */
    suspend fun addFromCalendarFlight(calendarFlight: CalendarFlight): Long {
        val distance = AirportCoordinatesMap.distanceNm(
            calendarFlight.departureCode,
            calendarFlight.arrivalCode
        )

        val logbookFlight = LogbookFlight(
            sourceCalendarEventId = calendarFlight.calendarEventId,
            sourceLegIndex = calendarFlight.legIndex,
            flightNumber = calendarFlight.flightNumber,
            departureCode = calendarFlight.departureCode,
            arrivalCode = calendarFlight.arrivalCode,
            departureTimeUtc = calendarFlight.scheduledTime,
            arrivalTimeUtc = calendarFlight.endTime,
            departureTimezone = calendarFlight.departureTimezone,
            arrivalTimezone = calendarFlight.arrivalTimezone,
            distanceNm = distance
        )

        return logbookFlightDao.insert(logbookFlight)
    }

    /**
     * Returns true if a flight with the same route and UTC departure day already exists.
     * [excludeId] allows the current flight to be ignored (for edit mode).
     */
    suspend fun existsByRouteAndDate(
        depCode: String,
        arrCode: String,
        departureTimeUtc: Long,
        excludeId: Long? = null
    ): Boolean {
        val utcDay = departureTimeUtc / 86400000
        return logbookFlightDao.existsByRouteAndDate(depCode, arrCode, utcDay, excludeId)
    }

    suspend fun insert(flight: LogbookFlight): Long = logbookFlightDao.insert(flight)

    suspend fun update(flight: LogbookFlight) =
        logbookFlightDao.update(flight.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(id: Long) = logbookFlightDao.deleteById(id)
}
