package com.flightlog.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flightlog.app.data.local.entity.LogbookFlight
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExportServiceTest {

    private lateinit var service: ExportService
    private lateinit var moshi: Moshi

    // 2025-03-14 02:30:00 UTC = 1741919400000
    private val baseUtcMillis = 1741919400000L

    private val fullFlight = LogbookFlight(
        id = 1,
        flightNumber = "JL5",
        departureCode = "NRT",
        arrivalCode = "JFK",
        departureTimeUtc = baseUtcMillis,
        arrivalTimeUtc = baseUtcMillis + 780 * 60000L,
        departureTimezone = "Asia/Tokyo",
        arrivalTimezone = "America/New_York",
        distanceNm = 6732,
        aircraftType = "Boeing 777-300ER",
        seatClass = "Business",
        seatNumber = "4A",
        notes = "Great flight, Mt. Fuji visible"
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        service = ExportService(context, moshi)
    }

    // ── E2: Single flight CSV format ──────────────────────────────────────────

    @Test
    fun `E2 - single flight CSV has header plus one data row`() = runTest {
        val file = service.exportToCsv(listOf(fullFlight))
        val content = file.readText(Charsets.UTF_8)
        val lines = content.removePrefix("\uFEFF").trimEnd().lines()
        assertEquals(2, lines.size)
        assertEquals(12, lines[0].split(",").size)
    }

    @Test
    fun `E2 - single flight CSV columns are correct`() = runTest {
        val file = service.exportToCsv(listOf(fullFlight))
        val content = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val dataLine = content.trimEnd().lines()[1]
        // 2025-03-14 02:30 UTC = 2025-03-14 11:30 JST (Asia/Tokyo, UTC+9)
        assertTrue(dataLine.startsWith("2025-03-14,JL5,NRT,JFK,11:30,"))
        assertTrue(dataLine.contains(",6732,"))
        assertTrue(dataLine.contains("Boeing 777-300ER"))
        assertTrue(dataLine.contains("Business"))
        assertTrue(dataLine.contains("4A"))
    }

    // ── E3: Notes with comma — RFC 4180 quoting ──────────────────────────────

    @Test
    fun `E3 - CSV quotes notes containing comma`() {
        val quoted = service.csvQuote("Good flight, smooth landing")
        assertEquals("\"Good flight, smooth landing\"", quoted)
    }

    // ── E4: Notes with double-quote — RFC 4180 escaping ──────────────────────

    @Test
    fun `E4 - CSV escapes double quotes in notes`() {
        val quoted = service.csvQuote("Pilot said \"smooth air\"")
        assertEquals("\"Pilot said \"\"smooth air\"\"\"", quoted)
    }

    // ── E5: Notes with newline — RFC 4180 quoting ────────────────────────────

    @Test
    fun `E5 - CSV quotes notes containing newline`() {
        val quoted = service.csvQuote("First leg\nSecond leg")
        assertEquals("\"First leg\nSecond leg\"", quoted)
    }

    // ── E6: Empty notes ──────────────────────────────────────────────────────

    @Test
    fun `E6 - CSV does not quote empty notes`() {
        val quoted = service.csvQuote("")
        assertEquals("", quoted)
    }

    // ── E7: arrivalTimeUtc is null — CSV and JSON ────────────────────────────

    @Test
    fun `E7 - null arrival produces empty CSV columns`() = runTest {
        val flight = fullFlight.copy(arrivalTimeUtc = null)
        val file = service.exportToCsv(listOf(flight))
        val content = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val dataLine = content.trimEnd().lines()[1]
        val cols = splitCsvLine(dataLine)
        assertEquals("", cols[5])
        assertEquals("", cols[6])
    }

    @Test
    fun `E7 - null arrival omits fields in JSON DTO`() {
        val flight = fullFlight.copy(arrivalTimeUtc = null)
        val export = service.toExport(flight)
        assertNull(export.arrivalTimeUtc)
        assertNull(export.arrivalTimeLocal)
        assertNull(export.durationMinutes)
    }

    // ── E8: distanceNm is null ───────────────────────────────────────────────

    @Test
    fun `E8 - null distance produces empty CSV column`() = runTest {
        val flight = fullFlight.copy(distanceNm = null)
        val file = service.exportToCsv(listOf(flight))
        val content = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val dataLine = content.trimEnd().lines()[1]
        val cols = splitCsvLine(dataLine)
        assertEquals("", cols[7])
    }

    @Test
    fun `E8 - null distance in JSON DTO`() {
        val flight = fullFlight.copy(distanceNm = null)
        val export = service.toExport(flight)
        assertNull(export.distanceNm)
    }

    // ── E9: Null timezone falls back to UTC ──────────────────────────────────

    @Test
    fun `E9 - null timezone falls back to UTC`() {
        val date = service.formatLocalDate(baseUtcMillis, null)
        assertEquals("2025-03-14", date)
        val time = service.formatLocalTime(baseUtcMillis, null)
        assertEquals("02:30", time)
    }

    // ── E10: Timezone shifts date across midnight ────────────────────────────

    @Test
    fun `E10 - timezone offset shifts date across midnight`() {
        // 2025-01-15 05:00 UTC, America/Los_Angeles = PST (UTC-8) = Jan 14 21:00
        val janUtcMillis = 1736917200000L
        val date = service.formatLocalDate(janUtcMillis, "America/Los_Angeles")
        assertEquals("2025-01-14", date)
        val time = service.formatLocalTime(janUtcMillis, "America/Los_Angeles")
        assertEquals("21:00", time)
    }

    // ── E11: Overnight flight duration ───────────────────────────────────────

    @Test
    fun `E11 - overnight flight duration computed correctly`() {
        val depUtc = baseUtcMillis
        val arrUtc = depUtc + 480 * 60000L
        val flight = fullFlight.copy(departureTimeUtc = depUtc, arrivalTimeUtc = arrUtc)
        val export = service.toExport(flight)
        assertEquals(480L, export.durationMinutes)
    }

    // ── E12: Very long notes — no truncation ─────────────────────────────────

    @Test
    fun `E12 - very long notes not truncated in CSV`() = runTest {
        val longNotes = "a".repeat(10_000)
        val flight = fullFlight.copy(notes = longNotes)
        val file = service.exportToCsv(listOf(flight))
        val content = file.readText(Charsets.UTF_8)
        assertTrue(content.contains(longNotes))
    }

    // ── E13: 1000 flights export completes ───────────────────────────────────

    @Test
    fun `E13 - exporting 1000 flights to CSV completes`() = runTest {
        val flights = (1..1000).map { i ->
            LogbookFlight(
                id = i.toLong(),
                flightNumber = "FL$i",
                departureCode = "NRT",
                arrivalCode = "JFK",
                departureTimeUtc = baseUtcMillis + i * 86400000L,
                distanceNm = i * 10
            )
        }
        val file = service.exportToCsv(flights)
        val lines = file.readText(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().lines()
        assertEquals(1001, lines.size)
    }

    @Test
    fun `E13 - exporting 1000 flights to JSON completes`() = runTest {
        val flights = (1..1000).map { i ->
            LogbookFlight(
                id = i.toLong(),
                flightNumber = "FL$i",
                departureCode = "NRT",
                arrivalCode = "JFK",
                departureTimeUtc = baseUtcMillis + i * 86400000L,
                distanceNm = i * 10
            )
        }
        val file = service.exportToJson(flights)
        val json = file.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"flight_count\": 1000"))
    }

    // ── E14: Same-day re-export overwrites ───────────────────────────────────

    @Test
    fun `E14 - same-day re-export overwrites previous file`() = runTest {
        val file1 = service.exportToCsv(listOf(fullFlight))
        val path1 = file1.absolutePath
        val file2 = service.exportToCsv(listOf(fullFlight, fullFlight.copy(id = 2)))
        assertEquals(path1, file2.absolutePath)
        val lines = file2.readText(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().lines()
        assertEquals(3, lines.size)
    }

    // ── E17: Export uses all flights passed ───────────────────────────────────

    @Test
    fun `E17 - export includes all flights passed in`() = runTest {
        val flights = (1..50).map { i ->
            LogbookFlight(
                id = i.toLong(),
                flightNumber = "FL$i",
                departureCode = if (i % 2 == 0) "NRT" else "ORD",
                arrivalCode = "JFK",
                departureTimeUtc = baseUtcMillis + i * 86400000L
            )
        }
        val file = service.exportToCsv(flights)
        val lines = file.readText(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().lines()
        assertEquals(51, lines.size)
    }

    // ── E18: Empty string to null mapping in JSON ────────────────────────────

    @Test
    fun `E18 - empty strings become null in JSON DTO`() {
        val flight = LogbookFlight(
            id = 1,
            flightNumber = "",
            departureCode = "NRT",
            arrivalCode = "HND",
            departureTimeUtc = baseUtcMillis,
            distanceNm = null,
            aircraftType = "",
            seatClass = "",
            seatNumber = "",
            notes = ""
        )
        val export = service.toExport(flight)
        assertNull(export.flightNumber)
        assertNull(export.aircraftType)
        assertNull(export.seatClass)
        assertNull(export.seatNumber)
        assertNull(export.notes)
        assertNull(export.distanceNm)
    }

    @Test
    fun `E18 - null fields serialize as null in JSON`() = runTest {
        val flight = LogbookFlight(
            id = 1,
            flightNumber = "",
            departureCode = "NRT",
            arrivalCode = "HND",
            departureTimeUtc = baseUtcMillis,
            distanceNm = null,
            aircraftType = "",
            seatClass = "",
            seatNumber = "",
            notes = ""
        )
        val file = service.exportToJson(listOf(flight))
        val json = file.readText(Charsets.UTF_8)
        // KotlinJsonAdapterFactory may serialize null as "key": null or omit it — both acceptable
        // Verify the key is either absent or null-valued
        val hasNullValue = json.contains("\"flight_number\": null")
        val hasNoKey = !json.contains("\"flight_number\"")
        assertTrue(hasNullValue || hasNoKey)
    }

    // ── E20: UTF-8 BOM in CSV ────────────────────────────────────────────────

    @Test
    fun `E20 - CSV starts with UTF-8 BOM`() = runTest {
        val file = service.exportToCsv(listOf(fullFlight))
        val bytes = file.readBytes()
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }

    @Test
    fun `E20 - CSV correctly encodes Japanese characters`() = runTest {
        val flight = fullFlight.copy(notes = "富士山が見えた")
        val file = service.exportToCsv(listOf(flight))
        val content = file.readText(Charsets.UTF_8)
        assertTrue(content.contains("富士山が見えた"))
    }

    // ── E23: Cache directory creation ─────────────────────────────────────────

    @Test
    fun `E23 - export creates cache directory if needed`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val exportsDir = File(context.cacheDir, "exports")
        if (exportsDir.exists()) exportsDir.deleteRecursively()

        val file = service.exportToCsv(listOf(fullFlight))
        assertTrue(file.exists())
        assertTrue(file.parentFile!!.exists())
    }

    // ── E24: JSON exported_at format ─────────────────────────────────────────

    @Test
    fun `E24 - JSON exported_at is ISO 8601 UTC format`() = runTest {
        val file = service.exportToJson(listOf(fullFlight))
        val json = file.readText(Charsets.UTF_8)
        val regex = Regex("\"exported_at\":\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
        assertTrue(regex.containsMatchIn(json))
        assertTrue(json.contains("Z"))
    }

    @Test
    fun `E24 - JSON flight_count matches flights size`() = runTest {
        val flights = listOf(fullFlight, fullFlight.copy(id = 2))
        val file = service.exportToJson(flights)
        val json = file.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"flight_count\": 2"))
    }

    // ── Additional: Empty list export ────────────────────────────────────────

    @Test
    fun `empty list produces CSV with only header`() = runTest {
        val file = service.exportToCsv(emptyList())
        val content = file.readText(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd()
        val lines = content.lines()
        assertEquals(1, lines.size)
    }

    @Test
    fun `empty list produces JSON with zero flight_count`() = runTest {
        val file = service.exportToJson(emptyList())
        val json = file.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"flight_count\": 0"))
    }

    // ── Additional: Invalid timezone handled gracefully ──────────────────────

    @Test
    fun `invalid timezone falls back to UTC`() {
        val date = service.formatLocalDate(baseUtcMillis, "Not/A/Timezone")
        assertEquals("2025-03-14", date)
    }

    // ── Additional: Arrival before departure produces null duration ───────────

    @Test
    fun `arrival before departure produces null duration`() {
        val flight = fullFlight.copy(
            arrivalTimeUtc = fullFlight.departureTimeUtc - 60000
        )
        val export = service.toExport(flight)
        assertNull(export.durationMinutes)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> {
                    if (inQuotes && sb.isNotEmpty() && sb.last() == '"') {
                        // escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                    sb.append(ch)
                }
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim('"'))
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim('"'))
        return result
    }
}
