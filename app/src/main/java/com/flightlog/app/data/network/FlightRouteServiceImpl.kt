package com.flightlog.app.data.network

import android.util.Log
import com.flightlog.app.BuildConfig
import com.flightlog.app.data.calendar.AirlineIcaoMap
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

class FlightRouteServiceImpl @Inject constructor(
    private val api: FlightAwareApi
) : FlightRouteService {

    override suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute? {
        return try {
            // Try IATA flight number first
            val route = fetchRoute(flightNumber, date)
            if (route != null) return route

            // FlightAware uses ICAO identifiers internally; some IATA codes
            // don't auto-map (e.g., JL5 fails but JAL5 works). Retry with ICAO.
            val icaoIdent = AirlineIcaoMap.toIcaoFlightNumber(flightNumber)
            if (icaoIdent != null) {
                Log.d(TAG, "IATA lookup empty for $flightNumber, retrying as $icaoIdent")
                fetchRoute(icaoIdent, date, originalFlightNumber = flightNumber)
            } else {
                null
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to look up route for $flightNumber", e)
            else Log.e(TAG, "Failed to look up route for $flightNumber")
            null
        }
    }

    /**
     * Performs a single API call and maps the result to a [FlightRoute].
     * [originalFlightNumber] preserves the user-facing IATA flight number in the
     * returned route even when the API was called with an ICAO identifier.
     */
    private suspend fun fetchRoute(
        ident: String,
        date: LocalDate,
        originalFlightNumber: String = ident
    ): FlightRoute? {
        val response = api.getFlights(
            ident = ident,
            start = date.toString()
        )

        if (!response.isSuccessful) {
            Log.w(TAG, "API returned ${response.code()} for $ident")
            return null
        }

        val flight = response.body()?.flights
            ?.firstOrNull { it.origin?.codeIata != null && it.destination?.codeIata != null }
            ?: return null

        return FlightRoute(
            flightNumber = originalFlightNumber,
            departureIata = flight.origin?.codeIata ?: return null,
            arrivalIata = flight.destination?.codeIata ?: return null,
            departureTimezone = flight.origin?.timezone,
            arrivalTimezone = flight.destination?.timezone,
            departureScheduledUtc = parseIsoToUtc(flight.scheduledOut),
            arrivalScheduledUtc = parseIsoToUtc(flight.scheduledIn),
            aircraftType = flight.aircraftType?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val TAG = "FlightRouteService"

        fun parseIsoToUtc(iso: String?): Long? =
            iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
