package com.flightlog.app.ui.calendarflights

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel

// ── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFlightsScreen(
    viewModel: CalendarFlightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val permState by viewModel.permissionState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val upcomingFlights by viewModel.upcomingFlights.collectAsState()
    val pastFlights by viewModel.pastFlights.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

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

    // Detail bottom sheet
    if (uiState.showDetailSheet && uiState.selectedFlight != null) {
        FlightDetailBottomSheet(
            flight = uiState.selectedFlight!!,
            onDismiss = viewModel::dismissDetailSheet,
            onDismissFlight = { viewModel.dismissFlight(it.id) }
        )
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
        is PermissionState.Granted -> { /* Fall through to the main Scaffold */ }
    }

    // Main screen (permission granted)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calendar Flights")
                        val subtitle = uiState.syncSubtitle()
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.syncStatus == SyncStatus.FAILED)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    SyncIconButton(
                        isSyncing = uiState.isSyncing,
                        onClick = { viewModel.onRefresh(context.contentResolver) }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FlightTab.entries.forEachIndexed { index, tab ->
                    val tabFlights = flightsForTab(tab, upcomingFlights, pastFlights)
                    SegmentedButton(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FlightTab.entries.size
                        ),
                        label = {
                            Text(
                                "${tab.name.lowercase().replaceFirstChar { it.uppercase() }} (${tabFlights.size})"
                            )
                        }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.onRefresh(context.contentResolver) },
                modifier = Modifier.fillMaxSize()
            ) {
                Crossfade(
                    targetState = uiState.selectedTab,
                    label = "tab_crossfade"
                ) { tab ->
                    val tabFlights = flightsForTab(tab, upcomingFlights, pastFlights)
                    if (tabFlights.isEmpty()) {
                        EmptyState(
                            tab = tab,
                            onSyncClick = { viewModel.onRefresh(context.contentResolver) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = tabFlights,
                                key = { it.id }
                            ) { flight ->
                                FlightCard(
                                    flight = flight,
                                    onClick = { viewModel.selectFlight(flight) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Sync icon with rotation animation ──────────────────────────────────────────

@Composable
private fun SyncIconButton(
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    val rotation = if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_angle"
        )
        angle
    } else {
        0f
    }

    IconButton(onClick = onClick, enabled = !isSyncing) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Sync calendar",
            modifier = Modifier.rotate(rotation)
        )
    }
}

// ── Empty state with hint example ──────────────────────────────────────────────

@Composable
private fun EmptyState(
    tab: FlightTab,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FlightTakeoff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (tab) {
                    FlightTab.UPCOMING -> "No upcoming flights"
                    FlightTab.PAST -> "No past flights"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add events like \"Flight AA0011 ORD-CMH\" to your calendar and sync to see them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSyncClick) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now")
            }
        }
    }
}
