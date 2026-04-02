package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.flightlog.app.data.local.entity.LogbookFlight
import kotlinx.coroutines.flow.Flow

@Dao
interface LogbookFlightDao {

    @Query("SELECT * FROM logbook_flights ORDER BY departureDateEpochDay DESC")
    fun getAll(): Flow<List<LogbookFlight>>

    @Query("SELECT * FROM logbook_flights WHERE id = :id")
    suspend fun getById(id: Long): LogbookFlight?

    @Query("SELECT * FROM logbook_flights WHERE sourceCalendarEventId = :calendarEventId LIMIT 1")
    suspend fun findByCalendarEventId(calendarEventId: Long): LogbookFlight?

    @Insert
    suspend fun insert(flight: LogbookFlight): Long

    @Update
    suspend fun update(flight: LogbookFlight)

    @Delete
    suspend fun delete(flight: LogbookFlight)

    // --- Stats queries ---

    @Query("SELECT COUNT(*) FROM logbook_flights")
    fun getFlightCount(): Flow<Int>

    @Query("SELECT SUM(durationMinutes) FROM logbook_flights")
    fun getTotalDurationMinutes(): Flow<Long?>

    @Query("SELECT SUM(distanceKm) FROM logbook_flights")
    fun getTotalDistanceKm(): Flow<Long?>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT departureCode AS code FROM logbook_flights
            UNION
            SELECT arrivalCode AS code FROM logbook_flights
        )
        """
    )
    fun getUniqueAirportCount(): Flow<Int>

    // --- Statistics queries ---

    @Query(
        """
        SELECT seatClass, COUNT(*) AS count
        FROM logbook_flights
        WHERE seatClass IS NOT NULL AND seatClass != ''
        GROUP BY seatClass
        ORDER BY count DESC
        """
    )
    fun getSeatClassCounts(): Flow<List<SeatClassCount>>

    @Query(
        """
        SELECT substr(flightNumber, 1, 2) AS airlineCode, COUNT(*) AS count
        FROM logbook_flights
        WHERE flightNumber != ''
        GROUP BY airlineCode
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun getTopAirlines(limit: Int = 5): Flow<List<AirlineCount>>

    @Query(
        """
        SELECT * FROM logbook_flights
        WHERE distanceKm IS NOT NULL
        ORDER BY distanceKm DESC
        LIMIT 1
        """
    )
    fun getLongestFlightByDistance(): Flow<LogbookFlight?>

    @Query(
        """
        SELECT * FROM logbook_flights
        WHERE durationMinutes IS NOT NULL AND durationMinutes > 0
        ORDER BY durationMinutes DESC
        LIMIT 1
        """
    )
    fun getLongestFlightByDuration(): Flow<LogbookFlight?>

    @Query(
        """
        SELECT departureCode || '→' || arrivalCode AS label, COUNT(*) AS count
        FROM logbook_flights
        WHERE departureCode != '' AND arrivalCode != ''
        GROUP BY departureCode, arrivalCode
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun getTopRoutes(limit: Int = 5): Flow<List<RouteCount>>

    @Query(
        """
        SELECT * FROM logbook_flights
        ORDER BY departureTimeMillis ASC
        LIMIT 1
        """
    )
    fun getFirstFlight(): Flow<LogbookFlight?>

    @Query(
        """
        SELECT strftime('%Y-%m', departureTimeMillis / 1000, 'unixepoch') AS month,
               COUNT(*) AS count
        FROM logbook_flights
        GROUP BY month
        ORDER BY month ASC
        """
    )
    fun getMonthlyFlightCounts(): Flow<List<MonthlyCount>>
}

data class SeatClassCount(
    val seatClass: String,
    val count: Int
)

data class AirlineCount(
    val airlineCode: String,
    val count: Int
)

data class RouteCount(
    val label: String,
    val count: Int
)

data class MonthlyCount(
    val month: String,
    val count: Int
)
