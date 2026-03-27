package com.flightlog.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a flight event synced from the device calendar.
 *
 * [calendarEventId] is the natural key sourced from CalendarContract.Events._ID and
 * carries a unique index used for upsert conflict resolution and stale-ID removal.
 *
 * [scheduledTime] (DTSTART in epoch millis) is indexed separately for efficient
 * upcoming/past partition queries.
 */
@Entity(
    tableName = "calendar_flights",
    indices = [
        Index(value = ["calendarEventId", "legIndex"], unique = true),
        Index(value = ["scheduledTime"])
    ]
)
data class CalendarFlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** CalendarContract.Events._ID — unique identifier from the device calendar. */
    @ColumnInfo(name = "calendarEventId")
    val calendarEventId: Long,

    /** Zero-based leg index within a multi-leg flight. Single-leg flights use 0. */
    val legIndex: Int = 0,

    /**
     * IATA/ICAO flight number parsed from the event title, e.g. "AA0011".
     * Empty string when no flight number could be extracted.
     */
    val flightNumber: String = "",

    /** Departure airport code, e.g. "ORD". */
    val departureCode: String,

    /** Arrival airport code, e.g. "CMH". */
    val arrivalCode: String,

    /** Original calendar event title — preserved for display and future re-parsing. */
    val rawTitle: String,

    /** DTSTART epoch millis. Indexed for fast upcoming/past queries. */
    @ColumnInfo(name = "scheduledTime")
    val scheduledTime: Long,

    /** DTEND epoch millis. Null for zero-duration or all-day events lacking an end time. */
    val endTime: Long? = null,

    /** Epoch millis when this row was last written by the sync worker. */
    val syncedAt: Long = System.currentTimeMillis(),

    /** IANA timezone of the departure airport, e.g. "America/Chicago". */
    val departureTimezone: String? = null,

    /** IANA timezone of the arrival airport, e.g. "America/New_York". */
    val arrivalTimezone: String? = null,

    /** When true the card is hidden from lists without deleting the row. */
    val isManuallyDismissed: Boolean = false
)
