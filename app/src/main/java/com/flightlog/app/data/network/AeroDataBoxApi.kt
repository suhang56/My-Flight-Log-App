package com.flightlog.app.data.network

import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AviationStackApi {

    @GET("v1/flights")
    suspend fun getFlightByNumber(
        @Query("access_key") accessKey: String,
        @Query("flight_iata") flightIata: String
    ): Response<AviationStackResponse>
}

data class AviationStackResponse(
    @Json(name = "data") val data: List<AviationStackFlight>?
)

data class AviationStackFlight(
    @Json(name = "departure") val departure: AviationStackEndpoint?,
    @Json(name = "arrival") val arrival: AviationStackEndpoint?
)

data class AviationStackEndpoint(
    @Json(name = "iata") val iata: String?
)
