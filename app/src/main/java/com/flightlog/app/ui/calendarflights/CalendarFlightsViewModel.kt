package com.flightlog.app.ui.calendarflights

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.data.repository.SyncResult
import com.flightlog.app.worker.CalendarSyncWorker
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// -- ViewModel --

@HiltViewModel
class CalendarFlightsViewModel @Inject constructor(
    private val application: Application,
    private val repository: CalendarRepository,
    private val logbookRepository: LogbookRepository
) : AndroidViewModel(application) {

    // -- Permission --

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)

    /** Observed by the screen to decide which UI surface to render. */
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    // -- Refreshing indicator --

    private val _isRefreshing = MutableStateFlow(false)

    /** Drives the PullToRefreshBox indicator directly. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // -- Flight lists --

    val upcomingFlights: StateFlow<List<CalendarFlight>> = repository.upcomingFlights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pastFlights: StateFlow<List<CalendarFlight>> = repository.pastFlights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -- General UI state --

    private val _uiState = MutableStateFlow(CalendarFlightsUiState())
    val uiState: StateFlow<CalendarFlightsUiState> = _uiState.asStateFlow()

    init {
        val hasPermission = ContextCompat.checkSelfPermission(
            application, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            _permissionState.value = PermissionState.Granted
            performSync(application.contentResolver)
        }
    }

    val visibleCount: StateFlow<Int> = repository.getVisibleCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val filteredFlights: StateFlow<List<CalendarFlight>> =
        combine(
            upcomingFlights,
            pastFlights,
            _uiState.map { it.searchQuery }
        ) { upcoming, past, query ->
            val all = (upcoming + past).distinctBy { it.id }
            if (query.isBlank()) all
            else all.filter { flight ->
                flight.flightNumber.contains(query, ignoreCase = true) ||
                    flight.departureCode.contains(query, ignoreCase = true) ||
                    flight.arrivalCode.contains(query, ignoreCase = true) ||
                    flight.rawTitle.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -- Public API --

    fun setTab(tab: FlightTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Called after the system permission dialog completes.
     *
     * @param granted             Whether READ_CALENDAR was granted.
     * @param shouldShowRationale [ActivityCompat.shouldShowRequestPermissionRationale] captured
     *                            immediately after the dialog result, before Activity resumes.
     */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        val newState: PermissionState = when {
            granted              -> PermissionState.Granted
            !shouldShowRationale -> PermissionState.PermanentlyDenied
            else                 -> PermissionState.Denied
        }
        _permissionState.value = newState

        if (granted) {
            CalendarSyncWorker.enqueuePeriodicSync(application)
            performSync(application.contentResolver)
        }
    }

    /**
     * Manual pull-to-refresh or "Try Again" trigger from the UI.
     * Silently ignored when permission is not yet granted.
     */
    fun onRefresh(contentResolver: ContentResolver = application.contentResolver) {
        if (_permissionState.value !is PermissionState.Granted) return
        performSync(contentResolver)
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun selectFlight(flight: CalendarFlight) {
        _uiState.update {
            it.copy(
                selectedFlight = flight,
                drawerAnchor = DrawerAnchor.HALF_EXPANDED
            )
        }
    }

    fun onFlightCardTapped(flight: CalendarFlight) {
        selectFlight(flight)
    }

    fun onMapTapped() {
        _uiState.update {
            it.copy(selectedFlight = null, drawerAnchor = DrawerAnchor.COLLAPSED)
        }
    }

    fun setDrawerAnchor(anchor: DrawerAnchor) {
        _uiState.update { state ->
            if (anchor == DrawerAnchor.COLLAPSED) {
                state.copy(drawerAnchor = anchor, selectedFlight = null)
            } else {
                state.copy(drawerAnchor = anchor)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun dismissDetailSheet() {
        _uiState.update { it.copy(drawerAnchor = DrawerAnchor.COLLAPSED, selectedFlight = null) }
    }

    fun dismissFlight(id: Long) {
        viewModelScope.launch {
            repository.dismiss(id)
            _uiState.update {
                it.copy(drawerAnchor = DrawerAnchor.COLLAPSED, selectedFlight = null)
            }
        }
    }

    // -- Logbook integration --

    suspend fun addToLogbook(calendarFlight: CalendarFlight): Boolean {
        return try {
            logbookRepository.addFromCalendarFlight(calendarFlight)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isAlreadyLogged(calendarEventId: Long): Boolean {
        return logbookRepository.isAlreadyLogged(calendarEventId)
    }

    // -- Private helpers --

    private fun performSync(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, syncMessage = null) }

            when (val result = withContext(Dispatchers.IO) { repository.syncFromCalendar(contentResolver) }) {
                is SyncResult.Success -> {
                    // Auto-log new synced flights to logbook
                    val autoLoggedCount = run {
                        var count = 0
                        val upcoming = repository.upcomingFlights().first()
                        val past = repository.pastFlights().first()
                        val allFlights = (upcoming + past).distinctBy { it.id }
                        for (flight in allFlights) {
                            if (!logbookRepository.isAlreadyLogged(flight.calendarEventId)) {
                                try {
                                    logbookRepository.addFromCalendarFlight(flight)
                                    count++
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                }
                            }
                        }
                        count
                    }

                    val msg = when {
                        result.syncedCount == 0 && result.removedCount == 0 && autoLoggedCount == 0 -> "No flights found"
                        result.removedCount > 0 ->
                            "Synced ${result.syncedCount} flights, removed ${result.removedCount}, logged $autoLoggedCount"
                        autoLoggedCount > 0 ->
                            "Synced ${result.syncedCount} flights, logged $autoLoggedCount to logbook"
                        else -> "Synced ${result.syncedCount} flights"
                    }
                    _uiState.update {
                        it.copy(
                            syncStatus         = SyncStatus.IDLE,
                            syncMessage        = msg,
                            lastSyncedAtMillis = System.currentTimeMillis()
                        )
                    }
                }
                is SyncResult.Error -> {
                    // If a SecurityException escapes sync the permission was revoked mid-session.
                    if (result.cause is SecurityException) {
                        _permissionState.value = PermissionState.Denied
                    }
                    _uiState.update {
                        it.copy(syncStatus = SyncStatus.FAILED, syncMessage = result.message)
                    }
                }
            }

            _isRefreshing.value = false
        }
    }
}
