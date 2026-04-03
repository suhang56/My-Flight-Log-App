package com.flightlog.app.data.network

import android.util.Log
import com.flightlog.app.BuildConfig
import com.flightlog.app.data.calendar.AirlineIcaoMap
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
     * Fetches routes using a multi-strategy approach to handle FlightAware's
     * limited date window. The API returns 400 for dates too far in the future.
     *
     * Strategy:
     * 1. For recent/future flights (within 7 days): call without date params first,
     *    then filter results client-side to match the target date.
     * 2. If no results: fall back to a date-bounded query (start + end params).
     * 3. If the date-bounded query returns 400: retry without date params as last resort.
     */
    private suspend fun fetchAllRoutes(
        ident: String,
        date: LocalDate,
        originalFlightNumber: String = ident
    ): List<FlightRoute> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val daysDiff = date.toEpochDay() - today.toEpochDay()
        val isRecentOrFuture = daysDiff >= -RECENT_DAYS_THRESHOLD

        // Strategy 1: For recent/future dates, try without date params first.
        // The undated endpoint returns recent + scheduled flights without 400 risk.
        if (isRecentOrFuture) {
            val undatedRoutes = callApi(ident, start = null, end = null, originalFlightNumber)
            val matched = filterByDate(undatedRoutes, date)
            if (matched.isNotEmpty()) return matched
        }

        // Strategy 2: Date-bounded query for historical or when undated had no match.
        val startDate = date.toString()
        val endDate = date.plusDays(1).toString()
        val datedResponse = api.getFlights(ident = ident, start = startDate, end = endDate)

        if (datedResponse.isSuccessful) {
            val routes = mapResponse(datedResponse, originalFlightNumber)
            if (routes.isNotEmpty()) return routes
        } else {
            Log.w(TAG, "Dated API returned ${datedResponse.code()} for $ident on $date")
        }

        // Strategy 3: If dated query failed (e.g., 400 for future dates) and we
        // haven't tried undated yet, try it now as a last resort.
        if (!isRecentOrFuture) {
            val undatedRoutes = callApi(ident, start = null, end = null, originalFlightNumber)
            val matched = filterByDate(undatedRoutes, date)
            if (matched.isNotEmpty()) return matched
        }

        return emptyList()
    }

    /** Calls the API and maps the response to [FlightRoute]s. */
    private suspend fun callApi(
        ident: String,
        start: String?,
        end: String?,
        originalFlightNumber: String
    ): List<FlightRoute> {
        val response = api.getFlights(ident = ident, start = start, end = end)
        if (!response.isSuccessful) {
            Log.w(TAG, "API returned ${response.code()} for $ident (start=$start, end=$end)")
            return emptyList()
        }
        return mapResponse(response, originalFlightNumber)
    }

    /** Maps a successful API response to a list of [FlightRoute]s. */
    private fun mapResponse(
        response: retrofit2.Response<FlightAwareFlightsResponse>,
        originalFlightNumber: String
    ): List<FlightRoute> {
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
                    aircraftType = flight.aircraftType?.takeIf { it.isNotBlank() },
                    registration = flight.registration?.takeIf { it.isNotBlank() }
                )
            } ?: emptyList()
    }

    /**
     * Filters routes to those whose scheduled departure falls within ±1 day of [targetDate].
     * This handles timezone differences where a flight's UTC time falls on an adjacent day.
     */
    internal fun filterByDate(routes: List<FlightRoute>, targetDate: LocalDate): List<FlightRoute> {
        val minEpochDay = targetDate.minusDays(DATE_TOLERANCE_DAYS).toEpochDay()
        val maxEpochDay = targetDate.plusDays(DATE_TOLERANCE_DAYS).toEpochDay()

        return routes.filter { route ->
            val depMillis = route.departureScheduledUtc ?: return@filter false
            val depDate = java.time.Instant.ofEpochMilli(depMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            depDate.toEpochDay() in minEpochDay..maxEpochDay
        }
    }

    companion object {
        private const val TAG = "FlightRouteService"
        private const val RECENT_DAYS_THRESHOLD = 7L
        private const val DATE_TOLERANCE_DAYS = 1L

        fun parseIsoToUtc(iso: String?): Long? =
            iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
