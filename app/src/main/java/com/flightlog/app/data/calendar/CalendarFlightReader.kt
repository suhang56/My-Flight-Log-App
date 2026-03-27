package com.flightlog.app.data.calendar

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import com.flightlog.app.data.local.entity.FlightEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val description: String,
    val dtStart: Long,
    val dtEnd: Long,
    val location: String
)

@Singleton
class CalendarFlightReader @Inject constructor() {

    companion object {
        // Matches patterns like: "Flight AA0011 ORD-CMH", "AA 123 NRT-HND", "Flight NH811 NRT - LAX"
        private val FLIGHT_PATTERN = Regex(
            """(?:Flight\s+)?([A-Z]{2}\s?\d{1,4})\s+([A-Z]{3})\s*[-–]\s*([A-Z]{3})""",
            RegexOption.IGNORE_CASE
        )

        // Broader pattern: just flight number in title
        private val FLIGHT_NUMBER_PATTERN = Regex(
            """(?:Flight\s+)?([A-Z]{2})\s?(\d{1,4})""",
            RegexOption.IGNORE_CASE
        )

        // Airport pair pattern in title or location
        private val AIRPORT_PAIR_PATTERN = Regex(
            """([A-Z]{3})\s*[-–>]\s*([A-Z]{3})"""
        )

        private val PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
    }

    fun readFlightEvents(
        contentResolver: ContentResolver,
        lookBackDays: Long = 90,
        lookAheadDays: Long = 365
    ): List<FlightEntity> {
        val now = Instant.now()
        val startMillis = now.minusSeconds(lookBackDays * 86400).toEpochMilli()
        val endMillis = now.plusSeconds(lookAheadDays * 86400).toEpochMilli()

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

        val events = mutableListOf<CalendarEvent>()

        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

            while (it.moveToNext()) {
                events.add(
                    CalendarEvent(
                        eventId = it.getLong(idIdx),
                        title = it.getString(titleIdx).orEmpty(),
                        description = it.getString(descIdx).orEmpty(),
                        dtStart = it.getLong(startIdx),
                        dtEnd = it.getLong(endIdx),
                        location = it.getString(locIdx).orEmpty()
                    )
                )
            }
        }

        return events.mapNotNull { event -> parseFlightEvent(event) }
    }

    private fun parseFlightEvent(event: CalendarEvent): FlightEntity? {
        val searchText = "${event.title} ${event.description} ${event.location}"

        // Try full pattern first: "Flight AA0011 ORD-CMH"
        val fullMatch = FLIGHT_PATTERN.find(searchText)
        if (fullMatch != null) {
            val flightNumber = fullMatch.groupValues[1].replace(" ", "").uppercase()
            val departure = fullMatch.groupValues[2].uppercase()
            val arrival = fullMatch.groupValues[3].uppercase()
            val airline = flightNumber.take(2)

            return FlightEntity(
                calendarEventId = event.eventId,
                flightNumber = flightNumber,
                airline = airline,
                departureAirport = departure,
                arrivalAirport = arrival,
                departureTime = Instant.ofEpochMilli(event.dtStart),
                arrivalTime = Instant.ofEpochMilli(event.dtEnd),
                title = event.title,
                notes = event.description
            )
        }

        // Try partial: flight number + separate airport pair
        val flightMatch = FLIGHT_NUMBER_PATTERN.find(searchText)
        val airportMatch = AIRPORT_PAIR_PATTERN.find(searchText)

        if (flightMatch != null && airportMatch != null) {
            val airlineCode = flightMatch.groupValues[1].uppercase()
            val number = flightMatch.groupValues[2]
            val flightNumber = "$airlineCode$number"
            val departure = airportMatch.groupValues[1].uppercase()
            val arrival = airportMatch.groupValues[2].uppercase()

            return FlightEntity(
                calendarEventId = event.eventId,
                flightNumber = flightNumber,
                airline = airlineCode,
                departureAirport = departure,
                arrivalAirport = arrival,
                departureTime = Instant.ofEpochMilli(event.dtStart),
                arrivalTime = Instant.ofEpochMilli(event.dtEnd),
                title = event.title,
                notes = event.description
            )
        }

        return null
    }
}
