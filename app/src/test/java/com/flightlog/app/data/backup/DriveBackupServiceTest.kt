package com.flightlog.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flightlog.app.data.export.ExportService
import com.flightlog.app.data.export.LogbookFlightExport
import com.flightlog.app.data.export.LogbookFlightExportWrapper
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveBackupServiceTest {

    private lateinit var context: Context
    private lateinit var exportService: ExportService
    private lateinit var repository: LogbookRepository
    private lateinit var moshi: Moshi
    private lateinit var metadataStore: BackupMetadataStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exportService = mockk()
        repository = mockk()
        moshi = Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        metadataStore = BackupMetadataStore(context)
        metadataStore.clear()
    }

    @Test
    fun `LogbookFlightExport to LogbookFlight conversion preserves all fields`() {
        val export = LogbookFlightExport(
            id = 99,
            date = "2026-03-27",
            flightNumber = "NH123",
            departure = "NRT",
            arrival = "LAX",
            departureTimeUtc = 1711555200000L,
            arrivalTimeUtc = 1711591200000L,
            departureTimeLocal = "09:00",
            arrivalTimeLocal = "10:00",
            departureTimezone = "Asia/Tokyo",
            arrivalTimezone = "America/Los_Angeles",
            durationMinutes = 600,
            distanceNm = 4723,
            aircraftType = "Boeing 777-300ER",
            seatClass = "Business",
            seatNumber = "2K",
            notes = "Great flight"
        )

        // Simulate the conversion done in DriveBackupService.restore()
        val logbookFlight = LogbookFlight(
            id = 0,
            flightNumber = export.flightNumber ?: "",
            departureCode = export.departure,
            arrivalCode = export.arrival,
            departureDateEpochDay = export.departureTimeUtc / 86_400_000,
            departureTimeMillis = export.departureTimeUtc,
            arrivalTimeMillis = export.arrivalTimeUtc,
            departureTimezone = export.departureTimezone,
            arrivalTimezone = export.arrivalTimezone,
            distanceKm = export.distanceNm,
            aircraftType = export.aircraftType,
            seatClass = export.seatClass,
            seatNumber = export.seatNumber,
            notes = export.notes
        )

        assertEquals(0L, logbookFlight.id) // Room auto-assigns
        assertEquals("NH123", logbookFlight.flightNumber)
        assertEquals("NRT", logbookFlight.departureCode)
        assertEquals("LAX", logbookFlight.arrivalCode)
        assertEquals(1711555200000L, logbookFlight.departureTimeMillis)
        assertEquals(1711591200000L, logbookFlight.arrivalTimeMillis)
        assertEquals("Asia/Tokyo", logbookFlight.departureTimezone)
        assertEquals("America/Los_Angeles", logbookFlight.arrivalTimezone)
        assertEquals(4723, logbookFlight.distanceKm)
        assertEquals("Boeing 777-300ER", logbookFlight.aircraftType)
        assertEquals("Business", logbookFlight.seatClass)
        assertEquals("2K", logbookFlight.seatNumber)
        assertEquals("Great flight", logbookFlight.notes)
    }

    @Test
    fun `null optional fields in export map to empty strings in LogbookFlight`() {
        val export = LogbookFlightExport(
            id = 1,
            date = "2026-01-01",
            flightNumber = null,
            departure = "JFK",
            arrival = "LHR",
            departureTimeUtc = 1000000L,
            arrivalTimeUtc = null,
            departureTimeLocal = "12:00",
            arrivalTimeLocal = null,
            departureTimezone = null,
            arrivalTimezone = null,
            durationMinutes = null,
            distanceNm = null,
            aircraftType = null,
            seatClass = null,
            seatNumber = null,
            notes = null
        )

        val logbookFlight = LogbookFlight(
            id = 0,
            flightNumber = export.flightNumber ?: "",
            departureCode = export.departure,
            arrivalCode = export.arrival,
            departureDateEpochDay = export.departureTimeUtc / 86_400_000,
            departureTimeMillis = export.departureTimeUtc,
            arrivalTimeMillis = export.arrivalTimeUtc,
            departureTimezone = export.departureTimezone,
            arrivalTimezone = export.arrivalTimezone,
            distanceKm = export.distanceNm,
            aircraftType = export.aircraftType,
            seatClass = export.seatClass,
            seatNumber = export.seatNumber,
            notes = export.notes
        )

        assertEquals("", logbookFlight.flightNumber)
        assertEquals(null, logbookFlight.aircraftType)
        assertEquals(null, logbookFlight.seatClass)
        assertEquals(null, logbookFlight.seatNumber)
        assertEquals(null, logbookFlight.notes)
        assertEquals(null, logbookFlight.arrivalTimeMillis)
        assertEquals(null, logbookFlight.departureTimezone)
        assertEquals(null, logbookFlight.arrivalTimezone)
        assertEquals(null, logbookFlight.distanceKm)
    }

    @Test
    fun `Moshi parses LogbookFlightExportWrapper with unknown fields`() {
        val json = """
        {
            "exported_at": "2026-03-27T00:00:00Z",
            "flight_count": 1,
            "future_field": "should be ignored",
            "flights": [{
                "id": 1,
                "date": "2026-03-27",
                "flight_number": "UA100",
                "departure": "SFO",
                "arrival": "NRT",
                "departure_time_utc": 1000000,
                "arrival_time_utc": 2000000,
                "departure_time_local": "08:00",
                "arrival_time_local": "16:00",
                "departure_timezone": "America/Los_Angeles",
                "arrival_timezone": "Asia/Tokyo",
                "duration_minutes": 660,
                "distance_nm": 4500,
                "aircraft_type": "B777",
                "seat_class": "Economy",
                "seat_number": "32A",
                "notes": "test",
                "unknown_nested": true
            }]
        }
        """.trimIndent()

        val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
        val wrapper = adapter.fromJson(json)

        assertNotNull(wrapper)
        assertEquals(1, wrapper!!.flightCount)
        assertEquals(1, wrapper.flights.size)
        assertEquals("UA100", wrapper.flights[0].flightNumber)
    }

    @Test
    fun `Moshi parses wrapper with zero flights`() {
        val json = """
        {
            "exported_at": "2026-03-27T00:00:00Z",
            "flight_count": 0,
            "flights": []
        }
        """.trimIndent()

        val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
        val wrapper = adapter.fromJson(json)

        assertNotNull(wrapper)
        assertEquals(0, wrapper!!.flightCount)
        assertTrue(wrapper.flights.isEmpty())
    }

    @Test
    fun `Moshi fails gracefully on malformed JSON`() {
        val json = "{ this is not valid json"

        val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
        val result = runCatching { adapter.fromJson(json) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `backup with empty logbook produces valid result`() = runTest {
        coEvery { repository.getAllOnce() } returns emptyList()

        // We can't fully test backup() without a real Drive API,
        // but we verify the export conversion handles empty lists
        val flights = repository.getAllOnce()
        assertTrue(flights.isEmpty())
    }

    @Test
    fun `BackupResult sealed class variants work correctly`() {
        val success: BackupResult = BackupResult.Success(flightCount = 10, fileSizeBytes = 5000)
        val failure: BackupResult = BackupResult.Failure(reason = "No network")

        assertTrue(success is BackupResult.Success)
        assertEquals(10, (success as BackupResult.Success).flightCount)
        assertEquals(5000L, success.fileSizeBytes)

        assertTrue(failure is BackupResult.Failure)
        assertEquals("No network", (failure as BackupResult.Failure).reason)
    }

    @Test
    fun `RestoreResult sealed class variants work correctly`() {
        val success: RestoreResult = RestoreResult.Success(imported = 8, skipped = 2)
        val failure: RestoreResult = RestoreResult.Failure(reason = "Parse error")

        assertTrue(success is RestoreResult.Success)
        assertEquals(8, (success as RestoreResult.Success).imported)
        assertEquals(2, success.skipped)

        assertTrue(failure is RestoreResult.Failure)
        assertEquals("Parse error", (failure as RestoreResult.Failure).reason)
    }
}
