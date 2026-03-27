package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A flight that the user has explicitly added to their personal logbook.
 *
 * Created from a [CalendarFlight] via the "Add to Logbook" action.
 * The [sourceCalendarEventId] + [sourceLegIndex] pair links back to the original
 * calendar row and prevents duplicate imports.
 */
@Entity(
    tableName = "logbook_flights",
    indices = [
        Index(value = ["sourceCalendarEventId", "sourceLegIndex"], unique = true),
        Index(value = ["departureTimeUtc"])
    ]
)
data class LogbookFlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Links to [CalendarFlight.calendarEventId]; null if manually created (future). */
    val sourceCalendarEventId: Long? = null,

    /** Links to [CalendarFlight.legIndex]; null if manually created. */
    val sourceLegIndex: Int? = null,

    /** IATA/ICAO flight number, e.g. "AA11". */
    val flightNumber: String = "",

    /** Departure airport IATA code, e.g. "ORD". */
    val departureCode: String,

    /** Arrival airport IATA code, e.g. "CMH". */
    val arrivalCode: String,

    /** Departure time in UTC epoch millis. */
    val departureTimeUtc: Long,

    /** Arrival time in UTC epoch millis; null when unknown. */
    val arrivalTimeUtc: Long? = null,

    /** IANA timezone of departure airport, e.g. "America/Chicago". */
    val departureTimezone: String? = null,

    /** IANA timezone of arrival airport, e.g. "America/New_York". */
    val arrivalTimezone: String? = null,

    /** Great-circle distance in nautical miles; null when coordinates are unknown. */
    val distanceNm: Int? = null,

    /** User-editable notes. */
    val notes: String = "",

    /** Epoch millis when this row was added to the logbook. */
    val addedAt: Long = System.currentTimeMillis()
)
