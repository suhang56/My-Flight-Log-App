package com.flightlog.app.ui.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.FlightEntity
import com.flightlog.app.data.repository.FlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlightDetailViewModel @Inject constructor(
    private val repository: FlightRepository
) : ViewModel() {

    private val _flight = MutableStateFlow<FlightEntity?>(null)
    val flight: StateFlow<FlightEntity?> = _flight.asStateFlow()

    fun loadFlight(id: Long) {
        viewModelScope.launch {
            _flight.value = repository.getFlightById(id)
        }
    }
}
