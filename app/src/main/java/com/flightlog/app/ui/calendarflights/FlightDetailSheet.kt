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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.util.toRelativeTimeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlightDetailBottomSheet(
    flight: CalendarFlight,
    onDismiss: () -> Unit,
    onDismissFlight: (CalendarFlight) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                text = formatInZone(flight.scheduledTime, flight.departureTimezone, FULL_DATE_TIME_TZ_FORMATTER),
                style = MaterialTheme.typography.bodyLarge
            )

            flight.endTime?.let { end ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arrives: ${formatInZone(end, flight.arrivalTimezone, FULL_DATE_TIME_TZ_FORMATTER)}",
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
