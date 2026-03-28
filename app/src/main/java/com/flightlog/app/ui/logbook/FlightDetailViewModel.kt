package com.flightlog.app.ui.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.AirportRepository
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FlightDetailUiState {
    data object Loading : FlightDetailUiState()
    data class Success(
        val flight: LogbookFlight,
        val departureCityName: String? = null,
        val arrivalCityName: String? = null
    ) : FlightDetailUiState()
    data object NotFound : FlightDetailUiState()
}

@HiltViewModel
class FlightDetailViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val airportRepository: AirportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val flightId: Long = checkNotNull(savedStateHandle["flightId"])

    val uiState: StateFlow<FlightDetailUiState> = repository.getByIdFlow(flightId)
        .map { flight ->
            if (flight != null) {
                val depCity = airportRepository.getByIata(flight.departureCode)?.city
                val arrCity = airportRepository.getByIata(flight.arrivalCode)?.city
                FlightDetailUiState.Success(flight, depCity, arrCity)
            } else {
                FlightDetailUiState.NotFound
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlightDetailUiState.Loading)

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private var hasLoadedSuccessfully = false

    fun onUiStateChanged(state: FlightDetailUiState) {
        if (state is FlightDetailUiState.Success) {
            hasLoadedSuccessfully = true
        }
    }

    fun shouldAutoNavigateBack(state: FlightDetailUiState): Boolean =
        hasLoadedSuccessfully && state is FlightDetailUiState.NotFound

    fun requestDelete() {
        _showDeleteConfirmation.value = true
    }

    fun cancelDelete() {
        _showDeleteConfirmation.value = false
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(flightId)
            _showDeleteConfirmation.value = false
            onDeleted()
        }
    }
}
