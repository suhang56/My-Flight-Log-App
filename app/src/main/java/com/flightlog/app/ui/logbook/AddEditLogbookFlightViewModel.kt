package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.AirportCoordinatesMap
import com.flightlog.app.data.AirportTimezoneMap
import com.flightlog.app.data.local.entity.LogbookFlight
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
import javax.inject.Inject

data class LogbookFormState(
    val departureCode: String = "",
    val arrivalCode: String = "",
    val departureDateMillis: Long? = null,
    val departureTimeMillis: Long? = null,
    val arrivalTimeMillis: Long? = null,
    val flightNumber: String = "",
    val aircraftType: String = "",
    val seatClass: String = "",
    val seatNumber: String = "",
    val notes: String = "",
    val isEditMode: Boolean = false,
    val editId: Long = 0,
    val sourceCalendarEventId: Long? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val departureCodeError: String? = null,
    val arrivalCodeError: String? = null,
    val departureDateError: String? = null
)

@HiltViewModel
class AddEditLogbookFlightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LogbookRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(LogbookFormState())
    val formState: StateFlow<LogbookFormState> = _formState.asStateFlow()

    init {
        val flightId = savedStateHandle.get<Long>("id")
        if (flightId != null && flightId > 0) {
            viewModelScope.launch {
                val flight = repository.getById(flightId) ?: return@launch
                _formState.value = LogbookFormState(
                    departureCode = flight.departureCode,
                    arrivalCode = flight.arrivalCode,
                    departureDateMillis = flight.departureTimeMillis,
                    departureTimeMillis = flight.departureTimeMillis,
                    arrivalTimeMillis = flight.arrivalTimeMillis,
                    flightNumber = flight.flightNumber,
                    aircraftType = flight.aircraftType ?: "",
                    seatClass = flight.seatClass ?: "",
                    seatNumber = flight.seatNumber ?: "",
                    notes = flight.notes ?: "",
                    isEditMode = true,
                    editId = flight.id,
                    sourceCalendarEventId = flight.sourceCalendarEventId
                )
            }
        }
    }

    fun updateDepartureCode(value: String) {
        _formState.update { it.copy(departureCode = value.uppercase().take(4), departureCodeError = null) }
    }

    fun updateArrivalCode(value: String) {
        _formState.update { it.copy(arrivalCode = value.uppercase().take(4), arrivalCodeError = null) }
    }

    fun updateDepartureDate(millis: Long?) {
        _formState.update { it.copy(departureDateMillis = millis, departureDateError = null) }
    }

    fun updateDepartureTime(millis: Long?) {
        _formState.update { it.copy(departureTimeMillis = millis) }
    }

    fun updateArrivalTime(millis: Long?) {
        _formState.update { it.copy(arrivalTimeMillis = millis) }
    }

    fun updateFlightNumber(value: String) {
        _formState.update { it.copy(flightNumber = value) }
    }

    fun updateAircraftType(value: String) {
        _formState.update { it.copy(aircraftType = value) }
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

    fun saveFlight() {
        val state = _formState.value
        var hasError = false

        if (state.departureCode.length < 3) {
            _formState.update { it.copy(departureCodeError = "Min 3 characters") }
            hasError = true
        }
        if (state.arrivalCode.length < 3) {
            _formState.update { it.copy(arrivalCodeError = "Min 3 characters") }
            hasError = true
        }
        if (state.departureDateMillis == null) {
            _formState.update { it.copy(departureDateError = "Date required") }
            hasError = true
        }
        if (hasError) return

        _formState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val depTz = AirportTimezoneMap.getTimezone(state.departureCode)
            val zoneId = if (depTz != null) ZoneId.of(depTz) else ZoneId.systemDefault()
            val departureDateEpochDay = Instant.ofEpochMilli(state.departureDateMillis!!)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()

            val departureTimeMillis = state.departureTimeMillis ?: state.departureDateMillis

            val durationMinutes = if (state.arrivalTimeMillis != null && state.arrivalTimeMillis > departureTimeMillis) {
                ((state.arrivalTimeMillis - departureTimeMillis) / 60_000).toInt()
            } else {
                null
            }

            val distanceKm = AirportCoordinatesMap.greatCircleKm(state.departureCode, state.arrivalCode)
            val arrTz = AirportTimezoneMap.getTimezone(state.arrivalCode)
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
                seatClass = state.seatClass.ifBlank { null },
                seatNumber = state.seatNumber.ifBlank { null },
                distanceKm = distanceKm,
                notes = state.notes.ifBlank { null },
                createdAt = if (state.isEditMode) now else now,
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
}
