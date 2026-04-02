package com.flightlog.app.ui.home

import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight

/**
 * Merges CalendarFlight and LogbookFlight into a single chronological list.
 * When a flight exists in both calendar and logbook (linked by sourceCalendarEventId),
 * only the logbook version is shown.
 */
sealed class UnifiedFlightItem {

    /** Epoch millis used for chronological sorting. */
    abstract val sortKey: Long

    abstract val flightNumber: String
    abstract val departureCode: String
    abstract val arrivalCode: String
    abstract val departureTimezone: String?

    data class FromCalendar(val flight: CalendarFlight) : UnifiedFlightItem() {
        override val sortKey: Long get() = flight.scheduledTime
        override val flightNumber: String get() = flight.flightNumber
        override val departureCode: String get() = flight.departureCode
        override val arrivalCode: String get() = flight.arrivalCode
        override val departureTimezone: String? get() = flight.departureTimezone
    }

    data class FromLogbook(val flight: LogbookFlight) : UnifiedFlightItem() {
        override val sortKey: Long get() = flight.departureTimeUtc
        override val flightNumber: String get() = flight.flightNumber
        override val departureCode: String get() = flight.departureCode
        override val arrivalCode: String get() = flight.arrivalCode
        override val departureTimezone: String? get() = flight.departureTimezone
    }

    companion object {
        /**
         * Merges calendar and logbook flights, de-duplicating by preferring logbook versions.
         * A calendar flight is considered a duplicate if any logbook flight has a matching
         * sourceCalendarEventId + sourceLegIndex.
         */
        fun merge(
            calendarFlights: List<CalendarFlight>,
            logbookFlights: List<LogbookFlight>
        ): List<UnifiedFlightItem> {
            val loggedCalendarKeys = logbookFlights
                .filter { it.sourceCalendarEventId != null }
                .map { it.sourceCalendarEventId!! to (it.sourceLegIndex ?: 0) }
                .toSet()

            val fromLogbook = logbookFlights.map { FromLogbook(it) }
            val fromCalendar = calendarFlights
                .filter { cal ->
                    (cal.calendarEventId to cal.legIndex) !in loggedCalendarKeys
                }
                .map { FromCalendar(it) }

            return (fromLogbook + fromCalendar).sortedByDescending { it.sortKey }
        }
    }
}
