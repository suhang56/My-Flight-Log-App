package com.flightlog.app.data.network

import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class FlightRouteServiceImpl @Inject constructor(
    private val api: AeroDataBoxApi
) : FlightRouteService {

    override suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute? {
        return try {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val response = api.getFlightByNumber(flightNumber, dateString)

            if (!response.isSuccessful) {
                Log.w(TAG, "API returned ${response.code()} for $flightNumber on $dateString")
                return null
            }

            val flight = response.body()?.firstOrNull() ?: return null
            val departureIata = flight.departure?.airport?.iata
            val arrivalIata = flight.arrival?.airport?.iata

            if (departureIata != null && arrivalIata != null) {
                FlightRoute(
                    flightNumber = flightNumber,
                    departureIata = departureIata,
                    arrivalIata = arrivalIata
                )
            } else {
                Log.w(TAG, "Missing IATA codes in response for $flightNumber")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to look up route for $flightNumber", e)
            null
        }
    }

    companion object {
        private const val TAG = "FlightRouteService"
    }
}
