package com.flightlog.app.data.trips

import com.flightlog.app.data.local.entity.LogbookFlight

/**
 * An in-memory grouping of logbook flights that form a single trip.
 * Computed on-the-fly from [LogbookFlight] data — not persisted in Room.
 */
data class TripGroup(
    /** Stable key: first flight's id as a string. */
    val id: String,
    /** Chronological legs of the trip (at least 1 flight). */
    val legs: List<LogbookFlight>,
    /** Route label, e.g. "NRT -> ORD -> LAX". Unique airports in visit order. */
    val label: String,
    /** Human-readable date range, e.g. "Mar 20 - Mar 27, 2026". */
    val dateRange: String,
    /** Sum of non-null distanceKm across all legs. */
    val totalDistanceKm: Int,
    /** Sum of per-leg durations in minutes; null if any leg is missing arrivalTimeUtc. */
    val totalDurationMin: Long?,
    /** Whether the trip's legs are visible in the UI. */
    val isExpanded: Boolean = true
)
