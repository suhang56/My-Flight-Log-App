package com.flightlog.app.data.trips

import com.flightlog.app.data.local.entity.LogbookFlight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure grouping algorithm — no DI, no Android dependencies.
 * Groups a flat list of logbook flights into [TripGroup]s based on time proximity.
 */
object TripGrouper {

    /** Two flights more than 48 hours apart start a new trip. */
    private const val GAP_MS = 48L * 60 * 60 * 1000

    private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val DATE_YEAR_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    /**
     * Groups [flights] into trips. Always sorts by departureTimeUtc ascending internally
     * before grouping, regardless of the caller's display sort order.
     *
     * A new group starts when the gap between the current flight's departure and the
     * previous flight's end time (arrivalTimeUtc if available, else departureTimeUtc)
     * exceeds [GAP_MS] (strictly greater than, not >=).
     */
    fun group(flights: List<LogbookFlight>): List<TripGroup> {
        if (flights.isEmpty()) return emptyList()

        val sorted = flights.sortedBy { it.departureTimeUtc }
        val groups = mutableListOf<MutableList<LogbookFlight>>()
        var currentGroup = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val prevEndTime = prev.arrivalTimeUtc ?: prev.departureTimeUtc
            val gap = curr.departureTimeUtc - prevEndTime

            if (gap > GAP_MS) {
                groups.add(currentGroup)
                currentGroup = mutableListOf(curr)
            } else {
                currentGroup.add(curr)
            }
        }
        groups.add(currentGroup)

        return groups.map { buildTripGroup(it) }
    }

    private fun buildTripGroup(legs: List<LogbookFlight>): TripGroup {
        val label = buildRouteLabel(legs)
        val dateRange = buildDateRange(legs)
        val totalDistance = legs.sumOf { it.distanceNm ?: 0 }
        val totalDuration = computeTotalDuration(legs)

        return TripGroup(
            id = legs.first().id.toString(),
            legs = legs,
            label = label,
            dateRange = dateRange,
            totalDistanceNm = totalDistance,
            totalDurationMin = totalDuration
        )
    }

    /**
     * Builds a route label from unique airports in visit order.
     * Deduplicates consecutive repeats only (ORD->LAX, LAX->ORD = "ORD -> LAX -> ORD").
     */
    internal fun buildRouteLabel(legs: List<LogbookFlight>): String {
        if (legs.isEmpty()) return ""
        val airports = mutableListOf<String>()

        // Start with departure of first leg
        val firstDep = legs.first().departureCode
        if (firstDep.isNotBlank()) airports.add(firstDep)

        // Add arrival of each leg, deduplicating consecutive
        for (leg in legs) {
            val arr = leg.arrivalCode
            if (arr.isNotBlank() && arr != airports.lastOrNull()) {
                airports.add(arr)
            }
        }

        return airports.joinToString(" \u2192 ")
    }

    /**
     * Builds a human-readable date range string.
     * Single day: "Mar 20, 2026"
     * Same year: "Mar 20 - Mar 27, 2026"
     * Different years: "Dec 30, 2025 - Jan 2, 2026"
     */
    private fun zoneFor(iana: String?): ZoneId =
        iana?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    internal fun buildDateRange(legs: List<LogbookFlight>): String {
        if (legs.isEmpty()) return ""

        val firstFlight = legs.first()
        val firstDate = Instant.ofEpochMilli(firstFlight.departureTimeUtc)
            .atZone(zoneFor(firstFlight.departureTimezone)).toLocalDate()
        val lastFlight = legs.last()
        val lastTime = lastFlight.arrivalTimeUtc ?: lastFlight.departureTimeUtc
        val lastTz = if (lastFlight.arrivalTimeUtc != null) lastFlight.arrivalTimezone else lastFlight.departureTimezone
        val lastDate = Instant.ofEpochMilli(lastTime)
            .atZone(zoneFor(lastTz)).toLocalDate()

        return when {
            firstDate == lastDate -> firstDate.format(DATE_YEAR_FMT)
            firstDate.year == lastDate.year ->
                "${firstDate.format(DATE_FMT)} \u2013 ${lastDate.format(DATE_YEAR_FMT)}"
            else ->
                "${firstDate.format(DATE_YEAR_FMT)} \u2013 ${lastDate.format(DATE_YEAR_FMT)}"
        }
    }

    /**
     * Sums per-leg durations. Returns null if any leg is missing arrivalTimeUtc.
     */
    private fun computeTotalDuration(legs: List<LogbookFlight>): Long? {
        var total = 0L
        for (leg in legs) {
            val arr = leg.arrivalTimeUtc ?: return null
            val duration = arr - leg.departureTimeUtc
            if (duration > 0) total += duration
        }
        return total / 60_000 // convert millis to minutes
    }
}
