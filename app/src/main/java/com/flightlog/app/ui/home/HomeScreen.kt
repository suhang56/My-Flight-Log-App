package com.flightlog.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.ui.calendarflights.PermissionState
import com.flightlog.app.util.DATE_FORMATTER
import com.flightlog.app.util.TIME_TZ_FORMATTER
import com.flightlog.app.util.formatInZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddFlight: () -> Unit = {},
    onViewLogbookFlight: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
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

    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            viewModel.clearSyncMessage()
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Handle permission states that need full-screen UI
    when (uiState.permissionState) {
        is PermissionState.NotRequested -> {
            PermissionPromptScreen(
                title = "Calendar Access",
                message = "Flight Log reads event titles like 'NH847 HND-LHR' to automatically find your flights. Your calendar data never leaves your device.",
                buttonText = "Grant Access",
                onButtonClick = {
                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            )
            return
        }
        is PermissionState.Denied -> {
            PermissionPromptScreen(
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
            PermissionPromptScreen(
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
        is PermissionState.Granted -> { /* Continue to main UI */ }
    }

    // Build route data for the map
    val routeDataList = remember(uiState.upcomingItems, uiState.pastItems) {
        val nextRoute = uiState.nextUpcomingRoute
        uiState.allRoutes.distinct().map { (dep, arr) ->
            RouteData(
                departureCode = dep,
                arrivalCode = arr,
                isHighlighted = nextRoute != null && dep == nextRoute.first && arr == nextRoute.second
            )
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen map background
        AllRoutesMapCanvas(
            routes = routeDataList,
            modifier = Modifier.fillMaxSize()
        )

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 160.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            sheetContent = {
                SheetContent(
                    uiState = uiState,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onViewLogbookFlight = onViewLogbookFlight
                )
            }
        ) {
            // No scaffold body content needed - map fills background
        }

        // FAB for adding flights
        FloatingActionButton(
            onClick = onAddFlight,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 176.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add flight")
        }
    }
}

@Composable
private fun SheetContent(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onViewLogbookFlight: (Long) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search flights...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        val hasItems = uiState.upcomingItems.isNotEmpty() || uiState.pastItems.isNotEmpty()

        if (!hasItems) {
            EmptyHomeState(modifier = Modifier.fillMaxWidth().padding(32.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Upcoming section
                if (uiState.upcomingItems.isNotEmpty()) {
                    item(key = "header_upcoming") {
                        SectionHeader(
                            title = "Upcoming",
                            count = uiState.upcomingItems.size
                        )
                    }
                    items(
                        items = uiState.upcomingItems,
                        key = { "upcoming_${it.itemKey}" }
                    ) { item ->
                        UnifiedFlightCard(
                            item = item,
                            onClick = {
                                if (item is UnifiedFlightItem.FromLogbook) {
                                    onViewLogbookFlight(item.flight.id)
                                }
                            }
                        )
                    }
                }

                // Past section
                if (uiState.pastItems.isNotEmpty()) {
                    item(key = "header_past") {
                        SectionHeader(
                            title = "Past",
                            count = uiState.pastItems.size
                        )
                    }
                    items(
                        items = uiState.pastItems,
                        key = { "past_${it.itemKey}" }
                    ) { item ->
                        UnifiedFlightCard(
                            item = item,
                            onClick = {
                                if (item is UnifiedFlightItem.FromLogbook) {
                                    onViewLogbookFlight(item.flight.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Stable key for LazyColumn items. */
private val UnifiedFlightItem.itemKey: String
    get() = when (this) {
        is UnifiedFlightItem.FromCalendar -> "cal_${flight.id}"
        is UnifiedFlightItem.FromLogbook -> "log_${flight.id}"
    }

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun UnifiedFlightCard(
    item: UnifiedFlightItem,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (item.flightNumber.isNotBlank()) {
                    Text(
                        text = item.flightNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val routeText = if (item.departureCode.isBlank() && item.arrivalCode.isBlank()) {
                    "Route pending"
                } else {
                    "${item.departureCode}  \u2192  ${item.arrivalCode}"
                }
                Text(
                    text = routeText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.departureCode.isBlank() && item.arrivalCode.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        Color.Unspecified
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date line
                Text(
                    text = formatInZone(item.sortKey, item.departureTimezone, DATE_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Time line
                val depTime = formatInZone(item.sortKey, item.departureTimezone, TIME_TZ_FORMATTER)
                val arrTime = when (item) {
                    is UnifiedFlightItem.FromLogbook -> item.flight.arrivalTimeUtc?.let {
                        formatInZone(it, item.flight.arrivalTimezone, TIME_TZ_FORMATTER)
                    }
                    is UnifiedFlightItem.FromCalendar -> item.flight.endTime?.let {
                        formatInZone(it, item.flight.arrivalTimezone, TIME_TZ_FORMATTER)
                    }
                }
                Text(
                    text = if (arrTime != null) "$depTime \u2192 $arrTime" else depTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Source indicator
            val sourceLabel = when (item) {
                is UnifiedFlightItem.FromLogbook -> "Logbook"
                is UnifiedFlightItem.FromCalendar -> "Calendar"
            }
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHomeState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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
            text = "No flights yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your calendar and logbook flights will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionPromptScreen(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onButtonClick) {
                Text(buttonText)
            }
        }
    }
}
