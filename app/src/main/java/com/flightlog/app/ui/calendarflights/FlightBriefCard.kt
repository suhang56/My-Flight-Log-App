package com.flightlog.app.ui.calendarflights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.util.toRelativeTimeLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FlightBriefCard(
    flight: CalendarFlight,
    onDismiss: (CalendarFlight) -> Unit,
    onAddToLogbook: suspend (CalendarFlight) -> Boolean,
    isAlreadyLogged: suspend (Long) -> Boolean,
    onLogbookSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val relativeLabel = flight.scheduledTime.toRelativeTimeLabel()
    val now = remember { System.currentTimeMillis() }
    val isUpcoming = flight.scheduledTime >= now
    val isToday = relativeLabel == "Today"
    val scope = rememberCoroutineScope()

    var alreadyLogged by remember { mutableStateOf<Boolean?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    LaunchedEffect(flight.calendarEventId) {
        alreadyLogged = isAlreadyLogged(flight.calendarEventId)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Flight number + badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = flight.flightNumber.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            RelativeTimeBadge(
                label = relativeLabel,
                isUpcoming = isUpcoming,
                isToday = isToday
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Route row
        if (flight.departureCode.isBlank() && flight.arrivalCode.isBlank()) {
            Text(
                text = "Route pending",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = flight.departureCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
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
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date/time
        Text(
            text = dateFormat.format(Date(flight.scheduledTime)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Arrival time
        flight.endTime?.let { end ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Arrives: ${dateFormat.format(Date(end))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Duration
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

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onDismiss(flight) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Dismiss")
            }
            when {
                alreadyLogged == true -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("In Logbook")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            scope.launch {
                                isAdding = true
                                val success = onAddToLogbook(flight)
                                isAdding = false
                                if (success) {
                                    alreadyLogged = true
                                    onLogbookSuccess()
                                }
                            }
                        },
                        enabled = !isAdding && alreadyLogged == false,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isAdding) "Adding..." else "Add to Logbook")
                    }
                }
            }
        }
    }
}
