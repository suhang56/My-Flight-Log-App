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
import com.flightlog.app.data.local.model.RouteCount
import kotlinx.coroutines.flow.Flow

@Dao
interface LogbookFlightDao {

    /** All logbook entries ordered by departure time, most recent first. */
    @Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc DESC, id DESC")
    fun getAll(): Flow<List<LogbookFlight>>

    /** All logbook entries ordered chronologically (oldest first) — one-shot for export. */
    @Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc ASC")
    suspend fun getAllOnce(): List<LogbookFlight>

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

    /** Insert or replace — used by undo-delete to restore a flight with its original ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flight: LogbookFlight): Long

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
        SELECT SUM((arrivalTimeUtc - departureTimeUtc) / 60000)
        FROM logbook_flights
        WHERE arrivalTimeUtc IS NOT NULL
          AND arrivalTimeUtc > departureTimeUtc
        """
    )
    fun getTotalDurationMinutes(): Flow<Long?>

    /** All distinct airport codes (departure + arrival), excluding empty strings. */
    @Query(
        """
        SELECT code FROM (
            SELECT departureCode AS code FROM logbook_flights WHERE departureCode != ''
            UNION
            SELECT arrivalCode AS code FROM logbook_flights WHERE arrivalCode != ''
        )
        """
    )
    fun getDistinctAirportCodes(): Flow<List<String>>

    /** Airline prefixes (2-letter IATA from flightNumber), grouped by count descending. */
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
    fun getDistinctAirlinePrefixes(): Flow<List<AirlineCount>>

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
    fun getFlightsPerMonth(): Flow<List<MonthlyCount>>

    /** Top departure airports by count, descending. */
    @Query(
        """
        SELECT departureCode AS code, COUNT(*) AS count
        FROM logbook_flights
        WHERE departureCode != ''
        GROUP BY departureCode
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun getTopDepartureAirports(limit: Int = 5): Flow<List<AirportCount>>

    /** Top arrival airports by count, descending. */
    @Query(
        """
        SELECT arrivalCode AS code, COUNT(*) AS count
        FROM logbook_flights
        WHERE arrivalCode != ''
        GROUP BY arrivalCode
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun getTopArrivalAirports(limit: Int = 5): Flow<List<AirportCount>>

    /** Flights grouped by seat class, excluding empty values. */
    @Query(
        """
        SELECT seatClass AS label, COUNT(*) AS count
        FROM logbook_flights
        WHERE seatClass IS NOT NULL AND seatClass != ''
        GROUP BY seatClass
        ORDER BY count DESC
        """
    )
    fun getSeatClassBreakdown(): Flow<List<LabelCount>>

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
    fun getLongestFlightByDistance(): Flow<LogbookFlight?>

    /** Longest flight by duration (arrival - departure). */
    @Query(
        """
        SELECT * FROM logbook_flights
        WHERE arrivalTimeUtc IS NOT NULL AND arrivalTimeUtc > departureTimeUtc
        ORDER BY (arrivalTimeUtc - departureTimeUtc) DESC LIMIT 1
        """
    )
    fun getLongestFlightByDuration(): Flow<LogbookFlight?>

    /** Top routes by frequency. */
    @Query(
        """
        SELECT departureCode, arrivalCode, COUNT(*) AS count
        FROM logbook_flights
        WHERE departureCode != '' AND arrivalCode != ''
        GROUP BY departureCode, arrivalCode
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun getTopRoutes(limit: Int = 5): Flow<List<RouteCount>>

    /** First flight ever logged by departure time. */
    @Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc ASC LIMIT 1")
    fun getFirstFlight(): Flow<LogbookFlight?>

    /** Distinct years present in the logbook (UTC-based), descending. */
    @Query("""
        SELECT DISTINCT strftime('%Y', departureTimeUtc / 1000, 'unixepoch') AS year
        FROM logbook_flights
        ORDER BY year DESC
    """)
    fun getDistinctYears(): Flow<List<String>>

    /** Distinct seat class values present in the logbook, alphabetical. */
    @Query("""
        SELECT DISTINCT seatClass
        FROM logbook_flights
        WHERE seatClass IS NOT NULL AND seatClass != ''
        ORDER BY seatClass ASC
    """)
    fun getDistinctSeatClasses(): Flow<List<String>>
}
