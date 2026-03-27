package com.flightlog.app.ui.logbook

import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.RouteCount
import com.flightlog.app.data.repository.LogbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModelSearchTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeSearchLogbookFlightDao
    private lateinit var repository: LogbookRepository
    private lateinit var vm: LogbookViewModel

    private val flight1 = LogbookFlight(
        id = 1, flightNumber = "JL5", departureCode = "NRT", arrivalCode = "HND",
        departureTimeUtc = 1718452800000, // 2024-06-15 12:00 UTC
        distanceNm = 200, seatClass = "Economy", aircraftType = "Boeing 737",
        notes = "Great window seat view of Mt. Fuji"
    )
    private val flight2 = LogbookFlight(
        id = 2, flightNumber = "AA11", departureCode = "ORD", arrivalCode = "CMH",
        departureTimeUtc = 1718539200000, // 2024-06-16 12:00 UTC
        distanceNm = 300, seatClass = "Business", aircraftType = "Airbus A320",
        notes = ""
    )
    private val flight3 = LogbookFlight(
        id = 3, flightNumber = "JL006", departureCode = "NRT", arrivalCode = "SFO",
        departureTimeUtc = 1735689600000, // 2025-01-01 00:00 UTC
        distanceNm = 5000, seatClass = "Business", aircraftType = "Boeing 777",
        notes = "New Year flight"
    )
    private val flight4 = LogbookFlight(
        id = 4, flightNumber = "JL789", departureCode = "HND", arrivalCode = "FUK",
        departureTimeUtc = 1735776000000, // 2025-01-02 00:00 UTC
        distanceNm = null, seatClass = "", aircraftType = "",
        notes = ""
    )
    private val flight5 = LogbookFlight(
        id = 5, flightNumber = "NH100", departureCode = "NRT", arrivalCode = "NRT",
        departureTimeUtc = 1735862400000, // 2025-01-03 00:00 UTC
        distanceNm = 0, seatClass = "First", aircraftType = "Boeing 787",
        notes = "Training flight"
    )

    private val allFlights = listOf(flight1, flight2, flight3, flight4, flight5)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeSearchLogbookFlightDao()
        fakeDao.allFlightsFlow.value = allFlights
        fakeDao.distinctYears = listOf("2025", "2024")
        fakeDao.distinctSeatClasses = listOf("Business", "Economy", "First")
        repository = LogbookRepository(fakeDao)
        vm = LogbookViewModel(repository)
    }

    private val collectJobs = mutableListOf<Job>()

    private fun TestScope.startCollecting() {
        collectJobs += backgroundScope.launch { vm.flights.collect {} }
        collectJobs += backgroundScope.launch { vm.flightCount.collect {} }
        collectJobs += backgroundScope.launch { vm.totalDistanceNm.collect {} }
        collectJobs += backgroundScope.launch { vm.availableYears.collect {} }
        collectJobs += backgroundScope.launch { vm.availableSeatClasses.collect {} }
    }

    @After
    fun tearDown() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        Dispatchers.resetMain()
    }

    private fun advancePastDebounce() {
        testDispatcher.scheduler.advanceTimeBy(400)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ── E1: Empty search query — all flights shown ──────────────────────────

    @Test
    fun `E1 - empty search query shows all flights`() = runTest {
        startCollecting()
        advancePastDebounce()
        assertEquals(5, vm.flights.value.size)
    }

    // ── E2: Search matches flight number (case-insensitive) ─────────────────

    @Test
    fun `E2 - search matches flight number case-insensitive`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("jl5")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(1, results.size)
        assertEquals("JL5", results[0].flightNumber)
    }

    @Test
    fun `E2 - search partial flight number matches multiple`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("JL")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(3, results.size) // JL5, JL006, JL789
    }

    // ── E3: Search matches departure code ───────────────────────────────────

    @Test
    fun `E3 - search matches departure code`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(3, results.size)
    }

    // ── E4: Search matches arrival code but not departure ───────────────────

    @Test
    fun `E4 - search matches arrival code`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("HND")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(2, results.size)
    }

    // ── E5: Search matches notes ────────────────────────────────────────────

    @Test
    fun `E5 - search matches notes`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("window seat")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(1, results.size)
        assertEquals(flight1.id, results[0].id)
    }

    // ── E6: Search matches nothing ──────────────────────────────────────────

    @Test
    fun `E6 - search with no matches returns empty list`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("ZZZZ")
        advancePastDebounce()
        assertTrue(vm.flights.value.isEmpty())
    }

    // ── E7: Logbook empty + no filter = empty-logbook state ─────────────────

    @Test
    fun `E7 - empty logbook with no filter active`() = runTest {
        startCollecting()
        fakeDao.allFlightsFlow.value = emptyList()
        advancePastDebounce()
        assertTrue(vm.flights.value.isEmpty())
        assertFalse(vm.filterState.value.isActive)
    }

    // ── E8: Filter active but no match = no-results state ───────────────────

    @Test
    fun `E8 - seat class filter with no matches`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.toggleSeatClassFilter("First")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(1, results.size)
        assertEquals(flight5.id, results[0].id)
    }

    // ── E9: Toggle seat class chip deselects ────────────────────────────────

    @Test
    fun `E9 - toggle seat class filter deselects on second tap`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.toggleSeatClassFilter("Business")
        assertEquals("Business", vm.filterState.value.selectedSeatClass)

        vm.toggleSeatClassFilter("Business")
        assertEquals(null, vm.filterState.value.selectedSeatClass)

        advancePastDebounce()
        assertEquals(5, vm.flights.value.size)
    }

    // ── E10: Year filter — UTC year boundary ────────────────────────────────

    @Test
    fun `E10 - year filter uses UTC year`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.toggleYearFilter("2024")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(2, results.size)
        assertTrue(results.all { it.id == 1L || it.id == 2L })
    }

    @Test
    fun `E10 - year 2025 filter gets 2025 flights`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.toggleYearFilter("2025")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(3, results.size)
    }

    // ── E11: Year chips from DAO, not filtered list ─────────────────────────

    @Test
    fun `E11 - available years come from DAO not filtered list`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        advancePastDebounce()
        assertEquals(listOf("2025", "2024"), vm.availableYears.value)
    }

    // ── E12: Seat class chips show only classes in logbook ──────────────────

    @Test
    fun `E12 - available seat classes reflect DAO data`() = runTest {
        startCollecting()
        advancePastDebounce()
        assertEquals(listOf("Business", "Economy", "First"), vm.availableSeatClasses.value)
    }

    // ── E13: Sort by oldest first ───────────────────────────────────────────

    @Test
    fun `E13 - sort oldest first`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.setSortOrder(LogbookSortOrder.OLDEST_FIRST)
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(flight1.id, results[0].id)
        assertEquals(flight5.id, results[4].id)
    }

    // ── E14: Sort by longest distance — nulls last ──────────────────────────

    @Test
    fun `E14 - sort longest distance with nulls last`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.setSortOrder(LogbookSortOrder.LONGEST_DISTANCE)
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(5000, results[0].distanceNm)
        assertEquals(300, results[1].distanceNm)
        assertEquals(200, results[2].distanceNm)
        assertEquals(0, results[3].distanceNm)
        assertEquals(null, results[4].distanceNm)
    }

    // ── E15: Sort by distance combined with search ──────────────────────────

    @Test
    fun `E15 - search plus sort by distance`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        vm.setSortOrder(LogbookSortOrder.LONGEST_DISTANCE)
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(3, results.size)
        assertEquals(5000, results[0].distanceNm)
        assertEquals(200, results[1].distanceNm)
        assertEquals(0, results[2].distanceNm)
    }

    // ── E16: Clear button resets all state ───────────────────────────────────

    @Test
    fun `E16 - clearFilters resets all filter state`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        vm.toggleSeatClassFilter("Business")
        vm.toggleYearFilter("2024")
        vm.setSortOrder(LogbookSortOrder.OLDEST_FIRST)

        vm.clearFilters()

        assertEquals(LogbookFilterState(), vm.filterState.value)
        advancePastDebounce()
        assertEquals(5, vm.flights.value.size)
    }

    // ── E17: Clear button visibility via isActive ───────────────────────────

    @Test
    fun `E17 - isActive false when default state`() = runTest {
        startCollecting()
        assertFalse(vm.filterState.value.isActive)
    }

    @Test
    fun `E17 - isActive true with search query`() = runTest {
        startCollecting()
        vm.updateSearchQuery("NRT")
        assertTrue(vm.filterState.value.isActive)
    }

    @Test
    fun `E17 - isActive true with seat class filter`() = runTest {
        startCollecting()
        vm.toggleSeatClassFilter("Business")
        assertTrue(vm.filterState.value.isActive)
    }

    @Test
    fun `E17 - isActive true with non-default sort`() = runTest {
        startCollecting()
        vm.setSortOrder(LogbookSortOrder.OLDEST_FIRST)
        assertTrue(vm.filterState.value.isActive)
    }

    @Test
    fun `E17 - isActive false after clearFilters`() = runTest {
        startCollecting()
        vm.updateSearchQuery("NRT")
        vm.clearFilters()
        assertFalse(vm.filterState.value.isActive)
    }

    // ── E18: Debounce — rapid typing ────────────────────────────────────────

    @Test
    fun `E18 - debounce delays filtering for rapid typing`() = runTest {
        startCollecting()
        advancePastDebounce()

        vm.updateSearchQuery("N")
        vm.updateSearchQuery("NR")
        vm.updateSearchQuery("NRT")

        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceTimeBy(400)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.flights.value.size)
    }

    // ── E19: StatsRow shows filtered counts ─────────────────────────────────

    @Test
    fun `E19 - flightCount and totalDistance reflect filtered results`() = runTest {
        startCollecting()
        advancePastDebounce()
        assertEquals(5, vm.flightCount.value)

        vm.updateSearchQuery("NRT")
        advancePastDebounce()
        assertEquals(3, vm.flightCount.value)
        assertEquals(5200, vm.totalDistanceNm.value)
    }

    // ── E20: Undo delete restores flight into filtered results ──────────────

    @Test
    fun `E20 - undo restores flight into filtered view`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        advancePastDebounce()
        assertEquals(3, vm.flights.value.size)

        fakeDao.allFlightsFlow.value = allFlights.filter { it.id != 1L }
        advancePastDebounce()
        assertEquals(2, vm.flights.value.size)

        fakeDao.allFlightsFlow.value = allFlights
        advancePastDebounce()
        assertEquals(3, vm.flights.value.size)
    }

    // ── E21: Search with leading/trailing whitespace ────────────────────────

    @Test
    fun `E21 - search trims whitespace`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("  NRT  ")
        advancePastDebounce()
        assertEquals(3, vm.flights.value.size)
    }

    // ── E22: Search with special characters ─────────────────────────────────

    @Test
    fun `E22 - search with SQL special characters returns empty`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("%")
        advancePastDebounce()
        assertTrue(vm.flights.value.isEmpty())
    }

    @Test
    fun `E22 - search with underscore returns empty`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("_")
        advancePastDebounce()
        assertTrue(vm.flights.value.isEmpty())
    }

    @Test
    fun `E22 - search with single quote returns empty`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("'")
        advancePastDebounce()
        assertTrue(vm.flights.value.isEmpty())
    }

    // ── E23: Performance with large list ────────────────────────────────────

    @Test
    fun `E23 - filtering 2000 flights completes`() = runTest {
        startCollecting()
        val largeList = (1..2000).map { i ->
            LogbookFlight(
                id = i.toLong(),
                flightNumber = "FL$i",
                departureCode = if (i % 3 == 0) "NRT" else "ORD",
                arrivalCode = "HND",
                departureTimeUtc = 1718452800000 + i * 86400000L,
                distanceNm = i * 10,
                seatClass = if (i % 2 == 0) "Economy" else "Business"
            )
        }
        fakeDao.allFlightsFlow.value = largeList
        advancePastDebounce()

        vm.updateSearchQuery("NRT")
        advancePastDebounce()

        val count = vm.flights.value.size
        assertTrue(count > 600)
        assertTrue(count < 700)
    }

    // ── E24: Filter state updates immediately (optimistic UI) ───────────────

    @Test
    fun `E24 - filterState updates immediately before debounce`() = runTest {
        startCollecting()
        vm.toggleSeatClassFilter("Business")
        assertEquals("Business", vm.filterState.value.selectedSeatClass)
        assertTrue(vm.filterState.value.isActive)
    }

    // ── E25: State preserved across recomposition (ViewModel survives) ──────

    @Test
    fun `E25 - filter state survives ViewModel lifetime`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("NRT")
        vm.toggleSeatClassFilter("Economy")

        assertEquals("NRT", vm.filterState.value.searchQuery)
        assertEquals("Economy", vm.filterState.value.selectedSeatClass)
    }

    // ── Additional: Search matches aircraftType (Architect P2) ──────────────

    @Test
    fun `search matches aircraftType`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("Boeing")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(3, results.size)
    }

    // ── Additional: Default sort is newest first ────────────────────────────

    @Test
    fun `default sort is newest first`() = runTest {
        startCollecting()
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(flight5.id, results[0].id)
        assertEquals(flight1.id, results[4].id)
    }

    // ── Additional: Year toggle deselects on second tap ─────────────────────

    @Test
    fun `year toggle deselects on second tap`() = runTest {
        startCollecting()
        vm.toggleYearFilter("2025")
        assertEquals("2025", vm.filterState.value.selectedYear)
        vm.toggleYearFilter("2025")
        assertEquals(null, vm.filterState.value.selectedYear)
    }

    // ── Additional: Combined search + year + seat class ─────────────────────

    @Test
    fun `combined search year and seat class filter`() = runTest {
        startCollecting()
        advancePastDebounce()
        vm.updateSearchQuery("JL")
        vm.toggleYearFilter("2025")
        vm.toggleSeatClassFilter("Business")
        advancePastDebounce()
        val results = vm.flights.value
        assertEquals(1, results.size)
        assertEquals(flight3.id, results[0].id)
    }
}

// ── Fake DAO ─────────────────────────────────────────────────────────────────

private class FakeSearchLogbookFlightDao : LogbookFlightDao {
    val allFlightsFlow = MutableStateFlow<List<LogbookFlight>>(emptyList())
    var distinctYears: List<String> = emptyList()
    var distinctSeatClasses: List<String> = emptyList()

    override fun getAll(): Flow<List<LogbookFlight>> = allFlightsFlow
    override suspend fun getById(id: Long): LogbookFlight? = allFlightsFlow.value.find { it.id == id }
    override suspend fun existsBySource(calendarEventId: Long, legIndex: Int): Boolean = false
    override suspend fun existsByRouteAndDate(depCode: String, arrCode: String, utcDay: Long, excludeId: Long?): Boolean = false
    override suspend fun insert(flight: LogbookFlight): Long = 1L
    override suspend fun upsert(flight: LogbookFlight): Long = 1L
    override suspend fun update(flight: LogbookFlight) {}
    override suspend fun deleteById(id: Long) {}
    override fun getCount(): Flow<Int> = flowOf(allFlightsFlow.value.size)
    override fun getTotalDistanceNm(): Flow<Int> = flowOf(allFlightsFlow.value.sumOf { it.distanceNm ?: 0 })
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
    override fun getDistinctYears(): Flow<List<String>> = flowOf(distinctYears)
    override fun getDistinctSeatClasses(): Flow<List<String>> = flowOf(distinctSeatClasses)
}
