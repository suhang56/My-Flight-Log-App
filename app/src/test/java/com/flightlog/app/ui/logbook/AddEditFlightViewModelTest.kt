package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.RouteCount
import com.flightlog.app.data.network.FlightRoute
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.repository.LogbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditFlightViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeFlightRouteService: FakeFlightRouteService
    private lateinit var fakeDao: FakeLogbookFlightDao
    private lateinit var repository: LogbookRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeFlightRouteService = FakeFlightRouteService()
        fakeDao = FakeLogbookFlightDao()
        repository = LogbookRepository(fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddEditLogbookFlightViewModel {
        return AddEditLogbookFlightViewModel(
            repository = repository,
            flightRouteService = fakeFlightRouteService,
            savedStateHandle = SavedStateHandle()
        )
    }

    // ── E1: Empty or blank flight number search ──────────────────────────────

    @Test
    fun `E1 - searchFlight with blank query does not call API`() = runTest {
        val vm = createViewModel()
        vm.updateFlightSearchQuery("   ")
        vm.searchFlight()
        advanceUntilIdle()

        assertFalse(vm.form.value.isSearching)
        assertNull(vm.form.value.searchError)
        assertEquals(0, fakeFlightRouteService.callCount)
    }

    @Test
    fun `E1 - searchFlight with empty query does not call API`() = runTest {
        val vm = createViewModel()
        vm.searchFlight()
        advanceUntilIdle()

        assertFalse(vm.form.value.isSearching)
        assertNull(vm.form.value.searchError)
        assertEquals(0, fakeFlightRouteService.callCount)
    }

    // ── E2: Flight number with lowercase input ──────────────────────────────

    @Test
    fun `E2 - updateFlightSearchQuery uppercases input`() = runTest {
        val vm = createViewModel()
        vm.updateFlightSearchQuery("aa11")
        assertEquals("AA11", vm.form.value.flightSearchQuery)
    }

    // ── E3: API returns null (flight not found) ─────────────────────────────

    @Test
    fun `E3 - searchFlight with null API result shows error`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("ZZ999")
        vm.searchFlight()
        advanceUntilIdle()

        assertFalse(vm.form.value.isSearching)
        assertNotNull(vm.form.value.searchError)
        assertTrue(vm.form.value.searchError!!.contains("Flight not found"))
        assertFalse(vm.form.value.autoFillApplied)
        assertEquals("", vm.form.value.departureCode)
        assertEquals("", vm.form.value.arrivalCode)
    }

    // ── E4: API returns null and user proceeds manually ─────────────────────

    @Test
    fun `E4 - after search failure user can fill form manually`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("ZZ999")
        vm.searchFlight()
        advanceUntilIdle()

        vm.updateDepartureCode("NRT")
        vm.updateArrivalCode("HND")
        vm.updateFlightNumber("XX123")

        assertEquals("NRT", vm.form.value.departureCode)
        assertEquals("HND", vm.form.value.arrivalCode)
        assertEquals("XX123", vm.form.value.flightNumber)
    }

    // ── E5: Auto-fill applied, then user edits departure code ───────────────

    @Test
    fun `E5 - editing departure code clears autoFillApplied`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        assertTrue(vm.form.value.autoFillApplied)
        assertEquals("NRT", vm.form.value.departureCode)

        vm.updateDepartureCode("HND")

        assertFalse(vm.form.value.autoFillApplied)
        assertFalse(vm.form.value.duplicateCheckPassed)
    }

    @Test
    fun `E5 - editing arrival code clears autoFillApplied`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        assertTrue(vm.form.value.autoFillApplied)

        vm.updateArrivalCode("KIX")

        assertFalse(vm.form.value.autoFillApplied)
    }

    // ── E6: Duplicate flight detection after auto-fill ──────────────────────

    @Test
    fun `E6 - duplicate warning shown after autofill and save`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        fakeDao.duplicateExists = true

        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.form.value.duplicateWarning)
        assertTrue(vm.form.value.duplicateWarning!!.contains("NRT"))
        assertTrue(vm.form.value.duplicateWarning!!.contains("HND"))
    }

    // ── E7: Duplicate check UTC day boundary ────────────────────────────────

    @Test
    fun `E7 - duplicate check passes correct codes to repository`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        fakeDao.duplicateExists = false

        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertTrue(fakeDao.existsByRouteAndDateCalled)
        assertEquals("NRT", fakeDao.lastDepCode)
        assertEquals("HND", fakeDao.lastArrCode)
    }

    // ── E8: Search triggered while already searching (double-tap) ───────────

    @Test
    fun `E8 - double tap cancels first search and starts second`() = runTest {
        fakeFlightRouteService.delayMs = 500
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")

        // First search starts
        vm.searchFlight()
        // Second search cancels the first and starts anew
        vm.searchFlight()
        advanceUntilIdle()

        // The second search should complete successfully
        assertFalse(vm.form.value.isSearching)
        assertEquals("NRT", vm.form.value.departureCode)
        assertEquals("HND", vm.form.value.arrivalCode)
        assertTrue(vm.form.value.autoFillApplied)
        // At least one call completed (first may have been cancelled before reaching the service)
        assertTrue(fakeFlightRouteService.callCount >= 1)
    }

    // ── E9: ViewModel state resets after process death ──────────────────────

    @Test
    fun `E9 - isSearching defaults to false on new ViewModel`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.form.value.isSearching)
        assertNull(vm.form.value.searchError)
        assertFalse(vm.form.value.autoFillApplied)
    }

    // ── E10: Flight number exactly 2 characters ─────────────────────────────

    @Test
    fun `E10 - two character flight number is sent to API`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("AA")
        vm.searchFlight()
        advanceUntilIdle()

        assertEquals(1, fakeFlightRouteService.callCount)
        assertEquals("AA", fakeFlightRouteService.lastQuery)
        assertNotNull(vm.form.value.searchError)
    }

    // ── E11: Flight number very long ────────────────────────────────────────

    @Test
    fun `E11 - very long flight number does not crash`() = runTest {
        fakeFlightRouteService.result = null
        val longQuery = "A".repeat(20)
        val vm = createViewModel()
        vm.updateFlightSearchQuery(longQuery)
        vm.searchFlight()
        advanceUntilIdle()

        assertEquals(1, fakeFlightRouteService.callCount)
        assertNotNull(vm.form.value.searchError)
    }

    // ── E12: API returns flight with null IATA codes ────────────────────────

    @Test
    fun `E12 - null IATA from API results in search error`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("XX123")
        vm.searchFlight()
        advanceUntilIdle()

        assertNotNull(vm.form.value.searchError)
        assertFalse(vm.form.value.autoFillApplied)
    }

    // ── E13: Manual flight insert has null source fields ────────────────────

    @Test
    fun `E13 - manual flight insert has null sourceCalendarEventId`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        fakeDao.duplicateExists = false
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertTrue(fakeDao.insertCalled)
        val inserted = fakeDao.lastInserted!!
        assertNull(inserted.sourceCalendarEventId)
        assertNull(inserted.sourceLegIndex)
    }

    // ── E14: Save with empty arrival time ───────────────────────────────────

    @Test
    fun `E14 - save with null arrival time succeeds`() = runTest {
        fakeDao.duplicateExists = false
        val vm = createViewModel()
        vm.updateDepartureCode("NRT")
        vm.updateArrivalCode("HND")
        vm.updateArrivalTime(null)

        vm.save()
        advanceUntilIdle()

        assertTrue(fakeDao.insertCalled)
        assertNull(fakeDao.lastInserted!!.arrivalTimeUtc)
    }

    // ── E15: Same departure and arrival airport ─────────────────────────────

    @Test
    fun `E15 - same departure and arrival airport saves without error`() = runTest {
        fakeDao.duplicateExists = false
        val vm = createViewModel()
        vm.updateDepartureCode("NRT")
        vm.updateArrivalCode("NRT")

        vm.save()
        advanceUntilIdle()

        assertTrue(fakeDao.insertCalled)
        assertEquals("NRT", fakeDao.lastInserted!!.departureCode)
        assertEquals("NRT", fakeDao.lastInserted!!.arrivalCode)
    }

    // ── E16: Date picker shows future dates ─────────────────────────────────

    @Test
    fun `E16 - future date search calls API without error`() = runTest {
        val futureDate = LocalDate.now().plusYears(1)
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.updateFlightSearchDate(futureDate)
        vm.searchFlight()
        advanceUntilIdle()

        assertEquals(1, fakeFlightRouteService.callCount)
        assertEquals(futureDate, fakeFlightRouteService.lastDate)
        assertNotNull(vm.form.value.searchError)
    }

    // ── E17: Network unavailable during search ──────────────────────────────

    @Test
    fun `E17 - service exception results in search error`() = runTest {
        fakeFlightRouteService.shouldThrow = true
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        assertFalse(vm.form.value.isSearching)
        assertNotNull(vm.form.value.searchError)
    }

    // ── E18: First manual flight ────────────────────────────────────────────

    @Test
    fun `E18 - first manual flight adds successfully`() = runTest {
        fakeDao.duplicateExists = false
        val vm = createViewModel()
        vm.updateDepartureCode("NRT")
        vm.updateArrivalCode("HND")
        vm.updateFlightNumber("JL5")

        vm.save()
        advanceUntilIdle()

        assertTrue(fakeDao.insertCalled)
        assertTrue(vm.form.value.savedSuccessfully)
    }

    // ── Additional edge tests ───────────────────────────────────────────────

    @Test
    fun `searchFlight sets form date to search date on success`() = runTest {
        val searchDate = LocalDate.of(2026, 3, 15)
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.updateFlightSearchDate(searchDate)
        vm.searchFlight()
        advanceUntilIdle()

        assertEquals(searchDate, vm.form.value.date)
    }

    @Test
    fun `dismissAutoFillBanner clears autoFillApplied`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        assertTrue(vm.form.value.autoFillApplied)
        vm.dismissAutoFillBanner()
        assertFalse(vm.form.value.autoFillApplied)
    }

    @Test
    fun `updateFlightSearchQuery clears searchError`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("ZZ999")
        vm.searchFlight()
        advanceUntilIdle()

        assertNotNull(vm.form.value.searchError)

        vm.updateFlightSearchQuery("JL5")
        assertNull(vm.form.value.searchError)
    }

    @Test
    fun `updateFlightSearchDate clears searchError`() = runTest {
        fakeFlightRouteService.result = null
        val vm = createViewModel()
        vm.updateFlightSearchQuery("ZZ999")
        vm.searchFlight()
        advanceUntilIdle()

        assertNotNull(vm.form.value.searchError)

        vm.updateFlightSearchDate(LocalDate.of(2026, 4, 1))
        assertNull(vm.form.value.searchError)
    }

    @Test
    fun `successful search fills flightNumber departureCode arrivalCode`() = runTest {
        fakeFlightRouteService.result = FlightRoute(
            flightNumber = "JL5",
            departureIata = "NRT",
            arrivalIata = "HND"
        )
        val vm = createViewModel()
        vm.updateFlightSearchQuery("JL5")
        vm.searchFlight()
        advanceUntilIdle()

        assertEquals("JL5", vm.form.value.flightNumber)
        assertEquals("NRT", vm.form.value.departureCode)
        assertEquals("HND", vm.form.value.arrivalCode)
        assertTrue(vm.form.value.autoFillApplied)
        assertFalse(vm.form.value.isSearching)
        assertFalse(vm.form.value.duplicateCheckPassed)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

private class FakeFlightRouteService : FlightRouteService {
    var result: FlightRoute? = null
    var shouldThrow: Boolean = false
    var delayMs: Long = 0
    var callCount: Int = 0
    var lastQuery: String? = null
    var lastDate: LocalDate? = null

    override suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute? {
        callCount++
        lastQuery = flightNumber
        lastDate = date
        if (delayMs > 0) delay(delayMs)
        if (shouldThrow) throw RuntimeException("Network error")
        return result
    }
}

private class FakeLogbookFlightDao : LogbookFlightDao {
    var duplicateExists: Boolean = false
    var insertCalled: Boolean = false
    var lastInserted: LogbookFlight? = null
    var existsByRouteAndDateCalled: Boolean = false
    var lastDepCode: String? = null
    var lastArrCode: String? = null
    var lastUtcDay: Long? = null

    override fun getAll(): Flow<List<LogbookFlight>> = flowOf(emptyList())
    override suspend fun getById(id: Long): LogbookFlight? = null
    override suspend fun existsBySource(calendarEventId: Long, legIndex: Int): Boolean = false
    override suspend fun existsByRouteAndDate(depCode: String, arrCode: String, utcDay: Long, excludeId: Long?): Boolean {
        existsByRouteAndDateCalled = true
        lastDepCode = depCode
        lastArrCode = arrCode
        lastUtcDay = utcDay
        return duplicateExists
    }
    override suspend fun insert(flight: LogbookFlight): Long {
        insertCalled = true
        lastInserted = flight
        return 1L
    }
    override suspend fun upsert(flight: LogbookFlight): Long = 1L
    override suspend fun update(flight: LogbookFlight) {}
    override suspend fun deleteById(id: Long) {}
    override fun getCount(): Flow<Int> = flowOf(0)
    override fun getTotalDistanceNm(): Flow<Int> = flowOf(0)
    override fun getTotalDurationMinutes(): Flow<Long?> = flowOf(null)
    override fun getDistinctAirportCodes(): Flow<List<String>> = flowOf(emptyList())
    override fun getDistinctAirlinePrefixes(): Flow<List<AirlineCount>> = flowOf(emptyList())
    override fun getFlightsPerMonth(): Flow<List<MonthlyCount>> = flowOf(emptyList())
    override fun getTopDepartureAirports(limit: Int): Flow<List<AirportCount>> = flowOf(emptyList())
    override fun getTopArrivalAirports(limit: Int): Flow<List<AirportCount>> = flowOf(emptyList())
    override fun getSeatClassBreakdown(): Flow<List<LabelCount>> = flowOf(emptyList())
    override fun getAircraftTypeDistribution(): Flow<List<LabelCount>> = flowOf(emptyList())
    override fun getLongestFlightByDistance(): Flow<LogbookFlight?> = flowOf(null)
    override fun getLongestFlightByDuration(): Flow<LogbookFlight?> = flowOf(null)
    override fun getTopRoutes(limit: Int): Flow<List<RouteCount>> = flowOf(emptyList())
    override fun getFirstFlight(): Flow<LogbookFlight?> = flowOf(null)
}
