package com.flightlog.app.ui.calendarflights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.ui.logbook.AircraftPhotoState

@Composable
internal fun MapFlightsScreen(
    allFlights: List<CalendarFlight>,
    filteredFlights: List<CalendarFlight>,
    upcomingFlights: List<CalendarFlight>,
    pastFlights: List<CalendarFlight>,
    selectedFlight: CalendarFlight?,
    drawerAnchor: DrawerAnchor,
    selectedTab: FlightTab,
    searchQuery: String,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChanged: (String) -> Unit,
    onTabChanged: (FlightTab) -> Unit,
    onFlightCardTapped: (CalendarFlight) -> Unit,
    onMapTapped: () -> Unit,
    onDrawerAnchorChanged: (DrawerAnchor) -> Unit,
    onDismissFlight: (CalendarFlight) -> Unit,
    onAddToLogbook: suspend (CalendarFlight) -> Boolean,
    isAlreadyLogged: suspend (Long) -> Boolean,
    onLogbookSuccess: () -> Unit,
    onSyncClick: () -> Unit,
    getLinkedLogbookFlight: suspend (Long) -> LogbookFlight?,
    onRatingChanged: (Long, Int?) -> Unit,
    aircraftPhotoState: AircraftPhotoState = AircraftPhotoState(),
    onAircraftTypeResolved: (aircraftType: String?, registration: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Map (full screen behind everything)
        FlightMapView(
            allFlights = allFlights,
            selectedFlight = selectedFlight,
            onMapTapped = onMapTapped,
            modifier = Modifier.fillMaxSize()
        )

        // Snackbar host (above drawer)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 248.dp)
        )

        // Bottom drawer
        FlightBottomDrawer(
            filteredFlights = filteredFlights,
            upcomingFlights = upcomingFlights,
            pastFlights = pastFlights,
            selectedFlight = selectedFlight,
            drawerAnchor = drawerAnchor,
            selectedTab = selectedTab,
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onTabChanged = onTabChanged,
            onFlightCardTapped = onFlightCardTapped,
            onDrawerAnchorChanged = onDrawerAnchorChanged,
            onDismissFlight = onDismissFlight,
            onAddToLogbook = onAddToLogbook,
            isAlreadyLogged = isAlreadyLogged,
            onLogbookSuccess = onLogbookSuccess,
            onSyncClick = onSyncClick,
            getLinkedLogbookFlight = getLinkedLogbookFlight,
            onRatingChanged = onRatingChanged,
            aircraftPhotoState = aircraftPhotoState,
            onAircraftTypeResolved = onAircraftTypeResolved
        )
    }
}
