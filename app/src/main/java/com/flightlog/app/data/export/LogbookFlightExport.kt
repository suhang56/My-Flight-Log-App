package com.flightlog.app.data.export

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogbookFlightExport(
    val id: Long,
    val date: String,
    @Json(name = "flight_number") val flightNumber: String?,
    val departure: String,
    val arrival: String,
    @Json(name = "departure_time_utc") val departureTimeUtc: Long,
    @Json(name = "arrival_time_utc") val arrivalTimeUtc: Long?,
    @Json(name = "departure_time_local") val departureTimeLocal: String,
    @Json(name = "arrival_time_local") val arrivalTimeLocal: String?,
    @Json(name = "departure_timezone") val departureTimezone: String?,
    @Json(name = "arrival_timezone") val arrivalTimezone: String?,
    @Json(name = "duration_minutes") val durationMinutes: Long?,
    @Json(name = "distance_nm") val distanceNm: Int?,
    @Json(name = "aircraft_type") val aircraftType: String?,
    @Json(name = "seat_class") val seatClass: String?,
    @Json(name = "seat_number") val seatNumber: String?,
    val notes: String?
)

@JsonClass(generateAdapter = true)
data class LogbookFlightExportWrapper(
    @Json(name = "exported_at") val exportedAt: String,
    @Json(name = "flight_count") val flightCount: Int,
    val flights: List<LogbookFlightExport>
)
