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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flightlog.app.R
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.ui.logbook.AircraftCard
import com.flightlog.app.ui.logbook.AircraftPhotoState
import com.flightlog.app.ui.logbook.RatingSection
import com.flightlog.app.util.toRelativeTimeLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FlightDetailContent(
    flight: CalendarFlight,
    onDismissFlight: (CalendarFlight) -> Unit,
    onAddToLogbook: suspend (CalendarFlight) -> Boolean,
    isAlreadyLogged: suspend (Long) -> Boolean,
    onLogbookSuccess: () -> Unit,
    getLinkedLogbookFlight: suspend (Long) -> LogbookFlight?,
    onRatingChanged: (Long, Int?) -> Unit,
    aircraftPhotoState: AircraftPhotoState = AircraftPhotoState(),
    onAircraftTypeResolved: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val relativeLabel = flight.scheduledTime.toRelativeTimeLabel()
    val now = remember { System.currentTimeMillis() }
    val isUpcoming = flight.scheduledTime >= now
    val isToday = relativeLabel == "Today"
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var alreadyLogged by remember { mutableStateOf<Boolean?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    var linkedFlight by remember { mutableStateOf<LogbookFlight?>(null) }

    LaunchedEffect(flight.calendarEventId) {
        alreadyLogged = isAlreadyLogged(flight.calendarEventId)
        val linked = getLinkedLogbookFlight(flight.calendarEventId)
        linkedFlight = linked
        onAircraftTypeResolved(linked?.aircraftType)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flight number + badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = flight.flightNumber.ifBlank { stringResource(R.string.flight_sheet_unknown) },
                style = MaterialTheme.typography.headlineMedium,
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
                text = stringResource(R.string.flight_sheet_route_pending),
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

        Text(
            text = dateFormat.format(Date(flight.scheduledTime)),
            style = MaterialTheme.typography.bodyLarge
        )

        flight.endTime?.let { end ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.flight_sheet_arrives_format, dateFormat.format(Date(end))),
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
                text = stringResource(R.string.flight_sheet_duration_format, hours, minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // Rating section — only show when flight is linked to logbook
        if (linkedFlight != null) {
            RatingSection(
                currentRating = linkedFlight?.rating,
                onRatingChanged = { newRating ->
                    linkedFlight?.let { lf ->
                        onRatingChanged(lf.id, newRating)
                        linkedFlight = lf.copy(rating = newRating)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            AircraftCard(
                aircraftType = linkedFlight?.aircraftType,
                registration = null,
                photoState = aircraftPhotoState,
                flightNumber = linkedFlight?.flightNumber
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.flight_sheet_calendar_format, flight.rawTitle),
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
                Text(stringResource(R.string.flight_sheet_dismiss))
            }
            when {
                alreadyLogged == true -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.flight_sheet_in_logbook))
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
                        Text(stringResource(if (isAdding) R.string.flight_sheet_adding else R.string.flight_sheet_add_to_logbook))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
