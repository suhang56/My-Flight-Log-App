package com.flightlog.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogbookStats(
    val flightCount: Int = 0,
    val totalDurationMinutes: Long = 0,
    val totalDistanceKm: Long = 0,
    val uniqueAirportCount: Int = 0
)

@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val repository: LogbookRepository
) : ViewModel() {

    val allFlights: StateFlow<List<LogbookFlight>> = repository.allFlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<LogbookStats> = combine(
        repository.getFlightCount(),
        repository.getTotalDurationMinutes(),
        repository.getTotalDistanceKm(),
        repository.getUniqueAirportCount()
    ) { count, duration, distance, airports ->
        LogbookStats(
            flightCount = count,
            totalDurationMinutes = duration ?: 0L,
            totalDistanceKm = distance ?: 0L,
            uniqueAirportCount = airports
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookStats())

    fun deleteFlight(id: Long) {
        viewModelScope.launch {
            val flight = repository.getById(id) ?: return@launch
            repository.delete(flight)
        }
    }
}
