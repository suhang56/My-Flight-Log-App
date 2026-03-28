package com.flightlog.app.data.network

import android.util.Log
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

class FlightRouteServiceImpl @Inject constructor(
    private val api: FlightAwareApi
) : FlightRouteService {

    override suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute? {
        return try {
            val response = api.getFlights(
                ident = flightNumber,
                start = date.toString()
            )

            if (!response.isSuccessful) {
                Log.w(TAG, "API returned ${response.code()} for $flightNumber")
                return null
            }

            val flight = response.body()?.flights
                ?.firstOrNull { it.origin?.codeIata != null && it.destination?.codeIata != null }
                ?: run {
                    Log.w(TAG, "No flight data returned for $flightNumber")
                    return null
                }

            FlightRoute(
                flightNumber = flightNumber,
                departureIata = flight.origin?.codeIata ?: return null,
                arrivalIata = flight.destination?.codeIata ?: return null,
                departureTimezone = flight.origin?.timezone,
                arrivalTimezone = flight.destination?.timezone,
                departureScheduledUtc = parseIsoToUtc(flight.scheduledOut),
                arrivalScheduledUtc = parseIsoToUtc(flight.scheduledIn),
                aircraftType = flight.aircraftType?.takeIf { it.isNotBlank() }
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to look up route for $flightNumber", e)
            null
        }
    }

    companion object {
        private const val TAG = "FlightRouteService"

        fun parseIsoToUtc(iso: String?): Long? =
            iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
