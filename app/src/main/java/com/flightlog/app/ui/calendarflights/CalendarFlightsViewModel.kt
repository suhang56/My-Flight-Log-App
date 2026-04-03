package com.flightlog.app.ui.calendarflights

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.AircraftTypePhotoProvider
import com.flightlog.app.data.network.PlanespottersApi
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.ui.logbook.AircraftPhotoState
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
    private val logbookRepository: LogbookRepository,
    private val planespottersApi: PlanespottersApi
) : AndroidViewModel(application) {

    // -- Permission --

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)

    /** Observed by the screen to decide which UI surface to render. */
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    // -- Refreshing indicator --

    private val _isRefreshing = MutableStateFlow(false)

    /** Drives the PullToRefreshBox indicator directly. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // -- Flight lists (merged: calendar + logbook-only flights) --

    val upcomingFlights: StateFlow<List<CalendarFlight>> = combine(
        repository.upcomingFlights(),
        logbookRepository.allFlights
    ) { calFlights, logFlights ->
        mergeFlights(calFlights, logFlights) { it.scheduledTime >= System.currentTimeMillis() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pastFlights: StateFlow<List<CalendarFlight>> = combine(
        repository.pastFlights(),
        logbookRepository.allFlights
    ) { calFlights, logFlights ->
        mergeFlights(calFlights, logFlights) { it.scheduledTime < System.currentTimeMillis() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // -- Aircraft photo for drawer --

    private val _aircraftPhotoState = MutableStateFlow(AircraftPhotoState())
    val aircraftPhotoState: StateFlow<AircraftPhotoState> = _aircraftPhotoState.asStateFlow()

    /**
     * Looks up an aircraft photo. Prefers Planespotters API by registration for real photos;
     * falls back to static AircraftTypePhotoProvider when registration is unavailable.
     */
    fun fetchAircraftPhoto(aircraftType: String?, registration: String? = null) {
        if (aircraftType.isNullOrBlank() && registration.isNullOrBlank()) {
            _aircraftPhotoState.value = AircraftPhotoState()
            return
        }

        if (!registration.isNullOrBlank()) {
            _aircraftPhotoState.value = AircraftPhotoState(isLoading = true)
            viewModelScope.launch {
                try {
                    val response = planespottersApi.getPhotosByRegistration(registration)
                    val photo = response.body()?.photos?.firstOrNull()
                    if (photo?.thumbnailLarge?.src != null) {
                        _aircraftPhotoState.value = AircraftPhotoState(
                            photoUrl = photo.thumbnailLarge.src,
                            photographer = photo.photographer
                        )
                    } else {
                        fallbackToTypePhoto(aircraftType)
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fallbackToTypePhoto(aircraftType)
                }
            }
        } else {
            fallbackToTypePhoto(aircraftType)
        }
    }

    private fun fallbackToTypePhoto(aircraftType: String?) {
        val photoInfo = if (!aircraftType.isNullOrBlank()) {
            AircraftTypePhotoProvider.getPhotoForType(aircraftType)
        } else null
        _aircraftPhotoState.value = AircraftPhotoState(
            photoUrl = photoInfo?.photoUrl,
            photographer = photoInfo?.photographer
        )
    }

    /**
     * Resets the aircraft photo state — called when the drawer is dismissed or a different flight is selected.
     */
    fun clearAircraftPhoto() {
        _aircraftPhotoState.value = AircraftPhotoState()
    }

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
            // Synthetic flights (from logbook, negative IDs) can't be dismissed via CalendarRepository
            if (id >= 0) {
                repository.dismiss(id)
            }
            _uiState.update {
                it.copy(drawerAnchor = DrawerAnchor.COLLAPSED, selectedFlight = null)
            }
        }
    }

    // -- Logbook integration --

    suspend fun addToLogbook(calendarFlight: CalendarFlight): Boolean {
        // Synthetic flights (from logbook) already exist — nothing to add
        if (calendarFlight.id < 0) return true
        return try {
            logbookRepository.addFromCalendarFlight(calendarFlight)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    suspend fun isAlreadyLogged(calendarEventId: Long): Boolean {
        // Synthetic flights (from logbook) are always already logged
        if (calendarEventId < 0) return true
        return logbookRepository.isAlreadyLogged(calendarEventId)
    }

    /**
     * Returns the linked LogbookFlight for a given calendar event ID, or null if not logged.
     * Synthetic flights (negative IDs) resolve via the original logbook ID.
     */
    suspend fun getLinkedLogbookFlight(calendarEventId: Long): LogbookFlight? {
        if (calendarEventId < 0) {
            // Synthetic flight: reverse the ID mapping from toSyntheticCalendarFlight()
            val logbookId = -(calendarEventId + 1_000_000)
            return logbookRepository.getById(logbookId)
        }
        return logbookRepository.findByCalendarEventId(calendarEventId)
    }

    /**
     * Updates the rating on a linked LogbookFlight.
     */
    fun updateLinkedRating(logbookFlightId: Long, rating: Int?) {
        viewModelScope.launch {
            logbookRepository.setRating(logbookFlightId, rating)
        }
    }

    // -- Private helpers --

    /**
     * Merges calendar flights with logbook-only flights (those not already linked to a calendar event).
     * Logbook flights are converted to synthetic CalendarFlight objects for display.
     * [timeFilter] selects upcoming vs past based on the synthetic scheduledTime.
     */
    private fun mergeFlights(
        calendarFlights: List<CalendarFlight>,
        logbookFlights: List<LogbookFlight>,
        timeFilter: (CalendarFlight) -> Boolean
    ): List<CalendarFlight> {
        val linkedCalendarEventIds = calendarFlights.map { it.calendarEventId }.toSet()

        val logbookOnly = logbookFlights
            .filter { lb -> lb.sourceCalendarEventId == null || lb.sourceCalendarEventId !in linkedCalendarEventIds }
            .map { it.toSyntheticCalendarFlight() }
            .filter(timeFilter)

        return (calendarFlights + logbookOnly).distinctBy { it.id }
    }

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

/**
 * Converts a [LogbookFlight] into a synthetic [CalendarFlight] for display on the home map/drawer.
 * Uses negative ID space to avoid collisions with real CalendarFlight rows.
 */
private fun LogbookFlight.toSyntheticCalendarFlight(): CalendarFlight = CalendarFlight(
    id = -id,  // negative to avoid collision with real CalendarFlight IDs
    calendarEventId = -(id + 1_000_000),  // won't match any real calendar event
    flightNumber = flightNumber,
    departureCode = departureCode,
    arrivalCode = arrivalCode,
    rawTitle = if (flightNumber.isNotBlank()) "$flightNumber $departureCode-$arrivalCode"
               else "$departureCode-$arrivalCode",
    scheduledTime = departureTimeMillis,
    endTime = arrivalTimeMillis,
    departureTimezone = departureTimezone,
    arrivalTimezone = arrivalTimezone,
    isManuallyDismissed = false
)
