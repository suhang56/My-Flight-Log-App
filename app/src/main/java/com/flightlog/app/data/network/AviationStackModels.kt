package com.flightlog.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AviationStackResponse(
    @Json(name = "data") val data: List<AviationStackFlight>?
)

@JsonClass(generateAdapter = true)
data class AviationStackFlight(
    @Json(name = "flight") val flight: AviationStackFlightCode?,
    @Json(name = "departure") val departure: AviationStackAirport?,
    @Json(name = "arrival") val arrival: AviationStackAirport?,
    @Json(name = "airline") val airline: AviationStackAirline?,
    @Json(name = "aircraft") val aircraft: AviationStackAircraft?
)

@JsonClass(generateAdapter = true)
data class AviationStackFlightCode(
    @Json(name = "iata") val iata: String?,
    @Json(name = "icao") val icao: String?
)

@JsonClass(generateAdapter = true)
data class AviationStackAirport(
    @Json(name = "iata") val iata: String?,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "scheduled") val scheduled: String?
)

@JsonClass(generateAdapter = true)
data class AviationStackAirline(
    @Json(name = "name") val name: String?,
    @Json(name = "iata") val iata: String?
)

@JsonClass(generateAdapter = true)
data class AviationStackAircraft(
    @Json(name = "modelCode") val modelCode: String?,
    @Json(name = "modelText") val modelText: String?
)
