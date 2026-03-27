package com.flightlog.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

enum class LogbookSortOrder(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    LONGEST_DISTANCE("Longest Distance")
}

data class LogbookFilterState(
    val searchQuery: String = "",
    val selectedSeatClass: String? = null,
    val selectedYear: String? = null,
    val sortOrder: LogbookSortOrder = LogbookSortOrder.NEWEST_FIRST
) {
    val isActive: Boolean get() =
        searchQuery.isNotBlank() || selectedSeatClass != null || selectedYear != null
            || sortOrder != LogbookSortOrder.NEWEST_FIRST
}

data class LogbookUiState(
    val deletedFlight: LogbookFlight? = null,
    val snackbarMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val repository: LogbookRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(LogbookFilterState())
    val filterState: StateFlow<LogbookFilterState> = _filterState.asStateFlow()

    val availableYears: StateFlow<List<String>> = repository.getDistinctYears()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableSeatClasses: StateFlow<List<String>> = repository.getDistinctSeatClasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allFlights: StateFlow<List<LogbookFlight>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val flights: StateFlow<List<LogbookFlight>> =
        combine(
            allFlights,
            _filterState.debounce(300L)
        ) { all, filter ->
            all
                .filter { flight -> matchesSearch(flight, filter.searchQuery) }
                .filter { flight -> filter.selectedSeatClass == null || flight.seatClass == filter.selectedSeatClass }
                .filter { flight -> filter.selectedYear == null || flightYear(flight) == filter.selectedYear }
                .let { filtered ->
                    when (filter.sortOrder) {
                        LogbookSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.departureTimeUtc }
                        LogbookSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.departureTimeUtc }
                        LogbookSortOrder.LONGEST_DISTANCE -> filtered.sortedByDescending { it.distanceNm ?: -1 }
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val flightCount: StateFlow<Int> = flights.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalDistanceNm: StateFlow<Int> = flights.map { list ->
        list.sumOf { it.distanceNm ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalFlightCount: StateFlow<Int> = allFlights.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(LogbookUiState())
    val uiState: StateFlow<LogbookUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun toggleSeatClassFilter(seatClass: String) {
        _filterState.update {
            it.copy(selectedSeatClass = if (it.selectedSeatClass == seatClass) null else seatClass)
        }
    }

    fun toggleYearFilter(year: String) {
        _filterState.update {
            it.copy(selectedYear = if (it.selectedYear == year) null else year)
        }
    }

    fun setSortOrder(order: LogbookSortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
    }

    fun clearFilters() {
        _filterState.update { LogbookFilterState() }
    }

    private fun matchesSearch(flight: LogbookFlight, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().uppercase()
        return flight.flightNumber.uppercase().contains(q)
            || flight.departureCode.uppercase().contains(q)
            || flight.arrivalCode.uppercase().contains(q)
            || flight.notes.uppercase().contains(q)
            || flight.aircraftType.uppercase().contains(q)
    }

    private fun flightYear(flight: LogbookFlight): String =
        Instant.ofEpochMilli(flight.departureTimeUtc)
            .atZone(ZoneOffset.UTC)
            .year.toString()

    fun undoDelete() {
        val flight = _uiState.value.deletedFlight ?: return
        viewModelScope.launch {
            // Upsert to preserve the original ID (and any sourceCalendarEventId link)
            repository.upsert(flight)
            _uiState.update { it.copy(deletedFlight = null, snackbarMessage = null) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null, deletedFlight = null) }
    }
}
