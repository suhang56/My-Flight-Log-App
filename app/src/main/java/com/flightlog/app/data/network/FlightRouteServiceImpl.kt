package com.flightlog.app.data.network

import android.util.Log
import com.flightlog.app.BuildConfig
import java.time.LocalDate
import javax.inject.Inject

class FlightRouteServiceImpl @Inject constructor(
    private val api: AviationStackApi
) : FlightRouteService {

    override suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute? {
        return try {
            val response = api.getFlightByNumber(
                accessKey = BuildConfig.AVIATION_STACK_KEY,
                flightIata = flightNumber,
                flightDate = date.toString()
            )

            if (!response.isSuccessful) {
                Log.w(TAG, "API returned ${response.code()} for $flightNumber")
                return null
            }

            val flight = response.body()?.data?.firstOrNull() ?: run {
                Log.w(TAG, "No flight data returned for $flightNumber")
                return null
            }

            val departureIata = flight.departure?.iata
            val arrivalIata = flight.arrival?.iata

            if (departureIata != null && arrivalIata != null) {
                FlightRoute(
                    flightNumber = flightNumber,
                    departureIata = departureIata,
                    arrivalIata = arrivalIata,
                    departureTimezone = flight.departure?.timezone,
                    arrivalTimezone = flight.arrival?.timezone
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
