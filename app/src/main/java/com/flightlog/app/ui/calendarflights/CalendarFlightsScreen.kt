package com.flightlog.app.ui.calendarflights

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun CalendarFlightsScreen(
    onNavigateToAddFlight: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: CalendarFlightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val permState by viewModel.permissionState.collectAsState()
    val upcomingFlights by viewModel.upcomingFlights.collectAsState()
    val pastFlights by viewModel.pastFlights.collectAsState()
    val filteredFlights by viewModel.filteredFlights.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val activity = context as? Activity
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CALENDAR)
        } ?: false
        viewModel.onPermissionResult(granted, shouldShowRationale)
    }

    // Show sync messages via snackbar
    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSyncMessage()
        }
    }

    // Permission gate
    when (permState) {
        is PermissionState.NotRequested -> {
            PermissionFullScreen(
                icon = Icons.Default.CalendarMonth,
                title = "Calendar Access",
                message = "Flight Log reads your calendar to automatically detect and display your upcoming and past flights.",
                buttonText = "Grant Access",
                onButtonClick = {
                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            )
            return
        }
        is PermissionState.Denied -> {
            PermissionFullScreen(
                icon = Icons.Default.CalendarMonth,
                title = "Calendar access denied",
                message = "Without calendar access Flight Log can't sync your flights. Tap below to try again.",
                buttonText = "Try Again",
                onButtonClick = {
                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            )
            return
        }
        is PermissionState.PermanentlyDenied -> {
            PermissionFullScreen(
                icon = Icons.Default.CalendarMonth,
                title = "Permission required",
                message = "Calendar access was permanently denied. Please enable it in App Settings to sync your flights.",
                buttonText = "Open Settings",
                onButtonClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
            return
        }
        is PermissionState.Granted -> { /* Fall through to the map screen */ }
    }

    // All flights for the map (both upcoming + past, deduplicated)
    val allFlights = remember(upcomingFlights, pastFlights) {
        (upcomingFlights + pastFlights).distinctBy { it.id }
    }

    MapFlightsScreen(
        allFlights = allFlights,
        filteredFlights = filteredFlights,
        upcomingFlights = upcomingFlights,
        pastFlights = pastFlights,
        selectedFlight = uiState.selectedFlight,
        drawerAnchor = uiState.drawerAnchor,
        selectedTab = uiState.selectedTab,
        searchQuery = uiState.searchQuery,
        snackbarHostState = snackbarHostState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onTabChanged = viewModel::setTab,
        onFlightCardTapped = viewModel::onFlightCardTapped,
        onMapTapped = viewModel::onMapTapped,
        onDrawerAnchorChanged = viewModel::setDrawerAnchor,
        onDismissFlight = { flight -> viewModel.dismissFlight(flight.id) },
        onAddToLogbook = { viewModel.addToLogbook(it) },
        isAlreadyLogged = { viewModel.isAlreadyLogged(it) },
        onLogbookSuccess = {
            scope.launch {
                snackbarHostState.showSnackbar("Added to Logbook")
            }
        },
        onSyncClick = { viewModel.onRefresh(context.contentResolver) },
        onAddFlightClick = onNavigateToAddFlight,
        onSettingsClick = onNavigateToSettings
    )
}
