package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.flightlog.app.data.local.entity.CalendarFlight
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarFlightDao {

    /**
     * All non-dismissed flights ordered by departure time, most-recent first.
     * Suitable for a combined "all flights" list.
     */
    @Query(
        """
        SELECT * FROM calendar_flights
        WHERE isManuallyDismissed = 0
        ORDER BY scheduledTime DESC
        """
    )
    fun getAllVisible(): Flow<List<CalendarFlight>>

    /**
     * Non-dismissed flights departing on or after [now], soonest first.
     * Pass [now] = System.currentTimeMillis() at the call site to snapshot the
     * current moment; the Flow re-emits whenever rows change, not on timer ticks.
     */
    @Query(
        """
        SELECT * FROM calendar_flights
        WHERE isManuallyDismissed = 0
          AND scheduledTime >= :now
        ORDER BY scheduledTime ASC
        """
    )
    fun getUpcoming(now: Long): Flow<List<CalendarFlight>>

    /**
     * Non-dismissed flights that have already departed (scheduledTime < [now]),
     * most-recent first.
     */
    @Query(
        """
        SELECT * FROM calendar_flights
        WHERE isManuallyDismissed = 0
          AND scheduledTime < :now
        ORDER BY scheduledTime DESC
        """
    )
    fun getPast(now: Long): Flow<List<CalendarFlight>>

    @Query("SELECT * FROM calendar_flights WHERE id = :id")
    suspend fun getById(id: Long): CalendarFlight?

    /**
     * Low-level upsert keyed on the autoGenerate primary key.
     * Callers must set [CalendarFlight.id] to the existing row's id (or 0 for new
     * rows) before calling — otherwise autoGenerate always INSERTs, hitting the
     * unique constraint on (calendarEventId, legIndex).
     *
     * Prefer [upsertAll] which resolves existing IDs automatically.
     */
    @Upsert
    suspend fun upsertAllRaw(flights: List<CalendarFlight>)

    /** Returns the id for an existing row matching [calendarEventId] and [legIndex], or null. */
    @Query("SELECT id FROM calendar_flights WHERE calendarEventId = :calendarEventId AND legIndex = :legIndex")
    suspend fun findIdByEventAndLeg(calendarEventId: Long, legIndex: Int): Long?

    /**
     * Insert-or-update all [flights] in a single transaction.
     * Resolves existing primary-key IDs via the (calendarEventId, legIndex) unique
     * index so that @Upsert correctly UPDATEs rather than always INSERTing.
     */
    @Transaction
    suspend fun upsertAll(flights: List<CalendarFlight>) {
        val resolved = flights.map { flight ->
            val existingId = findIdByEventAndLeg(flight.calendarEventId, flight.legIndex)
            if (existingId != null) flight.copy(id = existingId) else flight
        }
        upsertAllRaw(resolved)
    }

    /**
     * Returns the set of calendarEventIds that have been manually dismissed.
     * Used during sync to preserve dismissed state across upserts.
     */
    @Query("SELECT calendarEventId FROM calendar_flights WHERE isManuallyDismissed = 1")
    suspend fun getDismissedCalendarEventIds(): List<Long>

    /** Returns the full set of calendarEventIds currently in the table. */
    @Query("SELECT calendarEventId FROM calendar_flights")
    suspend fun getAllCalendarEventIds(): List<Long>

    /**
     * Deletes rows whose [calendarEventId] is not in [validIds].
     *
     * IMPORTANT: the caller must ensure [validIds] is non-empty before calling this.
     * Room does not support empty IN-list parameters and the query would delete all rows.
     */
    @Query(
        """
        DELETE FROM calendar_flights
        WHERE calendarEventId NOT IN (:validIds)
        """
    )
    suspend fun removeStaleIds(validIds: List<Long>)

    /**
     * Removes rows whose calendarEventId is in [eventIds].
     * Used to clean up rows for calendar events that still exist but no longer
     * parse as flights (e.g. after a parser fix rejects false positives).
     */
    @Query("DELETE FROM calendar_flights WHERE calendarEventId IN (:eventIds)")
    suspend fun removeByEventIds(eventIds: List<Long>)

    /** Soft-delete: hides a flight card without removing historical data. */
    @Query("UPDATE calendar_flights SET isManuallyDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    /** Dismiss all legs belonging to the same calendar event (multi-leg flights). */
    @Query("UPDATE calendar_flights SET isManuallyDismissed = 1 WHERE calendarEventId = :eventId")
    suspend fun dismissAllLegsForEvent(eventId: Long)

    @Query("SELECT COUNT(*) FROM calendar_flights WHERE isManuallyDismissed = 0")
    fun getVisibleCount(): Flow<Int>
}
