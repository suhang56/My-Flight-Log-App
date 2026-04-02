package com.flightlog.app.ui.calendarflights

import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.util.toRelativeTimeLabel

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
    val drawerAnchor: DrawerAnchor = DrawerAnchor.COLLAPSED,
    val searchQuery: String = ""
) {
    val isSyncing: Boolean get() = syncStatus == SyncStatus.SYNCING

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
