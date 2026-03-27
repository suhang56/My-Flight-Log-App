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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.util.toRelativeTimeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Badge colors (exact spec values with light/dark variants) ──────────────────

private object BadgeColors {
    // Light mode
    val todayBgLight = Color(0xFF1565C0)
    val todayTextLight = Color.White
    val upcomingBgLight = Color(0xFF2E7D32)
    val upcomingTextLight = Color.White
    val pastBgLight = Color(0xFFE0E0E0)
    val pastTextLight = Color(0xFF616161)

    // Dark mode
    val todayBgDark = Color(0xFF42A5F5)
    val todayTextDark = Color(0xFF0D1B2A)
    val upcomingBgDark = Color(0xFF66BB6A)
    val upcomingTextDark = Color(0xFF0D1B2A)
    val pastBgDark = Color(0xFF424242)
    val pastTextDark = Color(0xFFBDBDBD)
}

// ── Helpers ─────────────────────────────────────────────────────────────────────

/**
 * "Today" flights appear in BOTH tabs. Merge them into each tab's list.
 */
private fun flightsForTab(
    tab: FlightTab,
    upcoming: List<CalendarFlight>,
    past: List<CalendarFlight>
): List<CalendarFlight> {
    val now = System.currentTimeMillis()
    val todayFlights = (upcoming + past).distinctBy { it.id }.filter {
        it.scheduledTime.toRelativeTimeLabel(now) == "Today"
    }
    return when (tab) {
        FlightTab.UPCOMING -> {
            val ids = upcoming.map { it.id }.toSet()
            val missing = todayFlights.filter { it.id !in ids }
            (upcoming + missing).sortedBy { it.scheduledTime }
        }
        FlightTab.PAST -> {
            val ids = past.map { it.id }.toSet()
            val missing = todayFlights.filter { it.id !in ids }
            (missing + past).sortedByDescending { it.scheduledTime }
        }
    }
}

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

// ── Full-screen permission prompt ──────────────────────────────────────────────

@Composable
private fun PermissionFullScreen(
    icon: ImageVector,
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            when (buttonText) {
                "Grant Access" -> Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(buttonText) }
                "Try Again" -> FilledTonalButton(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(buttonText) }
                else -> FilledTonalButton(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(buttonText) }
            }
        }
    }
}

// ── Flight card ────────────────────────────────────────────────────────────────

@Composable
private fun FlightCard(
    flight: CalendarFlight,
    onClick: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val isUpcoming = flight.scheduledTime >= now
    val relativeLabel = flight.scheduledTime.toRelativeTimeLabel(now)
    val isToday = relativeLabel == "Today"

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }

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

                val routeText = if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank()) {
                    "Route pending"
                } else {
                    "${flight.departureCode}  \u2192  ${flight.arrivalCode}"
                }
                Text(
                    text = routeText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        Color.Unspecified
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormat.format(Date(flight.scheduledTime)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            RelativeTimeBadge(
                label = relativeLabel,
                isUpcoming = isUpcoming,
                isToday = isToday
            )
        }
    }
}

// ── Relative time badge with exact spec colors (light + dark) ──────────────────

@Composable
private fun RelativeTimeBadge(
    label: String,
    isUpcoming: Boolean,
    isToday: Boolean
) {
    val isDark = isSystemInDarkTheme()

    val containerColor = when {
        isToday -> if (isDark) BadgeColors.todayBgDark else BadgeColors.todayBgLight
        isUpcoming -> if (isDark) BadgeColors.upcomingBgDark else BadgeColors.upcomingBgLight
        else -> if (isDark) BadgeColors.pastBgDark else BadgeColors.pastBgLight
    }
    val contentColor = when {
        isToday -> if (isDark) BadgeColors.todayTextDark else BadgeColors.todayTextLight
        isUpcoming -> if (isDark) BadgeColors.upcomingTextDark else BadgeColors.upcomingTextLight
        else -> if (isDark) BadgeColors.pastTextDark else BadgeColors.pastTextLight
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── Detail bottom sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightDetailBottomSheet(
    flight: CalendarFlight,
    onDismiss: () -> Unit,
    onDismissFlight: (CalendarFlight) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val relativeLabel = flight.scheduledTime.toRelativeTimeLabel()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = relativeLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank()) {
                Text(
                    text = "Route pending",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = flight.departureCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(90f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = flight.arrivalCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (flight.flightNumber.isNotBlank()) {
                Text(
                    text = "Flight ${flight.flightNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = dateFormat.format(Date(flight.scheduledTime)),
                style = MaterialTheme.typography.bodyLarge
            )

            flight.endTime?.let { end ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arrives: ${dateFormat.format(Date(end))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            flight.endTime?.let { end ->
                val durationMinutes = ((end - flight.scheduledTime) / 60000).coerceAtLeast(0)
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${hours}h ${minutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Calendar: ${flight.rawTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onDismissFlight(flight) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
                Button(
                    onClick = { /* TODO: Add to logbook */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add to Logbook")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
