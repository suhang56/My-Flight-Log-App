package com.flightlog.app.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AviationStackApi {

    @GET("flightsFuture")
    suspend fun getScheduledFlights(
        @Query("access_key") accessKey: String,
        @Query("iataCode") iataCode: String,
        @Query("type") type: String,
        @Query("date") date: String,
        @Query("flight_iata") flightIata: String? = null
    ): Response<AviationStackResponse>
}
