package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.FlightRoute
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.repository.LogbookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditLogbookFlightViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: LogbookRepository
    private lateinit var flightRouteService: FlightRouteService
    private lateinit var savedStateHandle: SavedStateHandle

    // 2026-04-02 in UTC millis (midnight)
    private val april2Date = 1743552000000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxUnitFun = true)
        flightRouteService = mockk()
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = savedStateHandle): AddEditLogbookFlightViewModel {
        return AddEditLogbookFlightViewModel(handle, repository, flightRouteService)
    }

    // -- Validation tests --

    @Test
    fun `lookupFlight with empty flight number shows error`() = runTest {
        val vm = createViewModel()
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        assertEquals("Flight number required", vm.formState.value.flightNumberError)
        assertEquals(LookupState.Idle, vm.formState.value.lookupState)
    }

    @Test
    fun `lookupFlight with no date shows error`() = runTest {
        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.lookupFlight()
        advanceUntilIdle()

        assertEquals("Date required", vm.formState.value.departureDateError)
    }

    @Test
    fun `lookupFlight with blank flight number and no date shows both errors`() = runTest {
        val vm = createViewModel()
        vm.lookupFlight()
        advanceUntilIdle()

        assertEquals("Flight number required", vm.formState.value.flightNumberError)
        assertEquals("Date required", vm.formState.value.departureDateError)
    }

    @Test
    fun `saveFlight with missing required fields does not save`() = runTest {
        val vm = createViewModel()
        vm.saveFlight()
        advanceUntilIdle()

        assertFalse(vm.formState.value.isSaved)
        coVerify(exactly = 0) { repository.insert(any()) }
    }

    // -- Lookup flow tests --

    @Test
    fun `lookupFlight with single result auto-populates fields`() = runTest {
        val route = FlightRoute(
            flightNumber = "NH211",
            departureIata = "NRT",
            arrivalIata = "SFO",
            departureTimezone = "Asia/Tokyo",
            arrivalTimezone = "America/Los_Angeles",
            departureScheduledUtc = 1743570000000L,
            arrivalScheduledUtc = 1743603600000L,
            aircraftType = "B789"
        )
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.lookupState is LookupState.Success)
        assertEquals("NRT", state.departureCode)
        assertEquals("SFO", state.arrivalCode)
        assertEquals("B789", state.aircraftType)
        assertEquals("Asia/Tokyo", state.departureTimezone)
        assertEquals("America/Los_Angeles", state.arrivalTimezone)
        assertEquals(1743570000000L, state.departureTimeMillis)
        assertEquals(1743603600000L, state.arrivalTimeMillis)
    }

    @Test
    fun `lookupFlight with multiple results enters disambiguation state`() = runTest {
        val route1 = FlightRoute("NH211", "NRT", "SFO", departureScheduledUtc = 1743570000000L, arrivalScheduledUtc = 1743603600000L)
        val route2 = FlightRoute("NH211", "NRT", "LAX", departureScheduledUtc = 1743580000000L, arrivalScheduledUtc = 1743613600000L)
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route1, route2)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.lookupState is LookupState.Disambiguate)
        assertEquals(2, (state.lookupState as LookupState.Disambiguate).routes.size)
        // Fields should NOT be populated yet
        assertEquals("", state.departureCode)
    }

    @Test
    fun `selectRoute from disambiguation populates fields`() = runTest {
        val route1 = FlightRoute("NH211", "NRT", "SFO", aircraftType = "B789")
        val route2 = FlightRoute("NH211", "NRT", "LAX", aircraftType = "B77W")
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route1, route2)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        vm.selectRoute(route2)
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.lookupState is LookupState.Success)
        assertEquals("NRT", state.departureCode)
        assertEquals("LAX", state.arrivalCode)
        assertEquals("B77W", state.aircraftType)
    }

    @Test
    fun `lookupFlight with no results shows error`() = runTest {
        coEvery { flightRouteService.lookupAllRoutes("XX999", any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.updateFlightNumber("XX999")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        assertTrue(vm.formState.value.lookupState is LookupState.Error)
        assertTrue((vm.formState.value.lookupState as LookupState.Error).message.contains("No flights found"))
    }

    // -- Reset flow --

    @Test
    fun `resetLookup clears auto-populated fields`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO", aircraftType = "B789")
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        vm.resetLookup()
        val state = vm.formState.value
        assertEquals(LookupState.Idle, state.lookupState)
        assertEquals("", state.departureCode)
        assertEquals("", state.arrivalCode)
        assertEquals("", state.aircraftType)
        assertNull(state.departureTimeMillis)
        assertNull(state.arrivalTimeMillis)
        // Flight number and date should be preserved
        assertEquals("NH211", state.flightNumber)
        assertEquals(april2Date, state.departureDateMillis)
    }

    // -- Save flow tests --

    @Test
    fun `saveFlight after successful lookup inserts flight`() = runTest {
        val route = FlightRoute(
            "NH211", "NRT", "SFO",
            departureTimezone = "Asia/Tokyo",
            arrivalTimezone = "America/Los_Angeles",
            departureScheduledUtc = 1743570000000L,
            arrivalScheduledUtc = 1743603600000L,
            aircraftType = "B789"
        )
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)
        coEvery { repository.insert(any()) } returns 1L

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        vm.updateSeatClass("business")
        vm.updateSeatNumber("2K")
        vm.updateNotes("Great flight")
        vm.saveFlight()
        advanceUntilIdle()

        assertTrue(vm.formState.value.isSaved)
        coVerify { repository.insert(match { flight ->
            flight.flightNumber == "NH211" &&
            flight.departureCode == "NRT" &&
            flight.arrivalCode == "SFO" &&
            flight.aircraftType == "B789" &&
            flight.seatClass == "business" &&
            flight.seatNumber == "2K" &&
            flight.notes == "Great flight"
        }) }
    }

    // -- Edge case: flight number normalization --

    @Test
    fun `flight number is uppercased and trimmed`() = runTest {
        val vm = createViewModel()
        vm.updateFlightNumber("  nh211  ")

        // The ViewModel uppercases and trims
        assertEquals("NH211", vm.formState.value.flightNumber)
    }

    @Test
    fun `updating flight number clears error`() = runTest {
        val vm = createViewModel()
        vm.lookupFlight() // triggers error
        advanceUntilIdle()
        assertEquals("Flight number required", vm.formState.value.flightNumberError)

        vm.updateFlightNumber("NH211")
        assertNull(vm.formState.value.flightNumberError)
    }

    @Test
    fun `updating date clears error`() = runTest {
        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.lookupFlight() // triggers date error
        advanceUntilIdle()
        assertEquals("Date required", vm.formState.value.departureDateError)

        vm.updateDepartureDate(april2Date)
        assertNull(vm.formState.value.departureDateError)
    }

    // -- Edge case: blank optional fields saved as null --

    @Test
    fun `blank seat class and notes saved as null`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO",
            departureTimezone = "Asia/Tokyo",
            departureScheduledUtc = 1743570000000L)
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)
        coEvery { repository.insert(any()) } returns 1L

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        vm.updateSeatClass("")
        vm.updateSeatNumber("")
        vm.updateNotes("")
        vm.saveFlight()
        advanceUntilIdle()

        coVerify { repository.insert(match { flight ->
            flight.seatClass == null &&
            flight.seatNumber == null &&
            flight.notes == null
        }) }
    }

    // -- Edge case: API returns route with null times --

    @Test
    fun `route with null scheduled times still populates airports`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO",
            departureScheduledUtc = null,
            arrivalScheduledUtc = null,
            aircraftType = null)
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.lookupState is LookupState.Success)
        assertEquals("NRT", state.departureCode)
        assertEquals("SFO", state.arrivalCode)
        assertNull(state.departureTimeMillis)
        assertNull(state.arrivalTimeMillis)
        assertEquals("", state.aircraftType)
    }

    // -- Edit mode --

    @Test
    fun `edit mode loads existing flight data`() = runTest {
        val existingFlight = LogbookFlight(
            id = 42,
            flightNumber = "JL5",
            departureCode = "HND",
            arrivalCode = "SFO",
            departureDateEpochDay = 20547,
            departureTimeMillis = 1743570000000L,
            arrivalTimeMillis = 1743603600000L,
            aircraftType = "B77W",
            seatClass = "first",
            seatNumber = "1A",
            notes = "Amazing"
        )
        coEvery { repository.getById(42) } returns existingFlight

        val handle = SavedStateHandle(mapOf("id" to 42L))
        val vm = createViewModel(handle)
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.isEditMode)
        assertEquals(42L, state.editId)
        assertEquals("JL5", state.flightNumber)
        assertEquals("HND", state.departureCode)
        assertEquals("SFO", state.arrivalCode)
        assertEquals("B77W", state.aircraftType)
        assertEquals("first", state.seatClass)
        assertEquals("1A", state.seatNumber)
        assertEquals("Amazing", state.notes)
    }

    @Test
    fun `delete in edit mode calls repository delete`() = runTest {
        val existingFlight = LogbookFlight(
            id = 42, flightNumber = "JL5", departureCode = "HND", arrivalCode = "SFO",
            departureDateEpochDay = 20547, departureTimeMillis = 1743570000000L
        )
        coEvery { repository.getById(42) } returns existingFlight

        val handle = SavedStateHandle(mapOf("id" to 42L))
        val vm = createViewModel(handle)
        advanceUntilIdle()

        vm.deleteFlight()
        advanceUntilIdle()

        coVerify { repository.delete(existingFlight) }
        assertTrue(vm.formState.value.isSaved)
    }

    @Test
    fun `delete in non-edit mode does nothing`() = runTest {
        val vm = createViewModel()
        vm.deleteFlight()
        advanceUntilIdle()

        assertFalse(vm.formState.value.isSaved)
        coVerify(exactly = 0) { repository.delete(any<LogbookFlight>()) }
    }

    // -- Departure airport field visibility for dual-API --

    @Test
    fun `showDepartureAirportLookupField is false for near-future date`() = runTest {
        val vm = createViewModel()
        // Date within 7 days — FlightAware window
        val nearFuture = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3)
        val millis = nearFuture.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        vm.updateDepartureDate(millis)
        assertFalse(vm.formState.value.showDepartureAirportLookupField)
    }

    @Test
    fun `showDepartureAirportLookupField is false for exactly 7 days out`() = runTest {
        val vm = createViewModel()
        val boundaryDate = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(7)
        val millis = boundaryDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        vm.updateDepartureDate(millis)
        assertFalse(vm.formState.value.showDepartureAirportLookupField)
    }

    @Test
    fun `showDepartureAirportLookupField is true for 8 days out`() = runTest {
        val vm = createViewModel()
        val futureDate = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(8)
        val millis = futureDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        vm.updateDepartureDate(millis)
        assertTrue(vm.formState.value.showDepartureAirportLookupField)
    }

    @Test
    fun `showDepartureAirportLookupField is false for null date`() = runTest {
        val vm = createViewModel()
        vm.updateDepartureDate(null)
        assertFalse(vm.formState.value.showDepartureAirportLookupField)
    }

    @Test
    fun `showDepartureAirportLookupField is false for past date`() = runTest {
        val vm = createViewModel()
        val pastDate = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(10)
        val millis = pastDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        vm.updateDepartureDate(millis)
        assertFalse(vm.formState.value.showDepartureAirportLookupField)
    }

    @Test
    fun `updateDepartureAirportForLookup uppercases and trims`() = runTest {
        val vm = createViewModel()
        vm.updateDepartureAirportForLookup("  nrt  ")
        assertEquals("NRT", vm.formState.value.departureAirportForLookup)
    }

    @Test
    fun `departure airport is passed to lookupAllRoutes`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO", aircraftType = "B789")
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), eq("NRT")) } returns listOf(route)

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        val futureDate = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(15)
        val millis = futureDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        vm.updateDepartureDate(millis)
        vm.updateDepartureAirportForLookup("NRT")
        vm.lookupFlight()
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.lookupState is LookupState.Success)
        assertEquals("NRT", state.departureCode)
        coVerify { flightRouteService.lookupAllRoutes("NH211", any(), "NRT") }
    }

    @Test
    fun `departure airport is null when field is blank`() = runTest {
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), isNull()) } returns emptyList()

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()

        coVerify { flightRouteService.lookupAllRoutes("NH211", any(), isNull()) }
    }

    // -- Edge case: formatUtcMillisToLocalTime --

    @Test
    fun `formatUtcMillisToLocalTime with valid timezone`() {
        // 2026-04-02 05:00:00 UTC = 2026-04-02 14:00 JST (Asia/Tokyo = UTC+9)
        val result = AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(
            1743570000000L, "Asia/Tokyo"
        )
        assertEquals("14:00", result)
    }

    @Test
    fun `formatUtcMillisToLocalTime with null timezone uses system default`() {
        // Should not crash with null timezone
        val result = AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(
            1743570000000L, null
        )
        assertTrue(result.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `formatUtcMillisToLocalTime with invalid timezone falls back to system default`() {
        val result = AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(
            1743570000000L, "Invalid/Zone"
        )
        assertTrue(result.matches(Regex("\\d{2}:\\d{2}")))
    }

    // -- Edge case: duration calculation --

    @Test
    fun `save computes positive duration when arrival after departure`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO",
            departureTimezone = "Asia/Tokyo",
            departureScheduledUtc = 1743570000000L,    // 05:00 UTC
            arrivalScheduledUtc = 1743603600000L       // 14:20 UTC (+9h20m = 560 min)
        )
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)
        coEvery { repository.insert(any()) } returns 1L

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()
        vm.saveFlight()
        advanceUntilIdle()

        coVerify { repository.insert(match { flight ->
            flight.durationMinutes != null && flight.durationMinutes!! > 0
        }) }
    }

    @Test
    fun `save sets null duration when arrival is null`() = runTest {
        val route = FlightRoute("NH211", "NRT", "SFO",
            departureTimezone = "Asia/Tokyo",
            departureScheduledUtc = 1743570000000L,
            arrivalScheduledUtc = null
        )
        coEvery { flightRouteService.lookupAllRoutes("NH211", any(), any()) } returns listOf(route)
        coEvery { repository.insert(any()) } returns 1L

        val vm = createViewModel()
        vm.updateFlightNumber("NH211")
        vm.updateDepartureDate(april2Date)
        vm.lookupFlight()
        advanceUntilIdle()
        vm.saveFlight()
        advanceUntilIdle()

        coVerify { repository.insert(match { flight ->
            flight.durationMinutes == null
        }) }
    }
}
