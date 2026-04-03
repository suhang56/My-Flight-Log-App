package com.flightlog.app.data.repository

import com.flightlog.app.data.airport.AirportCoordinatesMap
import com.flightlog.app.data.airport.AirportTimezoneMap
import com.flightlog.app.data.local.dao.AirlineCount
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.dao.MonthlyCount
import com.flightlog.app.data.local.dao.RouteCount
import com.flightlog.app.data.local.dao.SeatClassCount
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogbookRepository @Inject constructor(
    private val logbookFlightDao: LogbookFlightDao
) {

    val allFlights: Flow<List<LogbookFlight>> = logbookFlightDao.getAll()

    /**
     * Converts a [CalendarFlight] into a [LogbookFlight] and inserts it.
     *
     * Computes:
     * - departureDateEpochDay using the departure airport's timezone (or system default)
     * - durationMinutes from endTime - scheduledTime
     * - distanceKm via [AirportCoordinatesMap.greatCircleKm]
     *
     * Returns the new row ID.
     */
    suspend fun addFromCalendarFlight(calendarFlight: CalendarFlight): Long {
        val depTz = AirportTimezoneMap.getTimezone(calendarFlight.departureCode)
        val arrTz = AirportTimezoneMap.getTimezone(calendarFlight.arrivalCode)

        val zoneId = if (depTz != null) ZoneId.of(depTz) else ZoneId.systemDefault()
        val departureDateEpochDay = Instant.ofEpochMilli(calendarFlight.scheduledTime)
            .atZone(zoneId)
            .toLocalDate()
            .toEpochDay()

        val durationMinutes = if (calendarFlight.endTime != null && calendarFlight.endTime > calendarFlight.scheduledTime) {
            ((calendarFlight.endTime - calendarFlight.scheduledTime) / 60_000).toInt()
        } else {
            null
        }

        val distanceKm = AirportCoordinatesMap.greatCircleKm(
            calendarFlight.departureCode,
            calendarFlight.arrivalCode
        )

        val now = System.currentTimeMillis()
        val logbookFlight = LogbookFlight(
            sourceCalendarEventId = calendarFlight.calendarEventId,
            flightNumber = calendarFlight.flightNumber,
            departureCode = calendarFlight.departureCode,
            arrivalCode = calendarFlight.arrivalCode,
            departureDateEpochDay = departureDateEpochDay,
            departureTimeMillis = calendarFlight.scheduledTime,
            arrivalTimeMillis = calendarFlight.endTime,
            durationMinutes = durationMinutes,
            departureTimezone = depTz,
            arrivalTimezone = arrTz,
            distanceKm = distanceKm,
            createdAt = now,
            updatedAt = now
        )

        return logbookFlightDao.insert(logbookFlight)
    }

    suspend fun isAlreadyLogged(calendarEventId: Long): Boolean {
        return logbookFlightDao.findByCalendarEventId(calendarEventId) != null
    }

    suspend fun findByCalendarEventId(calendarEventId: Long): LogbookFlight? {
        return logbookFlightDao.findByCalendarEventId(calendarEventId)
    }

    suspend fun getById(id: Long): LogbookFlight? = logbookFlightDao.getById(id)

    fun getByIdFlow(id: Long): Flow<LogbookFlight?> = logbookFlightDao.getByIdFlow(id)

    suspend fun insert(flight: LogbookFlight): Long = logbookFlightDao.insert(flight)

    suspend fun update(flight: LogbookFlight) = logbookFlightDao.update(flight)

    suspend fun delete(flight: LogbookFlight) = logbookFlightDao.delete(flight)

    suspend fun delete(id: Long) = logbookFlightDao.deleteById(id)

    suspend fun setRating(id: Long, rating: Int?) {
        val clamped = rating?.coerceIn(1, 5)
        logbookFlightDao.updateRating(id, clamped)
    }

    // --- Stats flows ---

    fun getFlightCount(): Flow<Int> = logbookFlightDao.getFlightCount()

    fun getTotalDurationMinutes(): Flow<Long?> = logbookFlightDao.getTotalDurationMinutes()

    fun getTotalDistanceKm(): Flow<Long?> = logbookFlightDao.getTotalDistanceKm()

    fun getUniqueAirportCount(): Flow<Int> = logbookFlightDao.getUniqueAirportCount()

    fun getSeatClassCounts(): Flow<List<SeatClassCount>> = logbookFlightDao.getSeatClassCounts()

    fun getTopAirlines(limit: Int = 5): Flow<List<AirlineCount>> = logbookFlightDao.getTopAirlines(limit)

    fun getLongestFlightByDistance(): Flow<LogbookFlight?> = logbookFlightDao.getLongestFlightByDistance()

    fun getLongestFlightByDuration(): Flow<LogbookFlight?> = logbookFlightDao.getLongestFlightByDuration()

    fun getTopRoutes(limit: Int = 5): Flow<List<RouteCount>> = logbookFlightDao.getTopRoutes(limit)

    fun getFirstFlight(): Flow<LogbookFlight?> = logbookFlightDao.getFirstFlight()

    fun getMonthlyFlightCounts(): Flow<List<MonthlyCount>> = logbookFlightDao.getMonthlyFlightCounts()

    suspend fun getAllOnce(): List<LogbookFlight> = logbookFlightDao.getAllOnce()

    suspend fun insertAllForRestore(flights: List<LogbookFlight>): List<Long> =
        flights.map { flight ->
            val exists = logbookFlightDao.countByFlightAndTime(
                flight.flightNumber,
                flight.departureTimeMillis
            ) > 0
            if (exists) -1L else logbookFlightDao.insert(flight)
        }
}
