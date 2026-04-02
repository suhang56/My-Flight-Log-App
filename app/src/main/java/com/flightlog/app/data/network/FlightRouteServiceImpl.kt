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
        return lookupAllRoutes(flightNumber, date).firstOrNull()
    }

    override suspend fun lookupAllRoutes(flightNumber: String, date: LocalDate): List<FlightRoute> {
        return try {
            // Try IATA flight number first
            val routes = fetchAllRoutes(flightNumber, date)
            if (routes.isNotEmpty()) return routes

            // FlightAware uses ICAO identifiers internally; some IATA codes
            // don't auto-map (e.g., JL5 fails but JAL5 works). Retry with ICAO.
            val icaoIdent = AirlineIcaoMap.toIcaoFlightNumber(flightNumber)
            if (icaoIdent != null) {
                Log.d(TAG, "IATA lookup empty for $flightNumber, retrying as $icaoIdent")
                fetchAllRoutes(icaoIdent, date, originalFlightNumber = flightNumber)
            } else {
                emptyList()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to look up route for $flightNumber", e)
            else Log.e(TAG, "Failed to look up route for $flightNumber")
            emptyList()
        }
    }

    /**
     * Performs a single API call and maps all valid results to [FlightRoute]s.
     * [originalFlightNumber] preserves the user-facing IATA flight number in the
     * returned routes even when the API was called with an ICAO identifier.
     */
    private suspend fun fetchAllRoutes(
        ident: String,
        date: LocalDate,
        originalFlightNumber: String = ident
    ): List<FlightRoute> {
        val response = api.getFlights(
            ident = ident,
            start = date.toString()
        )

        if (!response.isSuccessful) {
            Log.w(TAG, "API returned ${response.code()} for $ident")
            return emptyList()
        }

        return response.body()?.flights
            ?.filter { it.origin?.codeIata != null && it.destination?.codeIata != null }
            ?.mapNotNull { flight ->
                FlightRoute(
                    flightNumber = originalFlightNumber,
                    departureIata = flight.origin?.codeIata ?: return@mapNotNull null,
                    arrivalIata = flight.destination?.codeIata ?: return@mapNotNull null,
                    departureTimezone = flight.origin?.timezone,
                    arrivalTimezone = flight.destination?.timezone,
                    departureScheduledUtc = parseIsoToUtc(flight.scheduledOut),
                    arrivalScheduledUtc = parseIsoToUtc(flight.scheduledIn),
                    aircraftType = flight.aircraftType?.takeIf { it.isNotBlank() }
                )
            } ?: emptyList()
    }

    companion object {
        private const val TAG = "FlightRouteService"

        fun parseIsoToUtc(iso: String?): Long? =
            iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
