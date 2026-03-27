package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flightlog.app.data.local.entity.LogbookFlight
import kotlinx.coroutines.flow.Flow

@Dao
interface LogbookFlightDao {

    /** All logbook entries ordered by departure time, most recent first. */
    @Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc DESC, id DESC")
    fun getAll(): Flow<List<LogbookFlight>>

    @Query("SELECT * FROM logbook_flights WHERE id = :id")
    suspend fun getById(id: Long): LogbookFlight?

    /**
     * Returns true if a logbook entry already exists for the given calendar source.
     * Used to prevent duplicate "Add to Logbook" actions.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM logbook_flights
            WHERE sourceCalendarEventId = :calendarEventId
              AND sourceLegIndex = :legIndex
        )
        """
    )
    suspend fun existsBySource(calendarEventId: Long, legIndex: Int): Boolean

    /**
     * Returns true if a flight exists with the same route and departure date (UTC day).
     * Used to warn about potential manual duplicates.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM logbook_flights
            WHERE departureCode = :depCode
              AND arrivalCode = :arrCode
              AND departureTimeUtc / 86400000 = :utcDay
              AND (:excludeId IS NULL OR id != :excludeId)
        )
        """
    )
    suspend fun existsByRouteAndDate(depCode: String, arrCode: String, utcDay: Long, excludeId: Long? = null): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(flight: LogbookFlight): Long

    @Update
    suspend fun update(flight: LogbookFlight)

    @Query("DELETE FROM logbook_flights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM logbook_flights")
    fun getCount(): Flow<Int>

    /** Sum of all known distances in nautical miles. */
    @Query("SELECT COALESCE(SUM(distanceNm), 0) FROM logbook_flights")
    fun getTotalDistanceNm(): Flow<Int>
}
