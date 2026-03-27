package com.flightlog.app.data.network

import java.time.LocalDate

interface FlightRouteService {
    suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute?
}

data class FlightRoute(
    val flightNumber: String,
    val departureIata: String,
    val arrivalIata: String,
    val departureTimezone: String? = null,
    val arrivalTimezone: String? = null,
    val departureScheduledUtc: Long? = null,
    val arrivalScheduledUtc: Long? = null,
    val aircraftType: String? = null
)
