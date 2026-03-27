package com.flightlog.app.data.repository

import android.content.ContentResolver
import com.flightlog.app.data.calendar.CalendarFlightReader
import com.flightlog.app.data.local.dao.FlightDao
import com.flightlog.app.data.local.entity.FlightEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    data class Success(val newCount: Int, val updatedCount: Int, val removedCount: Int) : SyncResult()
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}

@Singleton
class FlightRepository @Inject constructor(
    private val flightDao: FlightDao,
    private val calendarReader: CalendarFlightReader
) {

    fun getAllFlights(): Flow<List<FlightEntity>> = flightDao.getAllFlights()

    fun getUpcomingFlights(): Flow<List<FlightEntity>> =
        flightDao.getUpcomingFlights(Instant.now().toEpochMilli())

    fun getPastFlights(): Flow<List<FlightEntity>> =
        flightDao.getPastFlights(Instant.now().toEpochMilli())

    fun getFlightCount(): Flow<Int> = flightDao.getFlightCount()

    suspend fun getFlightById(id: Long): FlightEntity? = flightDao.getFlightById(id)

    suspend fun syncFromCalendar(contentResolver: ContentResolver): SyncResult {
        return try {
            val calendarFlights = calendarReader.readFlightEvents(contentResolver)
            val existingEventIds = flightDao.getAllCalendarEventIds().toSet()
            val newEventIds = calendarFlights.map { it.calendarEventId }.toSet()

            // Insert or update all found flights
            flightDao.insertFlights(calendarFlights)

            // Remove flights whose calendar events no longer exist
            if (newEventIds.isNotEmpty()) {
                flightDao.deleteRemovedEvents(newEventIds.toList())
            }

            val newCount = calendarFlights.count { it.calendarEventId !in existingEventIds }
            val updatedCount = calendarFlights.size - newCount
            val removedCount = (existingEventIds - newEventIds).size

            SyncResult.Success(
                newCount = newCount,
                updatedCount = updatedCount,
                removedCount = removedCount
            )
        } catch (e: SecurityException) {
            SyncResult.Error("Calendar permission not granted", e)
        } catch (e: Exception) {
            SyncResult.Error("Failed to sync calendar: ${e.message}", e)
        }
    }
}
