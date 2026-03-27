package com.flightlog.app.ui.logbook

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.util.DATE_FORMATTER
import com.flightlog.app.util.DAY_DATE_FORMATTER
import com.flightlog.app.util.TIME_TZ_FORMATTER
import com.flightlog.app.util.formatInZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: FlightDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()

    LaunchedEffect(uiState) {
        viewModel.onUiStateChanged(uiState)
        if (viewModel.shouldAutoNavigateBack(uiState)) {
            onNavigateBack()
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete flight?") },
            text = { Text("This will permanently remove the flight from your logbook.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete { onNavigateBack() } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }

    when (val state = uiState) {
        is FlightDetailUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is FlightDetailUiState.NotFound -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Flight Detail") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Flight not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is FlightDetailUiState.Success -> {
            val flight = state.flight
            val context = LocalContext.current

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (flight.flightNumber.isNotBlank()) flight.flightNumber else "Flight Detail",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                val shareText = buildShareText(flight)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = { onNavigateToEdit(flight.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    RouteHeader(
                        departureCode = flight.departureCode,
                        arrivalCode = flight.arrivalCode
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TimelineSection(flight = flight)

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    FlightInfoSection(flight = flight)

                    val hasSeatInfo = flight.seatClass.isNotBlank() || flight.seatNumber.isNotBlank()
                    if (hasSeatInfo) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        SeatInfoSection(flight = flight)
                    }

                    if (flight.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        NotesSection(notes = flight.notes)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    MapPlaceholder()

                    Spacer(modifier = Modifier.height(24.dp))

                    ActionRow(
                        onDelete = { viewModel.requestDelete() },
                        onEdit = { onNavigateToEdit(flight.id) }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun RouteHeader(departureCode: String, arrivalCode: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = departureCode,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(20.dp))
        Icon(
            imageVector = Icons.Default.Flight,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .rotate(90f),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = arrivalCode,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TimelineSection(flight: LogbookFlight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DEPARTED",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatInZone(flight.departureTimeUtc, flight.departureTimezone, DAY_DATE_FORMATTER),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatInZone(flight.departureTimeUtc, flight.departureTimezone, TIME_TZ_FORMATTER),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ARRIVED",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (flight.arrivalTimeUtc != null) {
                Text(
                    text = formatInZone(flight.arrivalTimeUtc, flight.arrivalTimezone, DAY_DATE_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatInZone(flight.arrivalTimeUtc, flight.arrivalTimezone, TIME_TZ_FORMATTER),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "\u2014",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u2014",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val durationMinutes = flight.arrivalTimeUtc?.let { arr ->
        val diff = (arr - flight.departureTimeUtc) / 60000
        if (diff > 0) diff else null
    }
    val hasDistance = flight.distanceNm != null
    val hasDuration = durationMinutes != null

    if (hasDuration || hasDistance) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasDuration) {
                    val hours = durationMinutes!! / 60
                    val minutes = durationMinutes % 60
                    Text(
                        text = "${hours}h ${minutes}m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (hasDuration && hasDistance) {
                    Text(
                        text = "  |  ",
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
                if (hasDistance) {
                    Text(
                        text = "%,d NM".format(flight.distanceNm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun FlightInfoSection(flight: LogbookFlight) {
    if (flight.flightNumber.isNotBlank()) {
        InfoRow(label = "Flight", value = flight.flightNumber)
    }
    if (flight.aircraftType.isNotBlank()) {
        InfoRow(label = "Aircraft", value = flight.aircraftType)
    }
    flight.distanceNm?.let { nm ->
        InfoRow(label = "Distance", value = "%,d NM".format(nm))
    }
    InfoRow(
        label = "Added",
        value = formatInZone(flight.addedAt, null, DATE_FORMATTER)
    )
    InfoRow(
        label = "Source",
        value = if (flight.sourceCalendarEventId != null) "Calendar sync" else "Manually added"
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SeatInfoSection(flight: LogbookFlight) {
    if (flight.seatClass.isNotBlank()) {
        InfoRow(label = "Seat Class", value = flight.seatClass)
    }
    if (flight.seatNumber.isNotBlank()) {
        InfoRow(label = "Seat Number", value = flight.seatNumber)
    }
}

@Composable
private fun NotesSection(notes: String) {
    Text(
        text = "NOTES",
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    SelectionContainer {
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MapPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Route map coming soon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ActionRow(onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDelete,
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
            onClick = onEdit,
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
}

fun buildShareText(flight: LogbookFlight): String {
    val lines = mutableListOf<String>()

    val routePrefix = if (flight.flightNumber.isNotBlank()) {
        "\u2708 ${flight.flightNumber}: "
    } else {
        "\u2708 "
    }
    lines += "${routePrefix}${flight.departureCode} \u2192 ${flight.arrivalCode}"

    val depTime = formatInZone(flight.departureTimeUtc, flight.departureTimezone)
    val arrTime = flight.arrivalTimeUtc?.let {
        formatInZone(it, flight.arrivalTimezone, TIME_TZ_FORMATTER)
    }
    lines += if (arrTime != null) "$depTime \u2192 $arrTime" else depTime

    val parts3 = mutableListOf<String>()
    flight.arrivalTimeUtc?.let { arr ->
        val diffMin = (arr - flight.departureTimeUtc) / 60000
        if (diffMin > 0) {
            parts3 += "Duration: ${diffMin / 60}h ${diffMin % 60}m"
        }
    }
    flight.distanceNm?.let { nm ->
        parts3 += "Distance: %,d NM".format(nm)
    }
    if (parts3.isNotEmpty()) {
        lines += parts3.joinToString("  \u2022  ")
    }

    val parts4 = mutableListOf<String>()
    if (flight.aircraftType.isNotBlank()) {
        parts4 += "Aircraft: ${flight.aircraftType}"
    }
    val seatParts = listOfNotNull(
        flight.seatClass.takeIf { it.isNotBlank() },
        flight.seatNumber.takeIf { it.isNotBlank() }?.let { "Seat $it" }
    ).joinToString(", ")
    if (seatParts.isNotBlank()) {
        parts4 += seatParts
    }
    if (parts4.isNotEmpty()) {
        lines += parts4.joinToString("  \u2022  ")
    }

    lines += "Logged with My Flight Log"

    return lines.joinToString("\n")
}
