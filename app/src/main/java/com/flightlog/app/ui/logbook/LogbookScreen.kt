package com.flightlog.app.ui.logbook

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.LogbookFlight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private val FULL_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy  HH:mm z", Locale.getDefault())

private fun formatDate(epochMillis: Long, ianaTimezone: String?): String {
    val zone = ianaTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(zone).format(DATE_FORMATTER)
}

private fun formatDateTime(epochMillis: Long, ianaTimezone: String?): String {
    val zone = ianaTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(zone).format(FULL_DATE_TIME_FORMATTER)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    onAddFlight: () -> Unit = {},
    onEditFlight: (Long) -> Unit = {},
    viewModel: LogbookViewModel = hiltViewModel()
) {
    val flights by viewModel.flights.collectAsState()
    val flightCount by viewModel.flightCount.collectAsState()
    val totalDistanceNm by viewModel.totalDistanceNm.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showDetailSheet && uiState.selectedFlight != null) {
        LogbookDetailBottomSheet(
            flight = uiState.selectedFlight!!,
            onDismiss = viewModel::dismissDetailSheet,
            onDelete = { viewModel.deleteFlight(it.id) },
            onEdit = { flight ->
                viewModel.dismissDetailSheet()
                onEditFlight(flight.id)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Logbook") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFlight) {
                Icon(Icons.Default.Add, contentDescription = "Add flight")
            }
        }
    ) { padding ->
        if (flights.isEmpty()) {
            LogbookEmptyState(
                onAddFlight = onAddFlight,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                item(key = "stats") {
                    StatsRow(
                        flightCount = flightCount,
                        totalDistanceNm = totalDistanceNm
                    )
                }
                items(
                    items = flights,
                    key = { it.id }
                ) { flight ->
                    LogbookCard(
                        flight = flight,
                        onClick = { viewModel.selectFlight(flight) }
                    )
                }
            }
        }
    }
}

// ── Stats row ───────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    flightCount: Int,
    totalDistanceNm: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            icon = Icons.Default.FlightTakeoff,
            value = "$flightCount",
            label = if (flightCount == 1) "Flight" else "Flights"
        )
        StatItem(
            icon = Icons.Default.Route,
            value = "%,d".format(totalDistanceNm),
            label = "NM Total"
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
                    text = formatDate(flight.departureTimeUtc, flight.departureTimezone),
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

// ── Detail bottom sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookDetailBottomSheet(
    flight: LogbookFlight,
    onDismiss: () -> Unit,
    onDelete: (LogbookFlight) -> Unit,
    onEdit: (LogbookFlight) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = formatDateTime(flight.departureTimeUtc, flight.departureTimezone),
                style = MaterialTheme.typography.bodyLarge
            )

            flight.arrivalTimeUtc?.let { end ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arrives: ${formatDateTime(end, flight.arrivalTimezone)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            flight.arrivalTimeUtc?.let { end ->
                val durationMinutes = ((end - flight.departureTimeUtc) / 60000).coerceAtLeast(0)
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${hours}h ${minutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            flight.distanceNm?.let { nm ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Distance: %,d NM".format(nm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (flight.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = flight.notes,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onDelete(flight) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { onEdit(flight) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                Text("Add Flight")
            }
        }
    }
}
