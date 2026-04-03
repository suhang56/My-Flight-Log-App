package com.flightlog.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FlightAwareApi {

    @GET("flights/{ident}")
    suspend fun getFlights(
        @Path("ident") ident: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("max_pages") maxPages: Int = 1
    ): Response<FlightAwareFlightsResponse>

    @GET("flights/{ident}/position")
    suspend fun getPosition(
        @Path("ident") ident: String
    ): Response<FlightAwarePositionResponse>
}

// -- Response models ----------------------------------------------------------

@JsonClass(generateAdapter = true)
data class FlightAwareFlightsResponse(
    @Json(name = "flights") val flights: List<FlightAwareFlight>?
)

@JsonClass(generateAdapter = true)
data class FlightAwareFlight(
    @Json(name = "ident_iata") val identIata: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "departure_delay") val departureDelay: Int?,
    @Json(name = "arrival_delay") val arrivalDelay: Int?,
    @Json(name = "origin") val origin: FlightAwareAirport?,
    @Json(name = "destination") val destination: FlightAwareAirport?,
    @Json(name = "scheduled_out") val scheduledOut: String?,
    @Json(name = "estimated_out") val estimatedOut: String?,
    @Json(name = "actual_out") val actualOut: String?,
    @Json(name = "scheduled_in") val scheduledIn: String?,
    @Json(name = "estimated_in") val estimatedIn: String?,
    @Json(name = "actual_in") val actualIn: String?,
    @Json(name = "gate_origin") val gateOrigin: String?,
    @Json(name = "gate_destination") val gateDestination: String?,
    @Json(name = "aircraft_type") val aircraftType: String?,
    @Json(name = "registration") val registration: String? = null
)

@JsonClass(generateAdapter = true)
data class FlightAwareAirport(
    @Json(name = "code_iata") val codeIata: String?,
    @Json(name = "timezone") val timezone: String?
)

@JsonClass(generateAdapter = true)
data class FlightAwarePositionResponse(
    @Json(name = "last_position") val lastPosition: FlightAwareLivePosition?
)

@JsonClass(generateAdapter = true)
data class FlightAwareLivePosition(
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "altitude") val altitude: Int?,
    @Json(name = "groundspeed") val groundspeed: Int?,
    @Json(name = "heading") val heading: Int?,
    @Json(name = "timestamp") val timestamp: String?
)

// -- Status enum --------------------------------------------------------------

enum class FlightStatusEnum {
    SCHEDULED, BOARDING, DEPARTED, EN_ROUTE, LANDED, CANCELLED, DIVERTED, UNKNOWN
}

fun String?.toFlightStatusEnum(): FlightStatusEnum = when {
    this == null -> FlightStatusEnum.UNKNOWN
    startsWith("Scheduled") -> FlightStatusEnum.SCHEDULED
    startsWith("En Route") -> FlightStatusEnum.EN_ROUTE
    contains("Departed") -> FlightStatusEnum.DEPARTED
    contains("Arrived") || contains("Landed") -> FlightStatusEnum.LANDED
    contains("Cancelled") -> FlightStatusEnum.CANCELLED
    contains("Diverted") -> FlightStatusEnum.DIVERTED
    contains("Boarding") -> FlightStatusEnum.BOARDING
    else -> FlightStatusEnum.UNKNOWN
}
