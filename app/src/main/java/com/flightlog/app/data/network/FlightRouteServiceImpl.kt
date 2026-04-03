package com.flightlog.app.data.network

import android.util.Log
import com.flightlog.app.BuildConfig
import com.flightlog.app.data.calendar.AirlineIcaoMap
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

class FlightRouteServiceImpl @Inject constructor(
    private val api: FlightAwareApi,
    private val aviationStackApi: AviationStackApi
) : FlightRouteService {

    // Client-side throttle: track last AviationStack call to enforce 60s minimum interval
    @Volatile
    private var lastAviationStackCallMillis: Long = 0L

    override suspend fun lookupRoute(
        flightNumber: String,
        date: LocalDate,
        departureAirport: String?
    ): FlightRoute? {
        return lookupAllRoutes(flightNumber, date, departureAirport).firstOrNull()
    }

    override suspend fun lookupAllRoutes(
        flightNumber: String,
        date: LocalDate,
        departureAirport: String?
    ): List<FlightRoute> {
        return try {
            val primary = selectApi(date)
            val results = when (primary) {
                ApiSource.FLIGHTAWARE -> fetchFlightAwareRoutes(flightNumber, date)
                ApiSource.AVIATION_STACK -> fetchAviationStackRoutes(flightNumber, date, departureAirport)
            }
            // Fallback to the other API if primary returned empty
            if (results.isNotEmpty()) return results

            val fallback = when (primary) {
                ApiSource.FLIGHTAWARE -> fetchAviationStackRoutes(flightNumber, date, departureAirport)
                ApiSource.AVIATION_STACK -> fetchFlightAwareRoutes(flightNumber, date)
            }
            fallback
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to look up route for $flightNumber", e)
            else Log.e(TAG, "Failed to look up route for $flightNumber")
            emptyList()
        }
    }

    internal fun selectApi(date: LocalDate): ApiSource {
        val today = LocalDate.now(ZoneOffset.UTC)
        val daysDiff = date.toEpochDay() - today.toEpochDay()
        return if (daysDiff <= FLIGHTAWARE_WINDOW_DAYS) ApiSource.FLIGHTAWARE else ApiSource.AVIATION_STACK
    }

    // -- FlightAware fetch (existing logic) --

    private suspend fun fetchFlightAwareRoutes(
        flightNumber: String,
        date: LocalDate
    ): List<FlightRoute> {
        return try {
            val routes = fetchAllFlightAwareRoutes(flightNumber, date)
            if (routes.isNotEmpty()) return routes

            // Retry with ICAO identifier
            val icaoIdent = AirlineIcaoMap.toIcaoFlightNumber(flightNumber)
            if (icaoIdent != null) {
                Log.d(TAG, "IATA lookup empty for $flightNumber, retrying as $icaoIdent")
                fetchAllFlightAwareRoutes(icaoIdent, date, originalFlightNumber = flightNumber)
            } else {
                emptyList()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "FlightAware lookup failed for $flightNumber", e)
            emptyList()
        }
    }

    private suspend fun fetchAllFlightAwareRoutes(
        ident: String,
        date: LocalDate,
        originalFlightNumber: String = ident
    ): List<FlightRoute> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val daysDiff = date.toEpochDay() - today.toEpochDay()
        val isRecentOrFuture = daysDiff >= -RECENT_DAYS_THRESHOLD

        if (isRecentOrFuture) {
            val undatedRoutes = callFlightAwareApi(ident, start = null, end = null, originalFlightNumber)
            val matched = filterByDate(undatedRoutes, date)
            if (matched.isNotEmpty()) return matched
        }

        val startDate = date.toString()
        val endDate = date.plusDays(1).toString()
        val datedResponse = api.getFlights(ident = ident, start = startDate, end = endDate)

        if (datedResponse.isSuccessful) {
            val routes = mapFlightAwareResponse(datedResponse, originalFlightNumber)
            if (routes.isNotEmpty()) return routes
        } else {
            Log.w(TAG, "Dated API returned ${datedResponse.code()} for $ident on $date")
        }

        if (!isRecentOrFuture) {
            val undatedRoutes = callFlightAwareApi(ident, start = null, end = null, originalFlightNumber)
            val matched = filterByDate(undatedRoutes, date)
            if (matched.isNotEmpty()) return matched
        }

        return emptyList()
    }

    private suspend fun callFlightAwareApi(
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
        return mapFlightAwareResponse(response, originalFlightNumber)
    }

    private fun mapFlightAwareResponse(
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

    // -- AviationStack fetch --

    private suspend fun fetchAviationStackRoutes(
        flightNumber: String,
        date: LocalDate,
        departureAirport: String?
    ): List<FlightRoute> {
        // AviationStack requires a departure airport IATA code
        if (departureAirport.isNullOrBlank()) {
            Log.d(TAG, "Skipping AviationStack: no departure airport provided")
            return emptyList()
        }

        val apiKey = BuildConfig.AVIATION_STACK_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "AviationStack API key not configured")
            return emptyList()
        }

        // Client-side 60s throttle
        val now = System.currentTimeMillis()
        if (now - lastAviationStackCallMillis < AVIATION_STACK_THROTTLE_MS) {
            Log.d(TAG, "AviationStack throttled (last call <60s ago)")
            return emptyList()
        }

        return try {
            lastAviationStackCallMillis = System.currentTimeMillis()
            val response = aviationStackApi.getScheduledFlights(
                accessKey = apiKey,
                iataCode = departureAirport.uppercase(),
                type = "departure",
                date = date.toString(),
                flightIata = flightNumber.uppercase()
            )

            if (!response.isSuccessful) {
                val code = response.code()
                when (code) {
                    429 -> Log.w(TAG, "AviationStack rate limited (429)")
                    in 400..499 -> Log.w(TAG, "AviationStack client error ($code)")
                    else -> Log.w(TAG, "AviationStack error ($code)")
                }
                return emptyList()
            }

            mapAviationStackResponse(response, flightNumber)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "AviationStack lookup failed for $flightNumber", e)
            emptyList()
        }
    }

    internal fun mapAviationStackResponse(
        response: retrofit2.Response<AviationStackResponse>,
        originalFlightNumber: String
    ): List<FlightRoute> {
        return response.body()?.data
            ?.filter { it.departure?.iata != null && it.arrival?.iata != null }
            ?.mapNotNull { flight ->
                FlightRoute(
                    flightNumber = originalFlightNumber,
                    departureIata = flight.departure?.iata ?: return@mapNotNull null,
                    arrivalIata = flight.arrival?.iata ?: return@mapNotNull null,
                    departureTimezone = flight.departure?.timezone,
                    arrivalTimezone = flight.arrival?.timezone,
                    departureScheduledUtc = parseIsoToUtc(flight.departure?.scheduled),
                    arrivalScheduledUtc = parseIsoToUtc(flight.arrival?.scheduled),
                    aircraftType = flight.aircraft?.modelCode?.takeIf { it.isNotBlank() },
                    registration = null // AviationStack does not provide registration
                )
            } ?: emptyList()
    }

    // -- Shared utilities --

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

    internal enum class ApiSource { FLIGHTAWARE, AVIATION_STACK }

    companion object {
        private const val TAG = "FlightRouteService"
        private const val RECENT_DAYS_THRESHOLD = 7L
        private const val DATE_TOLERANCE_DAYS = 1L
        internal const val FLIGHTAWARE_WINDOW_DAYS = 7L
        internal const val AVIATION_STACK_THROTTLE_MS = 60_000L

        fun parseIsoToUtc(iso: String?): Long? =
            iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
