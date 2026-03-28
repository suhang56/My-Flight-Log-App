package com.flightlog.app.data.achievements

import com.flightlog.app.data.local.entity.Achievement
import com.flightlog.app.data.local.entity.LogbookFlight
import java.time.Instant
import java.time.ZoneId

/**
 * Pure evaluation logic — no Room/Hilt/Android dependency.
 * Returns the set of achievement IDs that should be unlocked but are not yet.
 */
object AchievementEvaluator {

    fun evaluate(flights: List<LogbookFlight>, current: List<Achievement>): Set<String> {
        val alreadyUnlocked = current.filter { it.unlockedAt != null }.map { it.id }.toSet()

        // Short-circuit: if ALL achievements are already unlocked, nothing new to evaluate
        if (alreadyUnlocked.size == AchievementDefinitions.ALL.size) return emptySet()

        val newlyUnlocked = mutableSetOf<String>()

        for (def in AchievementDefinitions.ALL) {
            if (def.id in alreadyUnlocked) continue
            if (checkCondition(def.id, flights)) {
                newlyUnlocked.add(def.id)
            }
        }

        return newlyUnlocked
    }

    private fun checkCondition(id: String, flights: List<LogbookFlight>): Boolean {
        return when (id) {
            "first_flight" -> flights.isNotEmpty()

            "first_manual_add" -> flights.any { it.sourceCalendarEventId == null }

            "ten_flights" -> flights.size >= 10

            "five_airports" -> countUniqueAirports(flights) >= 5

            "five_airlines" -> countUniqueAirlines(flights) >= 5

            "fifty_flights" -> flights.size >= 50

            "twenty_airports" -> countUniqueAirports(flights) >= 20

            "three_seat_classes" -> {
                flights.map { it.seatClass }
                    .filter { it.isNotBlank() }
                    .toSet()
                    .size >= 3
            }

            "distance_10k" -> totalDistanceNm(flights) >= 10_000

            "short_hop" -> {
                flights.count { flight ->
                    val d = flight.distanceNm
                    d != null && d < 300
                } >= 5
            }

            "century_club" -> flights.size >= 100

            "fifty_airports" -> countUniqueAirports(flights) >= 50

            "long_hauler" -> flights.any { (it.distanceNm ?: 0) >= 5_000 }

            "distance_100k" -> totalDistanceNm(flights) >= 100_000

            "night_owl" -> countNightFlights(flights) >= 3

            "five_hundred_flights" -> flights.size >= 500

            "ultra_long_haul" -> flights.any { (it.distanceNm ?: 0) >= 8_000 }

            "distance_500k" -> totalDistanceNm(flights) >= 500_000

            else -> false
        }
    }

    private fun countUniqueAirports(flights: List<LogbookFlight>): Int {
        val codes = mutableSetOf<String>()
        for (f in flights) {
            if (f.departureCode.isNotBlank()) codes.add(f.departureCode.uppercase())
            if (f.arrivalCode.isNotBlank()) codes.add(f.arrivalCode.uppercase())
        }
        return codes.size
    }

    private fun countUniqueAirlines(flights: List<LogbookFlight>): Int {
        return flights.mapNotNull { extractAirlinePrefix(it.flightNumber) }
            .filter { it.isNotBlank() }
            .toSet()
            .size
    }

    /** Extracts the letter prefix from a flight number like "AA123" -> "AA". */
    private fun extractAirlinePrefix(flightNumber: String): String? {
        if (flightNumber.isBlank()) return null
        val prefix = flightNumber.takeWhile { it.isLetter() }.uppercase()
        return prefix.ifBlank { null }
    }

    private fun totalDistanceNm(flights: List<LogbookFlight>): Long {
        return flights.sumOf { (it.distanceNm ?: 0).toLong() }
    }

    /** Counts flights departing between 00:00 and 04:59 in local departure time. */
    private fun countNightFlights(flights: List<LogbookFlight>): Int {
        return flights.count { flight ->
            val hour = departureLocalHour(flight)
            hour in 0..4
        }
    }

    /** Returns the local hour (0-23) of the departure. Falls back to UTC if timezone is null/invalid. */
    internal fun departureLocalHour(flight: LogbookFlight): Int {
        val zoneId = try {
            flight.departureTimezone?.let { ZoneId.of(it) } ?: ZoneId.of("UTC")
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }
        return Instant.ofEpochMilli(flight.departureTimeUtc)
            .atZone(zoneId)
            .hour
    }
}
