package com.flightlog.app.data.local.model

/** Flights per calendar month, e.g. "2026-03" -> 5. */
data class MonthlyCount(
    val yearMonth: String,
    val count: Int
)

/** Flights per airport code (departure or arrival). */
data class AirportCount(
    val code: String,
    val count: Int
)

/** Flights per airline prefix (2-letter IATA, e.g. "AA", "NH"). */
data class AirlineCount(
    val code: String,
    val count: Int
)

/** Generic label+count for seat class, aircraft type, etc. */
data class LabelCount(
    val label: String,
    val count: Int
)
