package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
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

    // ── Statistics queries ───────────────────────────────────────────────────────

    /** Total flight time in minutes (sum of arrival - departure for flights with both times). */
    @Query(
        """
        SELECT COALESCE(SUM((arrivalTimeUtc - departureTimeUtc) / 60000), 0)
        FROM logbook_flights
        WHERE arrivalTimeUtc IS NOT NULL
        """
    )
    fun getTotalFlightTimeMinutes(): Flow<Long>

    /** Number of distinct airports visited (as departure or arrival), excluding empty codes. */
    @Query(
        """
        SELECT COUNT(DISTINCT code) FROM (
            SELECT departureCode AS code FROM logbook_flights WHERE departureCode != ''
            UNION
            SELECT arrivalCode AS code FROM logbook_flights WHERE arrivalCode != ''
        )
        """
    )
    fun getUniqueAirportCount(): Flow<Int>

    /** Flights grouped by year-month (YYYY-MM), ordered chronologically. */
    @Query(
        """
        SELECT strftime('%Y-%m', departureTimeUtc / 1000, 'unixepoch') AS yearMonth,
               COUNT(*) AS count
        FROM logbook_flights
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """
    )
    fun getMonthlyFlightCounts(): Flow<List<MonthlyCount>>

    /** Top airports by visit count (departure + arrival combined), descending. */
    @Query(
        """
        SELECT code, SUM(cnt) AS count FROM (
            SELECT departureCode AS code, COUNT(*) AS cnt
            FROM logbook_flights WHERE departureCode != ''
            GROUP BY departureCode
            UNION ALL
            SELECT arrivalCode AS code, COUNT(*) AS cnt
            FROM logbook_flights WHERE arrivalCode != ''
            GROUP BY arrivalCode
        )
        GROUP BY code
        ORDER BY count DESC
        """
    )
    fun getTopAirports(): Flow<List<AirportCount>>

    /** Top airlines by 2-letter IATA prefix of flightNumber, excluding empty flight numbers. */
    @Query(
        """
        SELECT UPPER(SUBSTR(flightNumber, 1, 2)) AS airline,
               COUNT(*) AS count
        FROM logbook_flights
        WHERE LENGTH(flightNumber) >= 2 AND flightNumber != ''
        GROUP BY airline
        ORDER BY count DESC
        """
    )
    fun getTopAirlines(): Flow<List<AirlineCount>>

    /** Flights grouped by seat class, excluding empty values. */
    @Query(
        """
        SELECT seatClass AS label, COUNT(*) AS count
        FROM logbook_flights
        WHERE seatClass != ''
        GROUP BY seatClass
        ORDER BY count DESC
        """
    )
    fun getSeatClassDistribution(): Flow<List<LabelCount>>

    /** Flights grouped by aircraft type, excluding empty values. */
    @Query(
        """
        SELECT aircraftType AS label, COUNT(*) AS count
        FROM logbook_flights
        WHERE aircraftType != ''
        GROUP BY aircraftType
        ORDER BY count DESC
        """
    )
    fun getAircraftTypeDistribution(): Flow<List<LabelCount>>

    /** Longest flight by distance. */
    @Query("SELECT * FROM logbook_flights WHERE distanceNm IS NOT NULL ORDER BY distanceNm DESC LIMIT 1")
    fun getLongestFlight(): Flow<LogbookFlight?>
}
