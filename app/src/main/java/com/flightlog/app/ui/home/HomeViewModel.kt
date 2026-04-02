package com.flightlog.app.ui.home

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.data.repository.SyncResult
import com.flightlog.app.ui.calendarflights.PermissionState
import com.flightlog.app.worker.CalendarSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val calendarRepository: CalendarRepository,
    private val logbookRepository: LogbookRepository
) : AndroidViewModel(application) {

    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(FlightTab.UPCOMING)
    private val _isSearchExpanded = MutableStateFlow(false)

    // Stable Flow references: each DAO @Query returns a new Flow per call,
    // but these are captured once at property init time.
    private val calendarFlow = calendarRepository.getAllVisible()
    private val logbookFlow = logbookRepository.getAll()

    val uiState: StateFlow<HomeUiState> = combine(
        calendarFlow,
        logbookFlow,
        _permissionState,
        _isRefreshing,
        _searchQuery
    ) { calendarFlights, logbookFlights, permState, refreshing, query ->
        val merged = UnifiedFlightItem.merge(calendarFlights, logbookFlights)
        val now = clock()

        val filtered = if (query.isBlank()) merged else {
            val q = query.trim().lowercase()
            merged.filter { item ->
                item.flightNumber.lowercase().contains(q) ||
                    item.departureCode.lowercase().contains(q) ||
                    item.arrivalCode.lowercase().contains(q)
            }
        }

        val upcoming = filtered.filter { it.sortKey >= now }.sortedBy { it.sortKey }
        val past = filtered.filter { it.sortKey < now }.sortedByDescending { it.sortKey }

        HomeUiState(
            upcomingItems = upcoming,
            pastItems = past,
            isRefreshing = refreshing,
            permissionState = permState,
            searchQuery = query,
            routeSegments = HomeUiState.computeRouteSegments(upcoming, past)
        )
    }.combine(_selectedTab) { state, tab ->
        state.copy(selectedTab = tab)
    }.combine(_isSearchExpanded) { state, expanded ->
        state.copy(isSearchExpanded = expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            _permissionState.value = PermissionState.Granted
            performSync(application.contentResolver)
        }
    }

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        val newState: PermissionState = when {
            granted -> PermissionState.Granted
            !shouldShowRationale -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
        _permissionState.value = newState

        if (granted) {
            CalendarSyncWorker.enqueuePeriodicSync(application)
            performSync(application.contentResolver)
        }
    }

    fun onRefresh(contentResolver: ContentResolver = application.contentResolver) {
        if (_permissionState.value !is PermissionState.Granted) return
        performSync(contentResolver)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tab: FlightTab) {
        _selectedTab.value = tab
    }

    fun toggleSearch() {
        _isSearchExpanded.value = !_isSearchExpanded.value
        if (!_isSearchExpanded.value) {
            _searchQuery.value = ""
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private fun performSync(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isRefreshing.value = true

            when (val result = withContext(Dispatchers.IO) { calendarRepository.syncFromCalendar(contentResolver) }) {
                is SyncResult.Success -> {
                    val msg = when {
                        result.syncedCount == 0 && result.removedCount == 0 -> "No flights found"
                        result.removedCount > 0 ->
                            "Synced ${result.syncedCount} flights, removed ${result.removedCount}"
                        else -> "Synced ${result.syncedCount} flights"
                    }
                    _syncMessage.value = msg
                }
                is SyncResult.Error -> {
                    if (result.cause is SecurityException) {
                        _permissionState.value = PermissionState.Denied
                    }
                    _syncMessage.value = result.message
                }
            }

            _isRefreshing.value = false
        }
    }
}
