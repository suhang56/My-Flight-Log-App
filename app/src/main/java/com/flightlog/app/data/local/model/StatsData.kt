package com.flightlog.app.data.local.model

import com.flightlog.app.data.local.entity.LogbookFlight

/** Aggregated statistics for the Statistics screen. */
data class StatsData(
    val flightCount: Int = 0,
    val totalDistanceNm: Int = 0,
    val totalDurationMinutes: Long = 0,
    val uniqueAirportCount: Int = 0,
    val monthlyFlightCounts: List<MonthlyCount> = emptyList(),
    val topAirports: List<AirportCount> = emptyList(),
    val topAirlines: List<AirlineCount> = emptyList(),
    val seatClassBreakdown: List<LabelCount> = emptyList(),
    val aircraftTypeDistribution: List<LabelCount> = emptyList(),
    val longestFlight: LogbookFlight? = null
) {
    /** Total hours of flight time (truncated). */
    val totalHours: Long get() = totalDurationMinutes / 60

    /** True when there are no flights to show stats for. */
    val isEmpty: Boolean get() = flightCount == 0
}
