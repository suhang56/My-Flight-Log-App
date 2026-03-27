package com.flightlog.app.data.calendar

/**
 * Raw calendar event as read from the device ContentProvider before any flight-specific parsing.
 */
data class RawCalendarEvent(
    val eventId: Long,
    val title: String,
    val description: String,
    val location: String,
    val dtStart: Long,   // epoch millis (DTSTART)
    val dtEnd: Long?     // epoch millis (DTEND); null when absent or zero
)
