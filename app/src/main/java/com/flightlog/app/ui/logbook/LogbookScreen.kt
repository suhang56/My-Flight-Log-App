package com.flightlog.app.ui.logbook

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.trips.TripGroup
import com.flightlog.app.util.DATE_FORMATTER
import com.flightlog.app.util.formatInZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    onAddFlight: () -> Unit = {},
    onEditFlight: (Long) -> Unit = {},
    onViewFlight: (Long) -> Unit = {},
    viewModel: LogbookViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel()
) {
    val flights by viewModel.flights.collectAsState()
    val flightCount by viewModel.flightCount.collectAsState()
    val totalDistanceNm by viewModel.totalDistanceNm.collectAsState()
    val totalFlightCount by viewModel.totalFlightCount.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val availableSeatClasses by viewModel.availableSeatClasses.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val exportState by exportViewModel.exportState.collectAsState()
    val tripViewEnabled by viewModel.tripViewEnabled.collectAsState()
    val tripGroups by viewModel.tripGroups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val activityContext = LocalContext.current
    val showTripView = tripViewEnabled && !filterState.isActive

    // Undo-delete snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (uiState.deletedFlight != null) "Undo" else null,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    // Handle export state: share on Ready, snackbar on Error
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Ready -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = state.mimeType
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activityContext.startActivity(Intent.createChooser(intent, "Share flight log"))
                exportViewModel.clearExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                exportViewModel.clearExportState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logbook") },
                actions = {
                    if (filterState.isActive) {
                        TextButton(onClick = { viewModel.clearFilters() }) {
                            Text("Clear")
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LogbookSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.displayName,
                                            fontWeight = if (order == filterState.sortOrder)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (order == filterState.sortOrder) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                    // Export overflow menu
                    Box {
                        if (exportState is ExportState.Loading) {
                            IconButton(onClick = {}, enabled = false) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as CSV") },
                                onClick = {
                                    showExportMenu = false
                                    exportViewModel.exportCsv()
                                },
                                enabled = totalFlightCount > 0
                            )
                            DropdownMenuItem(
                                text = { Text("Export as JSON") },
                                onClick = {
                                    showExportMenu = false
                                    exportViewModel.exportJson()
                                },
                                enabled = totalFlightCount > 0
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFlight) {
                Icon(Icons.Default.Add, contentDescription = "Add flight")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar — fixed above list
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search flights...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filter chips row
            if (availableYears.isNotEmpty() || availableSeatClasses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableYears.forEach { year ->
                        FilterChip(
                            selected = filterState.selectedYear == year,
                            onClick = { viewModel.toggleYearFilter(year) },
                            label = { Text(year) },
                            leadingIcon = if (filterState.selectedYear == year) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                    availableSeatClasses.forEach { seatClass ->
                        FilterChip(
                            selected = filterState.selectedSeatClass == seatClass,
                            onClick = { viewModel.toggleSeatClassFilter(seatClass) },
                            label = { Text(seatClass) },
                            leadingIcon = if (filterState.selectedSeatClass == seatClass) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // View toggle: List vs Trip
            ViewToggleRow(
                tripViewEnabled = tripViewEnabled,
                filterActive = filterState.isActive,
                onToggle = { viewModel.toggleTripView() }
            )

            // Content area
            when {
                flights.isEmpty() && !filterState.isActive -> {
                    LogbookEmptyState(
                        onAddFlight = onAddFlight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                flights.isEmpty() && filterState.isActive -> {
                    NoResultsState(
                        onClearFilters = { viewModel.clearFilters() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "stats") {
                            StatsRow(
                                flightCount = flightCount,
                                totalDistanceNm = totalDistanceNm,
                                isFiltered = filterState.isActive
                            )
                        }

                        if (showTripView) {
                            tripGroups.forEach { trip ->
                                item(key = "trip_${trip.id}") {
                                    TripHeader(
                                        trip = trip,
                                        onToggle = { viewModel.toggleTripExpanded(trip.id) }
                                    )
                                }
                                if (trip.isExpanded) {
                                    items(
                                        items = trip.legs,
                                        key = { "leg_${it.id}" }
                                    ) { flight ->
                                        LogbookCard(
                                            flight = flight,
                                            onClick = { onViewFlight(flight.id) },
                                            indented = true
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = flights,
                                key = { it.id }
                            ) { flight ->
                                LogbookCard(
                                    flight = flight,
                                    onClick = { onViewFlight(flight.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Stats row ───────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    flightCount: Int,
    totalDistanceNm: Int,
    isFiltered: Boolean = false
) {
    val suffix = if (isFiltered) " (filtered)" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            icon = Icons.Default.FlightTakeoff,
            value = "$flightCount",
            label = (if (flightCount == 1) "Flight" else "Flights") + suffix
        )
        StatItem(
            icon = Icons.Default.Route,
            value = "%,d".format(totalDistanceNm),
            label = "NM Total$suffix"
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Logbook card ────────────────────────────────────────────────────────────────

@Composable
private fun LogbookCard(
    flight: LogbookFlight,
    onClick: () -> Unit,
    indented: Boolean = false
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (indented) Modifier.padding(start = 16.dp) else Modifier),
        elevation = if (indented) CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                    else CardDefaults.elevatedCardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (flight.flightNumber.isNotBlank()) {
                    Text(
                        text = flight.flightNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = "${flight.departureCode}  \u2192  ${flight.arrivalCode}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatInZone(flight.departureTimeUtc, flight.departureTimezone, DATE_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            flight.distanceNm?.let { nm ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%,d".format(nm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "NM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── No results state ────────────────────────────────────────────────────────────

@Composable
private fun NoResultsState(
    onClearFilters: () -> Unit,
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
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No flights match your search",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try adjusting your search or filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onClearFilters) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Filters")
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────────

@Composable
private fun LogbookEmptyState(
    onAddFlight: () -> Unit = {},
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
                imageVector = Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No flights logged",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap \"Add to Logbook\" on any calendar flight, or use the + button to add one manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddFlight) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add your first flight")
            }
        }
    }
}

// ── View toggle (List / Trip) ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewToggleRow(
    tripViewEnabled: Boolean,
    filterActive: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (filterActive) Modifier.alpha(0.38f) else Modifier)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = !tripViewEnabled,
                onClick = { if (!filterActive && tripViewEnabled) onToggle() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = !filterActive,
                icon = { Icon(Icons.Default.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("List")
            }
            SegmentedButton(
                selected = tripViewEnabled,
                onClick = { if (!filterActive && !tripViewEnabled) onToggle() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = !filterActive,
                icon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("Trips")
            }
        }

        if (filterActive && tripViewEnabled) {
            Text(
                text = "Clear filters to see trip view",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
