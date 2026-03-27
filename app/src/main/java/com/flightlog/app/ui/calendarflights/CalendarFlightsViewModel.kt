package com.flightlog.app.ui.calendarflights

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.SyncResult
import com.flightlog.app.util.toRelativeTimeLabel
import com.flightlog.app.worker.CalendarSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// -- Permission state --

/**
 * Sealed class representing the current READ_CALENDAR permission lifecycle state.
 * Drives which UI surface is displayed on the calendar flights screen.
 */
sealed class PermissionState {
    /** Initial state — show the rationale card with "Grant Access" button. */
    object NotRequested : PermissionState()

    /** Permission was granted; show the tabs + flight list. */
    object Granted : PermissionState()

    /** User declined once; rationale can be re-shown before asking again. */
    object Denied : PermissionState()

    /**
     * User ticked "Don't ask again"; only the system Settings can unlock this.
     * Show the "Open Settings" button.
     */
    object PermanentlyDenied : PermissionState()
}

// -- Supporting types --

enum class FlightTab { UPCOMING, PAST }

enum class SyncStatus { NEVER_SYNCED, IDLE, SYNCING, FAILED }

data class CalendarFlightsUiState(
    val syncStatus: SyncStatus   = SyncStatus.NEVER_SYNCED,
    val syncMessage: String?     = null,
    val lastSyncedAtMillis: Long? = null,
    val selectedTab: FlightTab   = FlightTab.UPCOMING,
    val selectedFlight: CalendarFlight? = null,
    val showDetailSheet: Boolean = false
) {
    /** Convenience for the PullToRefreshBox [isRefreshing] parameter. */
    val isSyncing: Boolean get() = syncStatus == SyncStatus.SYNCING

    /** Human-readable subtitle describing the last sync time. */
    fun syncSubtitle(): String = when (syncStatus) {
        SyncStatus.NEVER_SYNCED -> "Never synced"
        SyncStatus.SYNCING      -> "Syncing..."
        SyncStatus.FAILED       -> "Sync failed -- tap to retry"
        SyncStatus.IDLE         -> {
            val millis = lastSyncedAtMillis ?: return "Never synced"
            "Last synced: ${millis.toRelativeTimeLabel()}"
        }
    }
}

// -- ViewModel --

@HiltViewModel
class CalendarFlightsViewModel @Inject constructor(
    private val application: Application,
    private val repository: CalendarRepository
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

    val visibleCount: StateFlow<Int> = repository.getVisibleCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
        _uiState.update { it.copy(selectedFlight = flight, showDetailSheet = true) }
    }

    fun dismissDetailSheet() {
        _uiState.update { it.copy(showDetailSheet = false, selectedFlight = null) }
    }

    fun dismissFlight(id: Long) {
        viewModelScope.launch {
            repository.dismiss(id)
            _uiState.update { it.copy(showDetailSheet = false, selectedFlight = null) }
        }
    }

    // -- Private helpers --

    private fun performSync(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, syncMessage = null) }

            when (val result = withContext(Dispatchers.IO) { repository.syncFromCalendar(contentResolver) }) {
                is SyncResult.Success -> {
                    val msg = when {
                        result.syncedCount == 0 && result.removedCount == 0 -> "No flights found"
                        result.removedCount > 0 ->
                            "Synced ${result.syncedCount} flights, removed ${result.removedCount}"
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
