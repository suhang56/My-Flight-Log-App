package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.airport.AirportCoordinatesMap
import com.flightlog.app.data.airport.AirportTimezoneMap
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.FlightRoute
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Lookup status for the FlightAware API call. */
sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Success(val route: FlightRoute) : LookupState
    data class Disambiguate(val routes: List<FlightRoute>) : LookupState
    data class Error(val message: String) : LookupState
}

data class LogbookFormState(
    // -- User input (Step 1) --
    val flightNumber: String = "",
    val departureDateMillis: Long? = null,
    val departureAirportForLookup: String = "",
    val showDepartureAirportLookupField: Boolean = false,

    // -- Auto-populated from API --
    val departureCode: String = "",
    val arrivalCode: String = "",
    val departureTimeMillis: Long? = null,
    val arrivalTimeMillis: Long? = null,
    val aircraftType: String = "",
    val registration: String = "",
    val departureTimezone: String? = null,
    val arrivalTimezone: String? = null,

    // -- Personal details (user-editable) --
    val seatClass: String = "",
    val seatNumber: String = "",
    val notes: String = "",

    // -- Lookup --
    val lookupState: LookupState = LookupState.Idle,

    // -- Edit mode --
    val isEditMode: Boolean = false,
    val editId: Long = 0,
    val sourceCalendarEventId: Long? = null,
    val originalCreatedAt: Long? = null,

    // -- Save --
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,

    // -- Validation --
    val flightNumberError: String? = null,
    val departureDateError: String? = null
)

@HiltViewModel
class AddEditLogbookFlightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LogbookRepository,
    private val flightRouteService: FlightRouteService
) : ViewModel() {

    private val _formState = MutableStateFlow(LogbookFormState())
    val formState: StateFlow<LogbookFormState> = _formState.asStateFlow()

    init {
        val flightId = savedStateHandle.get<Long>("id")
        if (flightId != null && flightId > 0) {
            viewModelScope.launch {
                val flight = repository.getById(flightId) ?: return@launch
                _formState.value = LogbookFormState(
                    flightNumber = flight.flightNumber,
                    departureDateMillis = flight.departureTimeMillis,
                    departureCode = flight.departureCode,
                    arrivalCode = flight.arrivalCode,
                    departureTimeMillis = flight.departureTimeMillis,
                    arrivalTimeMillis = flight.arrivalTimeMillis,
                    aircraftType = flight.aircraftType ?: "",
                    registration = flight.registration ?: "",
                    departureTimezone = flight.departureTimezone,
                    arrivalTimezone = flight.arrivalTimezone,
                    seatClass = flight.seatClass ?: "",
                    seatNumber = flight.seatNumber ?: "",
                    notes = flight.notes ?: "",
                    lookupState = LookupState.Idle,
                    isEditMode = true,
                    editId = flight.id,
                    sourceCalendarEventId = flight.sourceCalendarEventId,
                    originalCreatedAt = flight.createdAt
                )
            }
        }
    }

    fun updateFlightNumber(value: String) {
        _formState.update { it.copy(flightNumber = value.uppercase().trim(), flightNumberError = null) }
    }

    fun updateDepartureDate(millis: Long?) {
        val showField = millis != null && isFutureBeyondFlightAwareWindow(millis)
        _formState.update {
            it.copy(
                departureDateMillis = millis,
                departureDateError = null,
                showDepartureAirportLookupField = showField
            )
        }
    }

    fun updateDepartureAirportForLookup(value: String) {
        _formState.update { it.copy(departureAirportForLookup = value.uppercase().trim()) }
    }

    private fun isFutureBeyondFlightAwareWindow(dateMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(dateMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val today = LocalDate.now(ZoneOffset.UTC)
        return date.toEpochDay() - today.toEpochDay() > FLIGHTAWARE_WINDOW_DAYS
    }

    fun updateSeatClass(value: String) {
        _formState.update { it.copy(seatClass = value) }
    }

    fun updateSeatNumber(value: String) {
        _formState.update { it.copy(seatNumber = value) }
    }

    fun updateNotes(value: String) {
        _formState.update { it.copy(notes = value) }
    }

    // Allow manual override of auto-populated fields in edit mode
    fun updateDepartureCode(value: String) {
        _formState.update { it.copy(departureCode = value.uppercase().take(4)) }
    }

    fun updateArrivalCode(value: String) {
        _formState.update { it.copy(arrivalCode = value.uppercase().take(4)) }
    }

    fun updateAircraftType(value: String) {
        _formState.update { it.copy(aircraftType = value) }
    }

    /** Trigger API lookup with current flight number + date. */
    fun lookupFlight() {
        val state = _formState.value
        var hasError = false

        if (state.flightNumber.isBlank()) {
            _formState.update { it.copy(flightNumberError = "Flight number required") }
            hasError = true
        }
        if (state.departureDateMillis == null) {
            _formState.update { it.copy(departureDateError = "Date required") }
            hasError = true
        }
        if (hasError) return

        _formState.update { it.copy(lookupState = LookupState.Loading) }

        viewModelScope.launch {
            val date = Instant.ofEpochMilli(state.departureDateMillis ?: return@launch)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()

            val depAirport = state.departureAirportForLookup.takeIf { it.isNotBlank() }
            val routes = flightRouteService.lookupAllRoutes(state.flightNumber, date, depAirport)

            when {
                routes.isEmpty() -> {
                    _formState.update {
                        it.copy(lookupState = LookupState.Error("No flights found for ${state.flightNumber} on this date"))
                    }
                }
                routes.size == 1 -> {
                    applyRoute(routes.first())
                }
                else -> {
                    _formState.update {
                        it.copy(lookupState = LookupState.Disambiguate(routes))
                    }
                }
            }
        }
    }

    /** User selects a specific flight from disambiguation list. */
    fun selectRoute(route: FlightRoute) {
        applyRoute(route)
    }

    private fun applyRoute(route: FlightRoute) {
        _formState.update {
            it.copy(
                departureCode = route.departureIata,
                arrivalCode = route.arrivalIata,
                departureTimeMillis = route.departureScheduledUtc,
                arrivalTimeMillis = route.arrivalScheduledUtc,
                aircraftType = route.aircraftType ?: "",
                registration = route.registration ?: "",
                departureTimezone = route.departureTimezone,
                arrivalTimezone = route.arrivalTimezone,
                lookupState = LookupState.Success(route)
            )
        }
    }

    /** Reset lookup to go back to Step 1 input. */
    fun resetLookup() {
        _formState.update {
            it.copy(
                lookupState = LookupState.Idle,
                departureCode = "",
                arrivalCode = "",
                departureTimeMillis = null,
                arrivalTimeMillis = null,
                aircraftType = "",
                registration = "",
                departureTimezone = null,
                arrivalTimezone = null
            )
        }
    }

    fun saveFlight() {
        val state = _formState.value
        var hasError = false

        if (state.flightNumber.isBlank()) {
            _formState.update { it.copy(flightNumberError = "Flight number required") }
            hasError = true
        }
        if (state.departureDateMillis == null) {
            _formState.update { it.copy(departureDateError = "Date required") }
            hasError = true
        }
        if (state.departureCode.length < 3) {
            hasError = true
        }
        if (state.arrivalCode.length < 3) {
            hasError = true
        }
        if (hasError) return

        _formState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val depTz = state.departureTimezone
                ?: AirportTimezoneMap.getTimezone(state.departureCode)
            val zoneId = if (depTz != null) ZoneId.of(depTz) else ZoneId.systemDefault()
            val departureDateMillis = state.departureDateMillis ?: return@launch
            val departureDateEpochDay = Instant.ofEpochMilli(departureDateMillis)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()

            val departureTimeMillis = state.departureTimeMillis ?: departureDateMillis

            val durationMinutes = if (state.arrivalTimeMillis != null && state.arrivalTimeMillis > departureTimeMillis) {
                ((state.arrivalTimeMillis - departureTimeMillis) / 60_000).toInt()
            } else {
                null
            }

            val distanceKm = AirportCoordinatesMap.greatCircleKm(state.departureCode, state.arrivalCode)
            val arrTz = state.arrivalTimezone
                ?: AirportTimezoneMap.getTimezone(state.arrivalCode)
            val now = System.currentTimeMillis()

            val flight = LogbookFlight(
                id = if (state.isEditMode) state.editId else 0,
                sourceCalendarEventId = state.sourceCalendarEventId,
                flightNumber = state.flightNumber,
                departureCode = state.departureCode,
                arrivalCode = state.arrivalCode,
                departureDateEpochDay = departureDateEpochDay,
                departureTimeMillis = departureTimeMillis,
                arrivalTimeMillis = state.arrivalTimeMillis,
                durationMinutes = durationMinutes,
                departureTimezone = depTz,
                arrivalTimezone = arrTz,
                aircraftType = state.aircraftType.ifBlank { null },
                registration = state.registration.ifBlank { null },
                seatClass = state.seatClass.ifBlank { null },
                seatNumber = state.seatNumber.ifBlank { null },
                distanceKm = distanceKm,
                notes = state.notes.ifBlank { null },
                createdAt = if (state.isEditMode) (state.originalCreatedAt ?: now) else now,
                updatedAt = now
            )

            if (state.isEditMode) {
                repository.update(flight)
            } else {
                repository.insert(flight)
            }

            _formState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun deleteFlight() {
        val state = _formState.value
        if (!state.isEditMode) return
        viewModelScope.launch {
            val flight = repository.getById(state.editId) ?: return@launch
            repository.delete(flight)
            _formState.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        private const val FLIGHTAWARE_WINDOW_DAYS = 7L

        /** Format a UTC epoch millis to a local time string for display. */
        fun formatUtcMillisToLocalTime(millis: Long, timezone: String?): String {
            val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            val zdt = Instant.ofEpochMilli(millis).atZone(zone)
            return zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        }
    }
}
