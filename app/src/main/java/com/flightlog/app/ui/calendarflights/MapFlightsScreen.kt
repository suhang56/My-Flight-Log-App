package com.flightlog.app.ui.calendarflights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight

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
    onAddFlightClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // FAB and settings gear fade based on drawer state
    val showOverlays = drawerAnchor != DrawerAnchor.FULL_EXPANDED
    val overlayAlpha: Float = animateFloatAsState(
        targetValue = if (showOverlays) 1f else 0f,
        label = "overlay_alpha"
    ).value

    Box(modifier = modifier.fillMaxSize()) {
        // Map (full screen behind everything)
        FlightMapView(
            allFlights = allFlights,
            selectedFlight = selectedFlight,
            onMapTapped = onMapTapped,
            modifier = Modifier.fillMaxSize()
        )

        // Settings gear (top-end)
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, end = 16.dp)
                .alpha(overlayAlpha)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        // Snackbar host (above drawer)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 248.dp)
        )

        // Add FAB (above drawer peek)
        SmallFloatingActionButton(
            onClick = onAddFlightClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 256.dp)
                .alpha(overlayAlpha)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add flight"
            )
        }

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
            onSyncClick = onSyncClick
        )
    }
}
