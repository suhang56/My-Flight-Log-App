package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.local.model.AirlineCount
import com.flightlog.app.data.local.model.AirportCount
import com.flightlog.app.data.local.model.LabelCount
import com.flightlog.app.data.local.model.MonthlyCount
import com.flightlog.app.data.local.model.RouteCount
import com.flightlog.app.data.local.dao.AirportDao
import com.flightlog.app.data.repository.AchievementRepository
import com.flightlog.app.data.repository.AirportRepository
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class FlightDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeDetailLogbookFlightDao
    private lateinit var repository: LogbookRepository
    private lateinit var airportRepository: AirportRepository

    private val fullFlight = LogbookFlight(
        id = 42,
        sourceCalendarEventId = 12345L,
        sourceLegIndex = 0,
        flightNumber = "JL 5",
        departureCode = "NRT",
        arrivalCode = "JFK",
        departureTimeUtc = 1742886600000L,
        arrivalTimeUtc = 1742934300000L,
        departureTimezone = "Asia/Tokyo",
        arrivalTimezone = "America/New_York",
        distanceNm = 6732,
        aircraftType = "Boeing 777-300ER",
        seatClass = "Business",
        seatNumber = "4A",
        notes = "Great window view of Mt. Fuji on descent.",
        addedAt = 1742041800000L
    )

    private val minimalFlight = LogbookFlight(
        id = 99,
        departureCode = "LAX",
        arrivalCode = "SFO",
        departureTimeUtc = 1742886600000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeDetailLogbookFlightDao()
        val mockAirportDao = mockk<AirportDao>()
        coEvery { mockAirportDao.getByIata(any()) } returns null
        airportRepository = AirportRepository(mockAirportDao)
        val mockAchievementRepo = mockk<AchievementRepository>(relaxUnitFun = true)
        coEvery { mockAchievementRepo.checkAndUnlock() } returns Unit
        repository = LogbookRepository(fakeDao, airportRepository, mockAchievementRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(flightId: Long = 42L): FlightDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("flightId" to flightId))
        return FlightDetailViewModel(repository, airportRepository, savedStateHandle)
    }

    private fun TestScope.startCollecting(vm: FlightDetailViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.showDeleteConfirmation.collect {} }
    }

    @Test
    fun `E1 valid flightId transitions Loading to Success`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        assertEquals(FlightDetailUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is FlightDetailUiState.Success)
        assertEquals(fullFlight, (state as FlightDetailUiState.Success).flight)
    }

    @Test
    fun `E2 unknown flightId transitions Loading to NotFound`() = runTest {
        val vm = createViewModel(9999L)
        startCollecting(vm)
        advanceUntilIdle()
        assertEquals(FlightDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `E3 flightId zero returns NotFound`() = runTest {
        val vm = createViewModel(0L)
        startCollecting(vm)
        advanceUntilIdle()
        assertEquals(FlightDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `E13 cancel delete resets confirmation flag`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        vm.requestDelete()
        assertTrue(vm.showDeleteConfirmation.value)
        vm.cancelDelete()
        assertFalse(vm.showDeleteConfirmation.value)
        assertTrue(vm.uiState.value is FlightDetailUiState.Success)
    }

    @Test
    fun `E14 confirm delete calls repository and onDeleted callback`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        var navigatedBack = false
        vm.requestDelete()
        vm.confirmDelete { navigatedBack = true }
        advanceUntilIdle()
        assertTrue(fakeDao.deleteCalled)
        assertEquals(42L, fakeDao.lastDeletedId)
        assertTrue(navigatedBack)
        assertFalse(vm.showDeleteConfirmation.value)
    }

    @Test
    fun `E14b confirm delete only calls callback once`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        var callCount = 0
        vm.confirmDelete { callCount++ }
        advanceUntilIdle()
        assertEquals(1, callCount)
        assertEquals(1, fakeDao.deleteCallCount)
    }

    @Test
    fun `E16 flow updates when flight is edited externally`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        val edited = fullFlight.copy(notes = "Updated notes")
        fakeDao.flightByIdFlow.value = edited
        advanceUntilIdle()
        val state = vm.uiState.value as FlightDetailUiState.Success
        assertEquals("Updated notes", state.flight.notes)
    }

    @Test
    fun `E17 shouldAutoNavigateBack true when flight deleted after successful load`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        vm.onUiStateChanged(vm.uiState.value)
        fakeDao.flightByIdFlow.value = null
        advanceUntilIdle()
        assertTrue(vm.shouldAutoNavigateBack(vm.uiState.value))
    }

    @Test
    fun `E17b shouldAutoNavigateBack false when flight was never found`() = runTest {
        val vm = createViewModel(9999L)
        startCollecting(vm)
        advanceUntilIdle()
        assertFalse(vm.shouldAutoNavigateBack(vm.uiState.value))
    }

    @Test
    fun `E21 uiState retains value without re-fetch`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        val state1 = vm.uiState.value
        val state2 = vm.uiState.value
        assertEquals(state1, state2)
        assertTrue(state1 is FlightDetailUiState.Success)
    }

    @Test
    fun `E23 calendar-synced flight has non-null sourceCalendarEventId`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        val flight = (vm.uiState.value as FlightDetailUiState.Success).flight
        assertTrue(flight.sourceCalendarEventId != null)
    }

    @Test
    fun `E24 manually added flight has null sourceCalendarEventId`() = runTest {
        fakeDao.flightByIdFlow.value = minimalFlight
        val vm = createViewModel(99L)
        startCollecting(vm)
        advanceUntilIdle()
        val flight = (vm.uiState.value as FlightDetailUiState.Success).flight
        assertTrue(flight.sourceCalendarEventId == null)
    }

    @Test
    fun `requestDelete without confirm does not call repository`() = runTest {
        fakeDao.flightByIdFlow.value = fullFlight
        val vm = createViewModel(42L)
        startCollecting(vm)
        advanceUntilIdle()
        vm.requestDelete()
        advanceUntilIdle()
        assertFalse(fakeDao.deleteCalled)
    }

    @Test
    fun `flightId is extracted from SavedStateHandle`() = runTest {
        val vm = createViewModel(42L)
        assertEquals(42L, vm.flightId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // buildShareText tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `E18 buildShareText with full flight includes all lines`() {
        val text = buildShareText(fullFlight)
        val lines = text.lines()
        assertTrue(lines[0].contains("JL 5"))
        assertTrue(lines[0].contains("NRT"))
        assertTrue(lines[0].contains("JFK"))
        assertTrue(lines[1].contains("\u2192"))
        assertTrue(lines[2].contains("Duration:"))
        assertTrue(lines[2].contains("13h 15m"))
        assertTrue(lines[2].contains("Distance:"))
        assertTrue(lines[2].contains("6,732 NM"))
        assertTrue(lines[3].contains("Aircraft: Boeing 777-300ER"))
        assertTrue(lines[3].contains("Business"))
        assertTrue(lines[3].contains("Seat 4A"))
        assertTrue(lines.last().contains("Logged with My Flight Log"))
    }

    @Test
    fun `E19 buildShareText with minimal flight omits optional lines`() {
        val text = buildShareText(minimalFlight)
        val lines = text.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("LAX"))
        assertTrue(lines[0].contains("SFO"))
        assertFalse(text.contains("Duration:"))
        assertFalse(text.contains("Distance:"))
        assertFalse(text.contains("Aircraft:"))
        assertTrue(lines.last().contains("Logged with My Flight Log"))
    }

    @Test
    fun `E7 buildShareText hides duration when arrival before departure`() {
        val badFlight = fullFlight.copy(arrivalTimeUtc = fullFlight.departureTimeUtc - 1000)
        assertFalse(buildShareText(badFlight).contains("Duration:"))
    }

    @Test
    fun `E7b buildShareText hides duration when arrival equals departure`() {
        val sameFlight = fullFlight.copy(arrivalTimeUtc = fullFlight.departureTimeUtc)
        assertFalse(buildShareText(sameFlight).contains("Duration:"))
    }

    @Test
    fun `buildShareText with distance but no arrival shows only distance`() {
        val flight = minimalFlight.copy(distanceNm = 337)
        val text = buildShareText(flight)
        assertTrue(text.contains("Distance: 337 NM"))
        assertFalse(text.contains("Duration:"))
    }

    @Test
    fun `buildShareText with only aircraft type omits seat info`() {
        val flight = minimalFlight.copy(aircraftType = "A320")
        val text = buildShareText(flight)
        assertTrue(text.contains("Aircraft: A320"))
        assertFalse(text.contains("Seat"))
    }

    @Test
    fun `buildShareText with only seat class and number omits aircraft`() {
        val flight = minimalFlight.copy(seatClass = "Economy", seatNumber = "22F")
        val text = buildShareText(flight)
        assertFalse(text.contains("Aircraft:"))
        assertTrue(text.contains("Economy"))
        assertTrue(text.contains("Seat 22F"))
    }

    @Test
    fun `buildShareText always ends with attribution`() {
        assertTrue(buildShareText(minimalFlight).endsWith("Logged with My Flight Log"))
    }

    @Test
    fun `buildShareText has no blank lines`() {
        buildShareText(fullFlight).lines().forEach { assertTrue(it.isNotBlank()) }
        buildShareText(minimalFlight).lines().forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `buildShareText starts with plane emoji`() {
        assertTrue(buildShareText(fullFlight).startsWith("\u2708"))
    }
}

// ── Fake DAO ─────────────────────────────────────────────────────────────────

private class FakeDetailLogbookFlightDao : LogbookFlightDao {
    val flightByIdFlow = MutableStateFlow<LogbookFlight?>(null)
    var deleteCalled: Boolean = false
    var deleteCallCount: Int = 0
    var lastDeletedId: Long? = null

    override fun getByIdFlow(id: Long): Flow<LogbookFlight?> = flightByIdFlow
    override fun getAll(): Flow<List<LogbookFlight>> = flowOf(emptyList())
    override suspend fun getAllOnce(): List<LogbookFlight> = emptyList()
    override suspend fun getById(id: Long): LogbookFlight? = flightByIdFlow.value
    override suspend fun existsBySource(calendarEventId: Long, legIndex: Int): Boolean = false
    override suspend fun existsByRouteAndDate(depCode: String, arrCode: String, utcDay: Long, excludeId: Long?): Boolean = false
    override suspend fun insert(flight: LogbookFlight): Long = 1L
    override suspend fun upsert(flight: LogbookFlight): Long = 1L
    override suspend fun update(flight: LogbookFlight) {}
    override suspend fun deleteById(id: Long) {
        deleteCalled = true
        deleteCallCount++
        lastDeletedId = id
    }
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
    override fun getDistinctYears(): Flow<List<String>> = flowOf(emptyList())
    override fun getDistinctSeatClasses(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getMostRecentFlight(): LogbookFlight? = null
}
