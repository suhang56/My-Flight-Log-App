package com.flightlog.app.data.repository

import com.flightlog.app.data.airport.AirportCoordinatesMap
import com.flightlog.app.data.airport.AirportNameMap
import com.flightlog.app.data.airport.AirportTimezoneMap
import com.flightlog.app.data.local.dao.AirportDao
import com.flightlog.app.data.local.entity.Airport
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class AirportRepository @Inject constructor(
    private val airportDao: AirportDao
) {

    /**
     * Looks up an airport by IATA code. Normalizes to uppercase.
     * Falls back to static maps if not found in the database.
     */
    suspend fun getByIata(iata: String): Airport? {
        val normalized = iata.uppercase().trim()
        if (normalized.isEmpty()) return null
        return airportDao.getByIata(normalized) ?: staticFallback(normalized)
    }

    /**
     * Searches airports by name, city, or IATA prefix.
     * Returns empty list for queries shorter than 2 characters.
     */
    suspend fun search(query: String): List<Airport> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return airportDao.search(trimmed)
    }

    /**
     * Computes great-circle distance in nautical miles between two airports.
     * Returns null if either airport has unknown coordinates.
     */
    suspend fun distanceNm(departureIata: String, arrivalIata: String): Int? {
        val dep = getByIata(departureIata) ?: return null
        val arr = getByIata(arrivalIata) ?: return null
        // Guard against NaN coords from static fallback without coordinate data
        if (dep.lat.isNaN() || dep.lng.isNaN() || arr.lat.isNaN() || arr.lng.isNaN()) return null
        return haversineNm(dep.lat, dep.lng, arr.lat, arr.lng)
    }

    /**
     * Resolves a city name to an IATA code using the database.
     * Falls back to static AirportNameMap if not found.
     */
    suspend fun resolveCity(cityName: String): String? {
        val trimmed = cityName.trim()
        if (trimmed.isEmpty()) return null
        val result = airportDao.search(trimmed).firstOrNull()?.iata
        return result ?: AirportNameMap.resolve(trimmed)
    }

    /**
     * Checks if a 3-letter code is a known IATA airport code.
     */
    suspend fun isKnownAirport(code: String): Boolean {
        return getByIata(code) != null
    }

    /**
     * Constructs a synthetic Airport from the three existing static maps.
     * Ensures zero regression if a code is missing from the database.
     */
    private fun staticFallback(iata: String): Airport? {
        val coords = AirportCoordinatesMap.getCoords(iata)
        val tz = AirportTimezoneMap.getTimezone(iata)
        if (coords == null && tz == null) return null
        return Airport(
            iata = iata,
            icao = null,
            name = iata,
            city = iata,
            country = "",
            lat = coords?.first ?: Double.NaN,
            lng = coords?.second ?: Double.NaN,
            timezone = tz
        )
    }

    private fun haversineNm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val r = 3440.065 // Earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * asin(sqrt(a))
        return (r * c).roundToInt()
    }
}
