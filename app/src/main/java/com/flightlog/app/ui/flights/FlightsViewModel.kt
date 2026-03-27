package com.flightlog.app.ui.flights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.FlightEntity
import com.flightlog.app.data.repository.FlightRepository
import com.flightlog.app.data.repository.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FlightFilter {
    ALL, UPCOMING, PAST
}

data class FlightsUiState(
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val selectedFilter: FlightFilter = FlightFilter.ALL,
    val permissionRequired: Boolean = false
)

@HiltViewModel
class FlightsViewModel @Inject constructor(
    private val application: Application,
    private val repository: FlightRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FlightsUiState())
    val uiState: StateFlow<FlightsUiState> = _uiState.asStateFlow()

    val allFlights: StateFlow<List<FlightEntity>> = repository.getAllFlights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flightCount: StateFlow<Int> = repository.getFlightCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setFilter(filter: FlightFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun syncCalendar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)

            val contentResolver = application.contentResolver
            when (val result = repository.syncFromCalendar(contentResolver)) {
                is SyncResult.Success -> {
                    val message = buildString {
                        append("Sync complete: ")
                        val parts = mutableListOf<String>()
                        if (result.newCount > 0) parts.add("${result.newCount} new")
                        if (result.updatedCount > 0) parts.add("${result.updatedCount} updated")
                        if (result.removedCount > 0) parts.add("${result.removedCount} removed")
                        if (parts.isEmpty()) append("no changes")
                        else append(parts.joinToString(", "))
                    }
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = message
                    )
                }
                is SyncResult.Error -> {
                    val needsPermission = result.cause is SecurityException
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = result.message,
                        permissionRequired = needsPermission
                    )
                }
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun onPermissionGranted() {
        _uiState.value = _uiState.value.copy(permissionRequired = false)
        syncCalendar()
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            permissionRequired = false,
            syncMessage = "Calendar permission is required to sync flights"
        )
    }
}
