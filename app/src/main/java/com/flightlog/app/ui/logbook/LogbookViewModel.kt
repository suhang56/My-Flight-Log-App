package com.flightlog.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogbookUiState(
    val selectedFlight: LogbookFlight? = null,
    val showDetailSheet: Boolean = false
)

@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val repository: LogbookRepository
) : ViewModel() {

    val flights: StateFlow<List<LogbookFlight>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val flightCount: StateFlow<Int> = repository.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalDistanceNm: StateFlow<Int> = repository.getTotalDistanceNm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(LogbookUiState())
    val uiState: StateFlow<LogbookUiState> = _uiState.asStateFlow()

    fun selectFlight(flight: LogbookFlight) {
        _uiState.update { it.copy(selectedFlight = flight, showDetailSheet = true) }
    }

    fun dismissDetailSheet() {
        _uiState.update { it.copy(showDetailSheet = false, selectedFlight = null) }
    }

    fun deleteFlight(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(showDetailSheet = false, selectedFlight = null) }
        }
    }
}
