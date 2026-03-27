package com.flightlog.app.ui.flights

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.local.entity.FlightEntity
import com.flightlog.app.util.RelativeTimeUtil
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    flightId: Long,
    onBackClick: () -> Unit,
    viewModel: FlightDetailViewModel = hiltViewModel()
) {
    val flight by viewModel.flight.collectAsState()

    LaunchedEffect(flightId) {
        viewModel.loadFlight(flightId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(flight?.flightNumber ?: "Flight Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        flight?.let { f ->
            FlightDetailContent(
                flight = f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun FlightDetailContent(
    flight: FlightEntity,
    modifier: Modifier = Modifier
) {
    val now = Instant.now()
    val isFuture = flight.departureTime.isAfter(now)
    val relativeTime = RelativeTimeUtil.relativeLabel(flight.departureTime, now)
    val flightDuration = Duration.between(flight.departureTime, flight.arrivalTime)
    val hours = flightDuration.toHours()
    val minutes = flightDuration.toMinutes() % 60

    Column(modifier = modifier) {
        // Header card with route visualization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isFuture) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Relative time label
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isFuture) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Route: DEP ✈ ARR
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = flight.departureAirport,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = RelativeTimeUtil.formatTime(flight.departureTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(90f),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = flight.arrivalAirport,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = RelativeTimeUtil.formatTime(flight.arrivalTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${hours}h ${minutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Detail rows
        DetailRow(
            icon = Icons.Default.Flight,
            label = "Flight Number",
            value = flight.flightNumber
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(
            icon = Icons.Default.FlightTakeoff,
            label = "Departure",
            value = "${flight.departureAirport} - ${RelativeTimeUtil.formatDateTime(flight.departureTime)}"
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(
            icon = Icons.Default.FlightLand,
            label = "Arrival",
            value = "${flight.arrivalAirport} - ${RelativeTimeUtil.formatDateTime(flight.arrivalTime)}"
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Duration",
            value = "${hours}h ${minutes}m"
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        DetailRow(
            icon = Icons.Default.CalendarMonth,
            label = "Calendar Event",
            value = flight.title
        )

        if (flight.notes.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(
                icon = Icons.Default.Notes,
                label = "Notes",
                value = flight.notes
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
