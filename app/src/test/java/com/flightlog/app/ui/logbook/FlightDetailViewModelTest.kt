package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.PlanespottersApi
import com.flightlog.app.data.network.PlanespottersPhoto
import com.flightlog.app.data.network.PlanespottersResponse
import com.flightlog.app.data.network.PlanespottersSize
import com.flightlog.app.data.network.PlanespottersThumbnail
import com.flightlog.app.data.repository.AirportRepository
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException

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

    @Test
    fun `fetchAircraftPhoto with null registration does not call API`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto(null)
        advanceUntilIdle()

        coVerify(exactly = 0) { planespottersApi.getPhotosByRegistration(any()) }
        assertFalse(viewModel.aircraftPhotoState.value.isLoading)
        assertNull(viewModel.aircraftPhotoState.value.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with blank registration does not call API`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("  ")
        advanceUntilIdle()

        coVerify(exactly = 0) { planespottersApi.getPhotosByRegistration(any()) }
    }

    @Test
    fun `fetchAircraftPhoto with empty string does not call API`() = runTest {
        val viewModel = createViewModel()

        viewModel.fetchAircraftPhoto("")
        advanceUntilIdle()

        coVerify(exactly = 0) { planespottersApi.getPhotosByRegistration(any()) }
    }

    @Test
    fun `fetchAircraftPhoto with valid registration returns photo`() = runTest {
        val photo = PlanespottersPhoto(
            id = "123",
            thumbnailLarge = PlanespottersThumbnail(
                src = "https://example.com/photo.jpg",
                size = PlanespottersSize(width = 400, height = 300)
            ),
            photographer = "John Doe"
        )
        val response = Response.success(PlanespottersResponse(photos = listOf(photo)))
        coEvery { planespottersApi.getPhotosByRegistration("N12345") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertEquals("https://example.com/photo.jpg", state.photoUrl)
        assertEquals("John Doe", state.photographer)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with empty photos array returns null photo`() = runTest {
        val response = Response.success(PlanespottersResponse(photos = emptyList()))
        coEvery { planespottersApi.getPhotosByRegistration("N99999") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N99999")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertNull(state.photographer)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with null photos returns null photo`() = runTest {
        val response = Response.success(PlanespottersResponse(photos = null))
        coEvery { planespottersApi.getPhotosByRegistration("N99999") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N99999")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with network error returns empty state`() = runTest {
        coEvery { planespottersApi.getPhotosByRegistration(any()) } throws IOException("Network error")

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto with HTTP 429 returns empty state`() = runTest {
        val errorResponse = Response.error<PlanespottersResponse>(
            429,
            "".toResponseBody(null)
        )
        coEvery { planespottersApi.getPhotosByRegistration("N12345") } returns errorResponse

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertFalse(state.isLoading)
    }

    @Test
    fun `fetchAircraftPhoto does not call API twice for same registration`() = runTest {
        val photo = PlanespottersPhoto(
            id = "123",
            thumbnailLarge = PlanespottersThumbnail(
                src = "https://example.com/photo.jpg",
                size = null
            ),
            photographer = "Jane"
        )
        val response = Response.success(PlanespottersResponse(photos = listOf(photo)))
        coEvery { planespottersApi.getPhotosByRegistration("N12345") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        // Call again -- should be no-op since photoUrl is already set
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        coVerify(exactly = 1) { planespottersApi.getPhotosByRegistration("N12345") }
    }

    @Test
    fun `fetchAircraftPhoto with photo missing thumbnailLarge returns null url`() = runTest {
        val photo = PlanespottersPhoto(
            id = "123",
            thumbnailLarge = null,
            photographer = "Jane"
        )
        val response = Response.success(PlanespottersResponse(photos = listOf(photo)))
        coEvery { planespottersApi.getPhotosByRegistration("N12345") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
    }

    @Test
    fun `fetchAircraftPhoto with photo missing src returns null url`() = runTest {
        val photo = PlanespottersPhoto(
            id = "123",
            thumbnailLarge = PlanespottersThumbnail(src = null, size = null),
            photographer = "Jane"
        )
        val response = Response.success(PlanespottersResponse(photos = listOf(photo)))
        coEvery { planespottersApi.getPhotosByRegistration("N12345") } returns response

        val viewModel = createViewModel()
        viewModel.fetchAircraftPhoto("N12345")
        advanceUntilIdle()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
    }

    @Test
    fun `initial aircraftPhotoState is empty`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.aircraftPhotoState.value
        assertNull(state.photoUrl)
        assertNull(state.photographer)
        assertFalse(state.isLoading)
    }

    @Test
    fun `uiState emits Success with flight data`() = runTest {
        val viewModel = createViewModel()

        // Use first{} to actively collect from the WhileSubscribed StateFlow
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
