package com.flightlog.app.ui.flights

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.ui.components.FlightCard
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightsScreen(
    viewModel: FlightsViewModel = hiltViewModel(),
    onFlightClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val flights by viewModel.allFlights.collectAsState()
    val flightCount by viewModel.flightCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Calendar permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Request permission when needed
    LaunchedEffect(uiState.permissionRequired) {
        if (uiState.permissionRequired) {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    // Show sync messages via snackbar
    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    // Filter flights based on selected filter
    val now = Instant.now()
    val filteredFlights = when (uiState.selectedFilter) {
        FlightFilter.ALL -> flights
        FlightFilter.UPCOMING -> flights.filter { it.departureTime.isAfter(now) }
        FlightFilter.PAST -> flights.filter { !it.departureTime.isAfter(now) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FlightsTopBar(
                flightCount = flightCount,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.syncCalendar() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync calendar"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            FilterChipRow(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filteredFlights.isEmpty()) {
                EmptyState(
                    filter = uiState.selectedFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = filteredFlights,
                        key = { it.id }
                    ) { flight ->
                        FlightCard(
                            flight = flight,
                            onClick = { onFlightClick(flight.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightsTopBar(
    flightCount: Int,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = {
            Column {
                Text("My Flights")
                if (flightCount > 0) {
                    Text(
                        text = "$flightCount flight${if (flightCount != 1) "s" else ""} logged",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun FilterChipRow(
    selectedFilter: FlightFilter,
    onFilterSelected: (FlightFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlightFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            FlightFilter.ALL -> "All"
                            FlightFilter.UPCOMING -> "Upcoming"
                            FlightFilter.PAST -> "Past"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun EmptyState(
    filter: FlightFilter,
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
                text = when (filter) {
                    FlightFilter.ALL -> "No flights yet"
                    FlightFilter.UPCOMING -> "No upcoming flights"
                    FlightFilter.PAST -> "No past flights"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the sync button to import flights from your calendar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}
