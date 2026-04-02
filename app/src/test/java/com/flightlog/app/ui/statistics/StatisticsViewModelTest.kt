package com.flightlog.app.ui.statistics

import com.flightlog.app.data.local.dao.AirlineCount
import com.flightlog.app.data.local.dao.MonthlyCount
import com.flightlog.app.data.local.dao.RouteCount
import com.flightlog.app.data.local.dao.SeatClassCount
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: LogbookRepository

    private val countFlow = MutableStateFlow(0)
    private val durationFlow = MutableStateFlow<Long?>(0L)
    private val distanceFlow = MutableStateFlow<Long?>(0L)
    private val airportCountFlow = MutableStateFlow(0)
    private val seatClassFlow = MutableStateFlow<List<SeatClassCount>>(emptyList())
    private val airlinesFlow = MutableStateFlow<List<AirlineCount>>(emptyList())
    private val longestDistFlow = MutableStateFlow<LogbookFlight?>(null)
    private val longestDurFlow = MutableStateFlow<LogbookFlight?>(null)
    private val routesFlow = MutableStateFlow<List<RouteCount>>(emptyList())
    private val firstFlightFlow = MutableStateFlow<LogbookFlight?>(null)
    private val monthlyFlow = MutableStateFlow<List<MonthlyCount>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)

        every { repository.getFlightCount() } returns countFlow
        every { repository.getTotalDurationMinutes() } returns durationFlow
        every { repository.getTotalDistanceKm() } returns distanceFlow
        every { repository.getUniqueAirportCount() } returns airportCountFlow
        every { repository.getSeatClassCounts() } returns seatClassFlow
        every { repository.getTopAirlines() } returns airlinesFlow
        every { repository.getLongestFlightByDistance() } returns longestDistFlow
        every { repository.getLongestFlightByDuration() } returns longestDurFlow
        every { repository.getTopRoutes() } returns routesFlow
        every { repository.getFirstFlight() } returns firstFlightFlow
        every { repository.getMonthlyFlightCounts() } returns monthlyFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StatisticsViewModel(repository)

    private fun logbookFlight(
        distanceKm: Int? = 5200,
        departureTimeMillis: Long = 1_700_000_000_000L,
        arrivalTimeMillis: Long? = 1_700_050_000_000L
    ) = LogbookFlight(
        flightNumber = "NH847",
        departureCode = "HND",
        arrivalCode = "LHR",
        departureDateEpochDay = departureTimeMillis / 86_400_000,
        departureTimeMillis = departureTimeMillis,
        arrivalTimeMillis = arrivalTimeMillis,
        distanceKm = distanceKm
    )

    @Test
    fun `initial state has all zeros and empty lists`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        val state = vm.stats.value
        assertEquals(0, state.flightCount)
        assertEquals(0L, state.totalDurationMinutes)
        assertEquals(0L, state.totalDistanceKm)
        assertEquals(0, state.uniqueAirportCount)
        assertTrue(state.seatClassCounts.isEmpty())
        assertTrue(state.topAirlines.isEmpty())
        assertNull(state.longestByDistance)
        assertNull(state.longestByDuration)
        assertTrue(state.topRoutes.isEmpty())
        assertNull(state.firstFlight)
        assertTrue(state.monthlyFlightCounts.isEmpty())

        job.cancel()
    }

    @Test
    fun `flight count and distance flow through`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        countFlow.value = 42
        distanceFlow.value = 125000L
        advanceUntilIdle()

        assertEquals(42, vm.stats.value.flightCount)
        assertEquals(125000L, vm.stats.value.totalDistanceKm)

        job.cancel()
    }

    @Test
    fun `null duration treated as zero`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        durationFlow.value = null
        advanceUntilIdle()

        assertEquals(0L, vm.stats.value.totalDurationMinutes)

        job.cancel()
    }

    @Test
    fun `airlines flow through`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        airlinesFlow.value = listOf(AirlineCount("NH", 10), AirlineCount("JL", 5))
        advanceUntilIdle()

        assertEquals(2, vm.stats.value.topAirlines.size)
        assertEquals("NH", vm.stats.value.topAirlines[0].airlineCode)

        job.cancel()
    }

    @Test
    fun `longest flight by distance flows through`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        longestDistFlow.value = logbookFlight(distanceKm = 9000)
        advanceUntilIdle()

        assertEquals(9000, vm.stats.value.longestByDistance?.distanceKm)

        job.cancel()
    }

    @Test
    fun `first flight flows through`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        firstFlightFlow.value = logbookFlight(departureTimeMillis = 1_500_000_000_000L)
        advanceUntilIdle()

        assertEquals(1_500_000_000_000L, vm.stats.value.firstFlight?.departureTimeMillis)

        job.cancel()
    }

    @Test
    fun `monthly counts flow through`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        monthlyFlow.value = listOf(MonthlyCount("2026-01", 5), MonthlyCount("2026-02", 3))
        advanceUntilIdle()

        assertEquals(2, vm.stats.value.monthlyFlightCounts.size)

        job.cancel()
    }

    @Test
    fun `stats update reactively`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        countFlow.value = 10
        advanceUntilIdle()
        assertEquals(10, vm.stats.value.flightCount)

        countFlow.value = 20
        advanceUntilIdle()
        assertEquals(20, vm.stats.value.flightCount)

        job.cancel()
    }

    @Test
    fun `multiple upstream flows updating simultaneously`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        countFlow.value = 5
        distanceFlow.value = 10000L
        durationFlow.value = 600L
        airportCountFlow.value = 8
        advanceUntilIdle()

        val state = vm.stats.value
        assertEquals(5, state.flightCount)
        assertEquals(10000L, state.totalDistanceKm)
        assertEquals(600L, state.totalDurationMinutes)
        assertEquals(8, state.uniqueAirportCount)

        job.cancel()
    }
}
