package com.flightlog.app.data.export

import android.content.Context
import com.flightlog.app.data.local.entity.LogbookFlight
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    suspend fun exportToCsv(flights: List<LogbookFlight>): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        // UTF-8 BOM for Windows Excel
        sb.append('\uFEFF')
        sb.appendLine("date,flight_number,departure,arrival,departure_time_local,arrival_time_local,duration_minutes,distance_km,aircraft_type,registration,seat_class,seat_number,notes,rating")

        for (flight in flights) {
            val date = formatLocalDate(flight.departureTimeMillis, flight.departureTimezone)
            val depTime = formatLocalTime(flight.departureTimeMillis, flight.departureTimezone)
            val arrTime = flight.arrivalTimeMillis?.let { formatLocalTime(it, flight.arrivalTimezone) } ?: ""
            val duration = computeDurationMinutes(flight)?.toString() ?: ""
            val distance = flight.distanceKm?.toString() ?: ""

            sb.append(date).append(',')
            sb.append(csvQuote(flight.flightNumber)).append(',')
            sb.append(flight.departureCode).append(',')
            sb.append(flight.arrivalCode).append(',')
            sb.append(depTime).append(',')
            sb.append(arrTime).append(',')
            sb.append(duration).append(',')
            sb.append(distance).append(',')
            sb.append(csvQuote(flight.aircraftType ?: "")).append(',')
            sb.append(csvQuote(flight.registration ?: "")).append(',')
            sb.append(csvQuote(flight.seatClass ?: "")).append(',')
            sb.append(csvQuote(flight.seatNumber ?: "")).append(',')
            sb.append(csvQuote(flight.notes ?: "")).append(',')
            sb.append(flight.rating?.toString() ?: "")
            sb.appendLine()
        }

        val file = File(cacheExportDir(), fileNameFor("csv"))
        file.writeText(sb.toString(), Charsets.UTF_8)
        file
    }

    suspend fun exportToJson(flights: List<LogbookFlight>): File = withContext(Dispatchers.IO) {
        val exportFlights = flights.map { toExport(it) }
        val wrapper = LogbookFlightExportWrapper(
            exportedAt = Instant.now().atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT),
            flightCount = exportFlights.size,
            flights = exportFlights
        )

        val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java).indent("  ")
        val json = adapter.toJson(wrapper)

        val file = File(cacheExportDir(), fileNameFor("json"))
        file.writeText(json, Charsets.UTF_8)
        file
    }

    internal fun toExport(flight: LogbookFlight): LogbookFlightExport {
        val duration = computeDurationMinutes(flight)
        return LogbookFlightExport(
            id = flight.id,
            date = formatLocalDate(flight.departureTimeMillis, flight.departureTimezone),
            flightNumber = flight.flightNumber.ifBlank { null },
            departure = flight.departureCode,
            arrival = flight.arrivalCode,
            departureTimeUtc = flight.departureTimeMillis,
            arrivalTimeUtc = flight.arrivalTimeMillis,
            departureTimeLocal = formatLocalTime(flight.departureTimeMillis, flight.departureTimezone),
            arrivalTimeLocal = flight.arrivalTimeMillis?.let { formatLocalTime(it, flight.arrivalTimezone) },
            departureTimezone = flight.departureTimezone,
            arrivalTimezone = flight.arrivalTimezone,
            durationMinutes = duration,
            distanceKm = flight.distanceKm,
            aircraftType = flight.aircraftType?.ifBlank { null },
            registration = flight.registration?.ifBlank { null },
            seatClass = flight.seatClass?.ifBlank { null },
            seatNumber = flight.seatNumber?.ifBlank { null },
            notes = flight.notes?.ifBlank { null },
            rating = flight.rating,
            createdAt = flight.createdAt,
            updatedAt = flight.updatedAt
        )
    }

    private fun computeDurationMinutes(flight: LogbookFlight): Long? {
        val arrival = flight.arrivalTimeMillis ?: return null
        val diff = arrival - flight.departureTimeMillis
        return if (diff > 0) diff / 60000 else null
    }

    internal fun formatLocalDate(utcMillis: Long, timezone: String?): String {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        return Instant.ofEpochMilli(utcMillis).atZone(zone)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    internal fun formatLocalTime(utcMillis: Long, timezone: String?): String {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        return Instant.ofEpochMilli(utcMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    internal fun csvQuote(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun fileNameFor(extension: String): String {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "flight-log-$date.$extension"
    }

    private fun cacheExportDir(): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
