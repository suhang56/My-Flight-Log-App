package com.flightlog.app.ui.logbook

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.R
import com.flightlog.app.data.airport.AirportCoordinatesMap
import com.flightlog.app.data.local.entity.FlightStatus
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.network.FlightStatusEnum
import com.flightlog.app.util.DATE_FORMATTER
import com.flightlog.app.util.DAY_DATE_FORMATTER
import com.flightlog.app.util.TIME_TZ_FORMATTER
import com.flightlog.app.util.formatInZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: FlightDetailViewModel = hiltViewModel(),
    trackingViewModel: FlightTrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    val flightStatus by trackingViewModel.flightStatus.collectAsState()
    val aircraftPhotoState by viewModel.aircraftPhotoState.collectAsState()

    LaunchedEffect(uiState) {
        viewModel.onUiStateChanged(uiState)
        if (viewModel.shouldAutoNavigateBack(uiState)) {
            onNavigateBack()
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.flight_detail_delete_title)) },
            text = { Text(stringResource(R.string.flight_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete { onNavigateBack() } }) {
                    Text(stringResource(R.string.flight_detail_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.flight_detail_cancel))
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
                        title = { Text(stringResource(R.string.flight_detail_title)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.flight_detail_back))
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
                            text = stringResource(R.string.flight_detail_not_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is FlightDetailUiState.Success -> {
            val flight = state.flight
            val departureCityName = state.departureCityName
            val arrivalCityName = state.arrivalCityName
            val context = LocalContext.current

            LaunchedEffect(flight.id) {
                viewModel.fetchAircraftPhoto(flight.aircraftType)
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (flight.flightNumber.isNotBlank()) flight.flightNumber else stringResource(R.string.flight_detail_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.flight_detail_back))
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
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.flight_detail_share))
                            }
                            IconButton(onClick = { onNavigateToEdit(flight.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.flight_detail_edit))
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
                        arrivalCode = flight.arrivalCode,
                        departureCityName = departureCityName,
                        arrivalCityName = arrivalCityName
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live status card + track button
                    TrackingSection(
                        flight = flight,
                        flightStatus = flightStatus,
                        trackingViewModel = trackingViewModel
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TimelineSection(flight = flight)

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    FlightInfoSection(flight = flight)

                    if (!flight.aircraftType.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        AircraftCard(
                            aircraftType = flight.aircraftType,
                            registration = null,
                            photoState = aircraftPhotoState
                        )
                    }

                    val hasSeatInfo = !flight.seatClass.isNullOrBlank() || !flight.seatNumber.isNullOrBlank()
                    if (hasSeatInfo) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        SeatInfoSection(flight = flight)
                    }

                    if (!flight.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        NotesSection(notes = flight.notes.orEmpty())
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    RatingSection(
                        currentRating = flight.rating,
                        onRatingChanged = { viewModel.setRating(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    val departureCoords = remember(flight.departureCode) {
                        AirportCoordinatesMap.getCoords(flight.departureCode)?.let {
                            AirportCoordinatesMap.LatLng(it.first, it.second)
                        }
                    }
                    val arrivalCoords = remember(flight.arrivalCode) {
                        AirportCoordinatesMap.getCoords(flight.arrivalCode)?.let {
                            AirportCoordinatesMap.LatLng(it.first, it.second)
                        }
                    }
                    val livePosition = flightStatus?.let { status ->
                        val lat = status.liveLat
                        val lng = status.liveLng
                        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                            LivePosition(lat = lat, lng = lng, heading = status.liveHeading)
                        } else null
                    }
                    RouteMapCanvas(
                        departure = departureCoords,
                        arrival = arrivalCoords,
                        departureIata = flight.departureCode,
                        arrivalIata = flight.arrivalCode,
                        livePosition = livePosition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )

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
private fun RouteHeader(
    departureCode: String,
    arrivalCode: String,
    departureCityName: String? = null,
    arrivalCityName: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = departureCode,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (departureCityName != null) {
                Text(
                    text = departureCityName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = arrivalCode,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (arrivalCityName != null) {
                Text(
                    text = arrivalCityName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                text = stringResource(R.string.flight_detail_departed),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatInZone(flight.departureTimeMillis, flight.departureTimezone, DAY_DATE_FORMATTER),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatInZone(flight.departureTimeMillis, flight.departureTimezone, TIME_TZ_FORMATTER),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.flight_detail_arrived),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (flight.arrivalTimeMillis != null) {
                Text(
                    text = formatInZone(flight.arrivalTimeMillis, flight.arrivalTimezone, DAY_DATE_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatInZone(flight.arrivalTimeMillis, flight.arrivalTimezone, TIME_TZ_FORMATTER),
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

    val durationMinutes = flight.arrivalTimeMillis?.let { arr ->
        val diff = (arr - flight.departureTimeMillis) / 60000
        if (diff > 0) diff else null
    }
    val hasDistance = flight.distanceKm != null
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
                if (durationMinutes != null) {
                    val hours = durationMinutes / 60
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
                        text = stringResource(R.string.flight_detail_distance_km, String.format("%,d", flight.distanceKm)),
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
        InfoRow(label = stringResource(R.string.flight_detail_flight_label), value = flight.flightNumber)
    }
    if (!flight.aircraftType.isNullOrBlank()) {
        InfoRow(label = stringResource(R.string.flight_detail_aircraft_label), value = flight.aircraftType.orEmpty())
    }
    flight.distanceKm?.let { km ->
        InfoRow(label = stringResource(R.string.flight_detail_distance_label), value = stringResource(R.string.flight_detail_distance_km, String.format("%,d", km)))
    }
    InfoRow(
        label = stringResource(R.string.flight_detail_added_label),
        value = formatInZone(flight.createdAt, null, DATE_FORMATTER)
    )
    InfoRow(
        label = stringResource(R.string.flight_detail_source_label),
        value = if (flight.sourceCalendarEventId != null) stringResource(R.string.flight_detail_source_calendar) else stringResource(R.string.flight_detail_source_manual)
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
    if (!flight.seatClass.isNullOrBlank()) {
        InfoRow(label = stringResource(R.string.flight_detail_seat_class_label), value = flight.seatClass.orEmpty())
    }
    if (!flight.seatNumber.isNullOrBlank()) {
        InfoRow(label = stringResource(R.string.flight_detail_seat_number_label), value = flight.seatNumber.orEmpty())
    }
}

@Composable
private fun NotesSection(notes: String) {
    Text(
        text = stringResource(R.string.flight_detail_notes_label),
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
            Text(stringResource(R.string.flight_detail_delete), color = MaterialTheme.colorScheme.error)
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
            Text(stringResource(R.string.flight_detail_edit))
        }
    }
}

@Composable
private fun TrackingSection(
    flight: LogbookFlight,
    flightStatus: FlightStatus?,
    trackingViewModel: FlightTrackingViewModel
) {
    val context = LocalContext.current
    val isTracking = flightStatus?.trackingEnabled == true
    val canTrack = flight.flightNumber.isNotBlank()

    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start tracking regardless of permission result (notifications just won't show)
        trackingViewModel.startTracking(context, flight.flightNumber)
    }

    // Show live status card if we have status data
    if (flightStatus != null) {
        LiveStatusCard(flightStatus)
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (canTrack) {
        if (isTracking) {
            OutlinedButton(
                onClick = { trackingViewModel.stopTracking(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.flight_detail_tracking))
            }
        } else {
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        trackingViewModel.startTracking(context, flight.flightNumber)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.flight_detail_track_this_flight))
            }
        }
    }
}

@Composable
private fun LiveStatusCard(status: FlightStatus) {
    val statusEnum = runCatching { FlightStatusEnum.valueOf(status.statusEnum) }
        .getOrDefault(FlightStatusEnum.UNKNOWN)

    Surface(
        color = statusBadgeColor(statusEnum),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusEnum.name.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val parts = mutableListOf<String>()
            status.departureGate?.let { parts.add(stringResource(R.string.flight_detail_gate_format, it)) }
            status.departureDelayMin?.takeIf { it > 0 }?.let { parts.add(stringResource(R.string.flight_detail_delayed_format, it)) }
            status.liveAltitude?.let { parts.add(stringResource(R.string.flight_detail_altitude_format, String.format("%,d", it))) }
            status.liveSpeedKnots?.let { parts.add(stringResource(R.string.flight_detail_speed_format, it)) }

            if (parts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parts.joinToString("  |  "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun statusBadgeColor(status: FlightStatusEnum): Color {
    return when (status) {
        FlightStatusEnum.SCHEDULED -> MaterialTheme.colorScheme.outlineVariant
        FlightStatusEnum.BOARDING -> MaterialTheme.colorScheme.secondaryContainer
        FlightStatusEnum.EN_ROUTE, FlightStatusEnum.DEPARTED -> MaterialTheme.colorScheme.primaryContainer
        FlightStatusEnum.LANDED -> MaterialTheme.colorScheme.tertiaryContainer
        FlightStatusEnum.CANCELLED, FlightStatusEnum.DIVERTED -> MaterialTheme.colorScheme.errorContainer
        FlightStatusEnum.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
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

    val depTime = formatInZone(flight.departureTimeMillis, flight.departureTimezone)
    val arrTime = flight.arrivalTimeMillis?.let {
        formatInZone(it, flight.arrivalTimezone, TIME_TZ_FORMATTER)
    }
    lines += if (arrTime != null) "$depTime \u2192 $arrTime" else depTime

    val parts3 = mutableListOf<String>()
    flight.arrivalTimeMillis?.let { arr ->
        val diffMin = (arr - flight.departureTimeMillis) / 60000
        if (diffMin > 0) {
            parts3 += "Duration: ${diffMin / 60}h ${diffMin % 60}m"
        }
    }
    flight.distanceKm?.let { nm ->
        parts3 += "Distance: %,d km".format(nm)
    }
    if (parts3.isNotEmpty()) {
        lines += parts3.joinToString("  \u2022  ")
    }

    val parts4 = mutableListOf<String>()
    if (!flight.aircraftType.isNullOrBlank()) {
        parts4 += "Aircraft: ${flight.aircraftType}"
    }
    val seatParts = listOfNotNull(
        flight.seatClass?.takeIf { it.isNotBlank() },
        flight.seatNumber?.takeIf { it.isNotBlank() }?.let { "Seat $it" }
    ).joinToString(", ")
    if (seatParts.isNotBlank()) {
        parts4 += seatParts
    }
    if (parts4.isNotEmpty()) {
        lines += parts4.joinToString("  \u2022  ")
    }

    flight.rating?.let { rating ->
        lines += "Rating: ${"★".repeat(rating)}${"☆".repeat(5 - rating)}"
    }

    lines += "Logged with My Flight Log"

    return lines.joinToString("\n")
}
