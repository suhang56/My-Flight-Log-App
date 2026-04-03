package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.AircraftTypePhotoProvider
import com.flightlog.app.data.network.PlanespottersApi
import com.flightlog.app.data.repository.AirportRepository
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class FlightDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: LogbookRepository
    private lateinit var airportRepository: AirportRepository
    private lateinit var planespottersApi: PlanespottersApi
    private lateinit var savedStateHandle: SavedStateHandle

    private val testFlight = LogbookFlight(
        id = 1L,
        flightNumber = "AA100",
        departureCode = "JFK",
        arrivalCode = "LAX",
        departureDateEpochDay = 20550L,
        departureTimeMillis = 1743552000000L,
        aircraftType = "B738"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        airportRepository = mockk(relaxed = true)
        planespottersApi = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("flightId" to 1L))

        every { repository.getByIdFlow(1L) } returns flowOf(testFlight)
        coEvery { airportRepository.getByIata(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FlightDetailViewModel {
        return FlightDetailViewModel(
            repository = repository,
            airportRepository = airportRepository,
            planespottersApi = planespottersApi,
            savedStateHandle = savedStateHandle
        )
    }

    // -- fetchAircraftPhoto tests (static type-based lookup) --

    @Test
    fun `fetchAircraftPhoto with null type does not set photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto(null)

        assertNull(viewModel.aircraftPhotoState.value.photoUrl)
        assertFalse(viewModel.aircraftPhotoState.value.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with blank type does not set photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("  ")

        assertNull(viewModel.aircraftPhotoState.value.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with empty string does not set photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("")

        assertNull(viewModel.aircraftPhotoState.value.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with known type B738 returns photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("B738")

        val state = viewModel.aircraftPhotoState.value
        assertNotNull(state.photoUrl)
        assertNotNull(state.photographer)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with known type A320 returns photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("A320")

        val state = viewModel.aircraftPhotoState.value
        assertNotNull(state.photoUrl)
        assertNotNull(state.photographer)
    }

    @Test
    fun `fetchAircraftPhoto with alias type B73H resolves to B738 photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("B73H")

        val state = viewModel.aircraftPhotoState.value
        val expectedInfo = AircraftTypePhotoProvider.getPhotoForType("B738")
        assertEquals(expectedInfo?.photoUrl, state.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with unknown type returns null photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("ZZZZ")

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertNull(state.photographer)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto is case insensitive`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("b738")

        val state = viewModel.aircraftPhotoState.value
        assertNotNull(state.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with leading trailing whitespace still resolves`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("  B738  ")

        val state = viewModel.aircraftPhotoState.value
        assertNotNull(state.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto does not overwrite existing photo`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("B738")
        val firstUrl = viewModel.aircraftPhotoState.value.photoUrl
        assertNotNull(firstUrl)

        // Call again with different type -- should be no-op since photoUrl is already set
        viewModel.fetchAircraftPhoto("A320")
        assertEquals(firstUrl, viewModel.aircraftPhotoState.value.photoUrl)
    }

    @Test
    fun `initial aircraftPhotoState is empty`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertNull(state.photographer)
        assertFalse(state.isLoading)
    }

    // -- Other ViewModel tests --

    @Test
    fun `uiState emits Success with flight data`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it !is FlightDetailUiState.Loading }
        assertTrue(state is FlightDetailUiState.Success)
        assertEquals(testFlight, (state as FlightDetailUiState.Success).flight)
    }

    @Test
    fun `uiState emits NotFound when flight is null`() = runTest {
        every { repository.getByIdFlow(1L) } returns flowOf(null)

        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it !is FlightDetailUiState.Loading }
        assertTrue(state is FlightDetailUiState.NotFound)
    }

    @Test
    fun `requestDelete sets showDeleteConfirmation to true`() = runTest {
        val viewModel = createViewModel()

        viewModel.requestDelete()

        assertTrue(viewModel.showDeleteConfirmation.value)
    }

    @Test
    fun `cancelDelete sets showDeleteConfirmation to false`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestDelete()

        viewModel.cancelDelete()

        assertFalse(viewModel.showDeleteConfirmation.value)
    }

    @Test
    fun `shouldAutoNavigateBack returns false before success`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.shouldAutoNavigateBack(FlightDetailUiState.NotFound))
    }

    @Test
    fun `shouldAutoNavigateBack returns true after success then not found`() = runTest {
        val viewModel = createViewModel()
        viewModel.onUiStateChanged(FlightDetailUiState.Success(testFlight))

        assertTrue(viewModel.shouldAutoNavigateBack(FlightDetailUiState.NotFound))
    }
}
