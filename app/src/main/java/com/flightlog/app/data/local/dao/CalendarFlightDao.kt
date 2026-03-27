package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
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
     * Insert-or-replace all [flights] in a single transaction.
     * Conflicts on the `calendarEventId` unique index cause a replace, effectively
     * updating existing rows while preserving the autoGenerate primary key sequence.
     *
     * NOTE: This raw upsert overwrites ALL columns including isManuallyDismissed.
     * Use [upsertPreservingDismissed] in sync flows to avoid un-dismissing flights.
     */
    @Upsert
    suspend fun upsertAll(flights: List<CalendarFlight>)

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

    /** Soft-delete: hides a flight card without removing historical data. */
    @Query("UPDATE calendar_flights SET isManuallyDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    /** Dismiss all legs belonging to the same calendar event (multi-leg flights). */
    @Query("UPDATE calendar_flights SET isManuallyDismissed = 1 WHERE calendarEventId = :eventId")
    suspend fun dismissAllLegsForEvent(eventId: Long)

    @Query("SELECT COUNT(*) FROM calendar_flights WHERE isManuallyDismissed = 0")
    fun getVisibleCount(): Flow<Int>
}
