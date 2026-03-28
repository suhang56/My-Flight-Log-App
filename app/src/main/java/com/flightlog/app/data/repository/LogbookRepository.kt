package com.flightlog.app.data.repository

import android.content.Context
import com.flightlog.app.data.backup.AutoBackupWorker
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.RouteCount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogbookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logbookFlightDao: LogbookFlightDao,
    private val airportRepository: AirportRepository,
    private val achievementRepository: AchievementRepository
) {

    fun getAll(): Flow<List<LogbookFlight>> = logbookFlightDao.getAll()

    suspend fun getAllOnce(): List<LogbookFlight> = logbookFlightDao.getAllOnce()

    suspend fun getById(id: Long): LogbookFlight? = logbookFlightDao.getById(id)

    fun getByIdFlow(id: Long): Flow<LogbookFlight?> = logbookFlightDao.getByIdFlow(id)

    fun getCount(): Flow<Int> = logbookFlightDao.getCount()

    fun getTotalDistanceNm(): Flow<Int> = logbookFlightDao.getTotalDistanceNm()

    suspend fun getMostRecentFlight(): LogbookFlight? = logbookFlightDao.getMostRecentFlight()

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
        val distance = airportRepository.distanceNm(
            calendarFlight.departureCode,
            calendarFlight.arrivalCode
        )

        // Null-out arrival time if it's not after departure (bad calendar data / cross-midnight issue)
        val safeArrivalUtc = calendarFlight.endTime?.takeIf { it > calendarFlight.scheduledTime }

        val logbookFlight = LogbookFlight(
            sourceCalendarEventId = calendarFlight.calendarEventId,
            sourceLegIndex = calendarFlight.legIndex,
            flightNumber = calendarFlight.flightNumber,
            departureCode = calendarFlight.departureCode,
            arrivalCode = calendarFlight.arrivalCode,
            departureTimeUtc = calendarFlight.scheduledTime,
            arrivalTimeUtc = safeArrivalUtc,
            departureTimezone = calendarFlight.departureTimezone,
            arrivalTimezone = calendarFlight.arrivalTimezone,
            distanceNm = distance
        )

        val rowId = logbookFlightDao.insert(logbookFlight)
        if (rowId != -1L) try { achievementRepository.checkAndUnlock() } catch (_: Exception) { /* Achievement evaluation is a side effect — never block the primary operation */ }
        if (rowId != -1L) AutoBackupWorker.enqueueIfSignedIn(context)
        return rowId
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

    suspend fun insert(flight: LogbookFlight): Long {
        val rowId = logbookFlightDao.insert(flight)
        if (rowId != -1L) try { achievementRepository.checkAndUnlock() } catch (_: Exception) { /* Achievement evaluation is a side effect — never block the primary operation */ }
        if (rowId != -1L) AutoBackupWorker.enqueueIfSignedIn(context)
        return rowId
    }

    /** Batch insert for restore — bypasses per-flight achievement/backup triggers. */
    suspend fun insertAllForRestore(flights: List<LogbookFlight>): List<Long> =
        logbookFlightDao.insertAll(flights)

    /** Trigger achievement evaluation (exposed for batch operations like restore). */
    suspend fun checkAchievements() {
        try { achievementRepository.checkAndUnlock() } catch (_: Exception) { }
    }

    /** Insert or replace — preserves the original ID on undo-delete. */
    suspend fun upsert(flight: LogbookFlight): Long {
        val rowId = logbookFlightDao.upsert(flight)
        try { achievementRepository.checkAndUnlock() } catch (_: Exception) { /* Achievement evaluation is a side effect — never block the primary operation */ }
        AutoBackupWorker.enqueueIfSignedIn(context)
        return rowId
    }

    suspend fun update(flight: LogbookFlight) {
        logbookFlightDao.update(flight.copy(updatedAt = System.currentTimeMillis()))
        try { achievementRepository.checkAndUnlock() } catch (_: Exception) { /* Achievement evaluation is a side effect — never block the primary operation */ }
        AutoBackupWorker.enqueueIfSignedIn(context)
    }

    suspend fun delete(id: Long) {
        logbookFlightDao.deleteById(id)
        AutoBackupWorker.enqueueIfSignedIn(context)
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    fun getTotalDurationMinutes(): Flow<Long?> = logbookFlightDao.getTotalDurationMinutes()

    fun getDistinctAirportCodes(): Flow<List<String>> = logbookFlightDao.getDistinctAirportCodes()

    fun getFlightsPerMonth(): Flow<List<MonthlyCount>> = logbookFlightDao.getFlightsPerMonth()

    fun getTopDepartureAirports(limit: Int = 5): Flow<List<AirportCount>> =
        logbookFlightDao.getTopDepartureAirports(limit)

    fun getTopArrivalAirports(limit: Int = 5): Flow<List<AirportCount>> =
        logbookFlightDao.getTopArrivalAirports(limit)

    /** Combines departure and arrival airport counts, sorted by total count descending. */
    fun getTopAirports(limit: Int = 5): Flow<List<AirportCount>> =
        combine(
            logbookFlightDao.getTopDepartureAirports(limit * 2),
            logbookFlightDao.getTopArrivalAirports(limit * 2)
        ) { dep, arr ->
            val combined = mutableMapOf<String, Int>()
            dep.forEach { combined[it.code] = (combined[it.code] ?: 0) + it.count }
            arr.forEach { combined[it.code] = (combined[it.code] ?: 0) + it.count }
            combined.entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { AirportCount(it.key, it.value) }
        }

    fun getDistinctAirlinePrefixes(): Flow<List<AirlineCount>> =
        logbookFlightDao.getDistinctAirlinePrefixes()

    fun getSeatClassBreakdown(): Flow<List<LabelCount>> = logbookFlightDao.getSeatClassBreakdown()

    fun getAircraftTypeDistribution(): Flow<List<LabelCount>> =
        logbookFlightDao.getAircraftTypeDistribution()

    fun getLongestFlightByDistance(): Flow<LogbookFlight?> =
        logbookFlightDao.getLongestFlightByDistance()

    fun getLongestFlightByDuration(): Flow<LogbookFlight?> =
        logbookFlightDao.getLongestFlightByDuration()

    fun getTopRoutes(limit: Int = 5): Flow<List<RouteCount>> =
        logbookFlightDao.getTopRoutes(limit)

    fun getFirstFlight(): Flow<LogbookFlight?> =
        logbookFlightDao.getFirstFlight()

    fun getDistinctYears(): Flow<List<String>> = logbookFlightDao.getDistinctYears()

    fun getDistinctSeatClasses(): Flow<List<String>> = logbookFlightDao.getDistinctSeatClasses()
}
