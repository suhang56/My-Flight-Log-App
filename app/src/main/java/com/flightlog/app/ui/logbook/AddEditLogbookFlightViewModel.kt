package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.Airport
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.FlightRouteService
import com.flightlog.app.data.repository.AirportRepository
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class AddEditFormState(
    val flightNumber: String = "",
    val departureCode: String = "",
    val arrivalCode: String = "",
    val date: LocalDate = LocalDate.now(),
    val departureTime: LocalTime = LocalTime.of(12, 0),
    val arrivalTime: LocalTime? = null,
    val aircraftType: String = "",
    val seatClass: String = "",
    val seatNumber: String = "",
    val notes: String = "",
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val departureCodeError: String? = null,
    val arrivalCodeError: String? = null,
    val duplicateWarning: String? = null,
    val duplicateCheckPassed: Boolean = false,
    val flightSearchQuery: String = "",
    val flightSearchDate: LocalDate = LocalDate.now(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val autoFillApplied: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AddEditLogbookFlightViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val airportRepository: AirportRepository,
    private val flightRouteService: FlightRouteService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editId: Long? = savedStateHandle.get<Long>("flightId")?.takeIf { it > 0 }

    private val _form = MutableStateFlow(AddEditFormState(isEditMode = editId != null))
    val form: StateFlow<AddEditFormState> = _form.asStateFlow()

    private val _departureQuery = MutableStateFlow("")
    private val _arrivalQuery = MutableStateFlow("")

    private val _departureSuggestions = MutableStateFlow<List<Airport>>(emptyList())
    val departureSuggestions: StateFlow<List<Airport>> = _departureSuggestions.asStateFlow()

    private val _arrivalSuggestions = MutableStateFlow<List<Airport>>(emptyList())
    val arrivalSuggestions: StateFlow<List<Airport>> = _arrivalSuggestions.asStateFlow()

    private var existingFlight: LogbookFlight? = null
    private var searchJob: Job? = null

    init {
        if (editId != null) {
            viewModelScope.launch {
                repository.getById(editId)?.let { flight ->
                    existingFlight = flight
                    val depZone = flight.departureTimezone?.let {
                        runCatching { ZoneId.of(it) }.getOrNull()
                    } ?: ZoneId.systemDefault()
                    val arrZone = flight.arrivalTimezone?.let {
                        runCatching { ZoneId.of(it) }.getOrNull()
                    } ?: ZoneId.systemDefault()

                    val depZdt = Instant.ofEpochMilli(flight.departureTimeUtc).atZone(depZone)
                    val arrTime = flight.arrivalTimeUtc?.let {
                        Instant.ofEpochMilli(it).atZone(arrZone).toLocalTime()
                    }

                    _form.update {
                        it.copy(
                            flightNumber = flight.flightNumber,
                            departureCode = flight.departureCode,
                            arrivalCode = flight.arrivalCode,
                            date = depZdt.toLocalDate(),
                            departureTime = depZdt.toLocalTime(),
                            arrivalTime = arrTime,
                            aircraftType = flight.aircraftType,
                            seatClass = flight.seatClass,
                            seatNumber = flight.seatNumber,
                            notes = flight.notes,
                            isEditMode = true
                        )
                    }
                }
            }
        }

        // Debounced departure autocomplete
        viewModelScope.launch {
            _departureQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    _departureSuggestions.value = if (query.length >= 2) {
                        airportRepository.search(query)
                    } else {
                        emptyList()
                    }
                }
        }

        // Debounced arrival autocomplete
        viewModelScope.launch {
            _arrivalQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    _arrivalSuggestions.value = if (query.length >= 2) {
                        airportRepository.search(query)
                    } else {
                        emptyList()
                    }
                }
        }
    }

    fun updateFlightNumber(value: String) { _form.update { it.copy(flightNumber = value) } }
    fun updateDepartureCode(value: String) {
        _form.update { it.copy(departureCode = value.uppercase(), departureCodeError = null, duplicateCheckPassed = false, autoFillApplied = false) }
        _departureQuery.value = value.uppercase()
    }
    fun updateArrivalCode(value: String) {
        _form.update { it.copy(arrivalCode = value.uppercase(), arrivalCodeError = null, duplicateCheckPassed = false, autoFillApplied = false) }
        _arrivalQuery.value = value.uppercase()
    }

    fun selectDepartureAirport(airport: Airport) {
        _form.update { it.copy(departureCode = airport.iata, departureCodeError = null, duplicateCheckPassed = false, autoFillApplied = false) }
        _departureSuggestions.value = emptyList()
        _departureQuery.value = ""
    }

    fun selectArrivalAirport(airport: Airport) {
        _form.update { it.copy(arrivalCode = airport.iata, arrivalCodeError = null, duplicateCheckPassed = false, autoFillApplied = false) }
        _arrivalSuggestions.value = emptyList()
        _arrivalQuery.value = ""
    }

    fun dismissDepartureSuggestions() { _departureSuggestions.value = emptyList() }
    fun dismissArrivalSuggestions() { _arrivalSuggestions.value = emptyList() }

    fun updateDate(value: LocalDate) { _form.update { it.copy(date = value, duplicateCheckPassed = false) } }
    fun updateDepartureTime(value: LocalTime) { _form.update { it.copy(departureTime = value) } }
    fun updateArrivalTime(value: LocalTime?) { _form.update { it.copy(arrivalTime = value) } }
    fun updateAircraftType(value: String) { _form.update { it.copy(aircraftType = value) } }
    fun updateSeatClass(value: String) { _form.update { it.copy(seatClass = value) } }
    fun updateSeatNumber(value: String) { _form.update { it.copy(seatNumber = value) } }
    fun updateNotes(value: String) { _form.update { it.copy(notes = value) } }

    fun dismissDuplicateWarning() {
        _form.update { it.copy(duplicateWarning = null) }
    }

    fun confirmSaveDespiteDuplicate() {
        _form.update { it.copy(duplicateWarning = null, duplicateCheckPassed = true) }
        save()
    }

    fun updateFlightSearchQuery(value: String) {
        _form.update { it.copy(flightSearchQuery = value.uppercase(), searchError = null) }
    }

    fun updateFlightSearchDate(value: LocalDate) {
        _form.update { it.copy(flightSearchDate = value, searchError = null) }
    }

    fun searchFlight() {
        val query = _form.value.flightSearchQuery.trim().ifBlank { return }
        searchJob?.cancel()
        _form.update { it.copy(isSearching = true, searchError = null) }
        searchJob = viewModelScope.launch {
            val route = try {
                flightRouteService.lookupRoute(query, _form.value.flightSearchDate)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (route != null) {
                val depZone = route.departureTimezone
                    ?.let { tz -> runCatching { ZoneId.of(tz) }.getOrNull() }
                    ?: ZoneId.systemDefault()
                val arrZone = route.arrivalTimezone
                    ?.let { tz -> runCatching { ZoneId.of(tz) }.getOrNull() }
                    ?: ZoneId.systemDefault()

                _form.update {
                    it.copy(
                        isSearching = false,
                        flightNumber = route.flightNumber,
                        departureCode = route.departureIata,
                        arrivalCode = route.arrivalIata,
                        date = it.flightSearchDate,
                        departureTime = route.departureScheduledUtc
                            ?.let { ms -> Instant.ofEpochMilli(ms).atZone(depZone).toLocalTime() }
                            ?: it.departureTime,
                        arrivalTime = route.arrivalScheduledUtc
                            ?.let { ms -> Instant.ofEpochMilli(ms).atZone(arrZone).toLocalTime() }
                            ?: it.arrivalTime,
                        aircraftType = route.aircraftType ?: it.aircraftType,
                        autoFillApplied = true,
                        duplicateCheckPassed = false
                    )
                }
            } else {
                _form.update {
                    it.copy(
                        isSearching = false,
                        searchError = "Flight not found. Check the flight number and date, or enter details manually."
                    )
                }
            }
        }
    }

    fun dismissAutoFillBanner() {
        _form.update { it.copy(autoFillApplied = false) }
    }

    fun save() {
        val current = _form.value

        var hasError = false
        if (current.departureCode.length != 3) {
            _form.update { it.copy(departureCodeError = "Enter 3-letter airport code") }
            hasError = true
        }
        if (current.arrivalCode.length != 3) {
            _form.update { it.copy(arrivalCodeError = "Enter 3-letter airport code") }
            hasError = true
        }
        if (hasError) return

        _form.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val depAirport = airportRepository.getByIata(current.departureCode)
            val arrAirport = airportRepository.getByIata(current.arrivalCode)
            val depTz = depAirport?.timezone
            val arrTz = arrAirport?.timezone
            val depZone = depTz?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
            val arrZone = arrTz?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()

            val departureUtc = current.date.atTime(current.departureTime)
                .atZone(depZone).toInstant().toEpochMilli()

            val arrivalUtc = current.arrivalTime?.let { arrTime ->
                var arrDate = current.date
                if (arrTime < current.departureTime) {
                    arrDate = arrDate.plusDays(1)
                }
                arrDate.atTime(arrTime).atZone(arrZone).toInstant().toEpochMilli()
            }

            // Duplicate guard
            if (!current.duplicateCheckPassed) {
                val isDuplicate = repository.existsByRouteAndDate(
                    depCode = current.departureCode.trim(),
                    arrCode = current.arrivalCode.trim(),
                    departureTimeUtc = departureUtc,
                    excludeId = editId
                )
                if (isDuplicate) {
                    _form.update {
                        it.copy(
                            isSaving = false,
                            duplicateWarning = "A flight ${current.departureCode} \u2192 ${current.arrivalCode} on this date already exists in your logbook."
                        )
                    }
                    return@launch
                }
            }

            val distance = airportRepository.distanceNm(current.departureCode, current.arrivalCode)

            val existing = existingFlight
            if (editId != null && existing != null) {
                val updated = existing.copy(
                    flightNumber = current.flightNumber.trim(),
                    departureCode = current.departureCode.trim(),
                    arrivalCode = current.arrivalCode.trim(),
                    departureTimeUtc = departureUtc,
                    arrivalTimeUtc = arrivalUtc,
                    departureTimezone = depTz,
                    arrivalTimezone = arrTz,
                    distanceNm = distance,
                    aircraftType = current.aircraftType.trim(),
                    seatClass = current.seatClass.trim(),
                    seatNumber = current.seatNumber.trim(),
                    notes = current.notes.trim()
                )
                repository.update(updated)
            } else {
                val flight = LogbookFlight(
                    flightNumber = current.flightNumber.trim(),
                    departureCode = current.departureCode.trim(),
                    arrivalCode = current.arrivalCode.trim(),
                    departureTimeUtc = departureUtc,
                    arrivalTimeUtc = arrivalUtc,
                    departureTimezone = depTz,
                    arrivalTimezone = arrTz,
                    distanceNm = distance,
                    aircraftType = current.aircraftType.trim(),
                    seatClass = current.seatClass.trim(),
                    seatNumber = current.seatNumber.trim(),
                    notes = current.notes.trim()
                )
                repository.insert(flight)
            }

            _form.update { it.copy(isSaving = false, savedSuccessfully = true, duplicateCheckPassed = false) }
        }
    }

    fun delete() {
        if (editId == null) return
        viewModelScope.launch {
            repository.delete(editId)
            _form.update { it.copy(savedSuccessfully = true) }
        }
    }
}
