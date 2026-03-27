package com.flightlog.app.data.network

import com.squareup.moshi.Json

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface AeroDataBoxApi {

    @GET("flights/number/{flightNumber}/{date}")
    suspend fun getFlightByNumber(
        @Path("flightNumber") flightNumber: String,
        @Path("date") date: String // yyyy-MM-dd
    ): Response<List<AeroDataBoxFlight>>
}

data class AeroDataBoxFlight(
    @Json(name = "departure") val departure: AeroDataBoxEndpoint?,
    @Json(name = "arrival") val arrival: AeroDataBoxEndpoint?
)

data class AeroDataBoxEndpoint(
    @Json(name = "airport") val airport: AeroDataBoxAirport?
)

data class AeroDataBoxAirport(
    @Json(name = "iata") val iata: String?
)
