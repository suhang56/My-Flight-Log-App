package com.flightlog.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onNavigateToSettings: () -> Unit = {},
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
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSyncMessage()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            confirmValueChange = { true }
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen map background (always renders, even with no routes)
        AllRoutesMapCanvas(
            routes = uiState.routeSegments,
            modifier = Modifier.fillMaxSize()
        )

        // Settings icon top-right
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 140.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetDragHandle = { DragHandlePill() },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            sheetContent = {
                SheetContent(
                    uiState = uiState,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onViewLogbookFlight = onViewLogbookFlight,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        ) {
            // No scaffold body -- map fills background via Box
        }

        // FAB for adding flights
        FloatingActionButton(
            onClick = onAddFlight,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 156.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add flight")
        }
    }
}

// 32x4dp drag handle pill
@Composable
private fun DragHandlePill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun SheetContent(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onViewLogbookFlight: (Long) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Inline permission prompt (inside drawer, not full-screen blocker)
        when (uiState.permissionState) {
            is PermissionState.NotRequested -> {
                InlinePermissionBanner(
                    message = "Grant calendar access to auto-sync flights",
                    buttonText = "Grant Access",
                    onClick = onRequestPermission
                )
            }
            is PermissionState.Denied -> {
                InlinePermissionBanner(
                    message = "Calendar access denied. Tap to try again.",
                    buttonText = "Try Again",
                    onClick = onRequestPermission
                )
            }
            is PermissionState.PermanentlyDenied -> {
                InlinePermissionBanner(
                    message = "Calendar access permanently denied.",
                    buttonText = "Open Settings",
                    onClick = onOpenSettings
                )
            }
            is PermissionState.Granted -> { /* no banner */ }
        }

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
                if (uiState.upcomingItems.isNotEmpty()) {
                    item(key = "header_upcoming") {
                        SectionHeader(title = "Upcoming", count = uiState.upcomingItems.size)
                    }
                    items(
                        items = uiState.upcomingItems,
                        key = { "upcoming_${it.itemKey}" }
                    ) { item ->
                        UnifiedFlightCard(
                            item = item,
                            onClick = when (item) {
                                is UnifiedFlightItem.FromLogbook -> ({ onViewLogbookFlight(item.flight.id) })
                                is UnifiedFlightItem.FromCalendar -> null
                            }
                        )
                    }
                }

                if (uiState.pastItems.isNotEmpty()) {
                    item(key = "header_past") {
                        SectionHeader(title = "Past", count = uiState.pastItems.size)
                    }
                    items(
                        items = uiState.pastItems,
                        key = { "past_${it.itemKey}" }
                    ) { item ->
                        UnifiedFlightCard(
                            item = item,
                            onClick = when (item) {
                                is UnifiedFlightItem.FromLogbook -> ({ onViewLogbookFlight(item.flight.id) })
                                is UnifiedFlightItem.FromCalendar -> null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlinePermissionBanner(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(buttonText)
        }
    }
}

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
    onClick: (() -> Unit)?
) {
    ElevatedCard(
        onClick = onClick ?: {},
        enabled = onClick != null,
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

                Text(
                    text = formatInZone(item.sortKey, item.departureTimezone, DATE_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
