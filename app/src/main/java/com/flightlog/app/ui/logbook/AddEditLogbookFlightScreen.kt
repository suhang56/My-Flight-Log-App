package com.flightlog.app.ui.logbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.flightlog.app.R
import com.flightlog.app.data.network.FlightRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLogbookFlightScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddEditLogbookFlightViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) onNavigateBack()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (formState.isEditMode) "Edit Flight" else "Add Flight") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -- Step 1: Flight number + date --
            Text(
                text = "Flight Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Flight number
            OutlinedTextField(
                value = formState.flightNumber,
                onValueChange = viewModel::updateFlightNumber,
                label = { Text("Flight Number") },
                placeholder = { Text("NH211") },
                leadingIcon = { Icon(Icons.Default.AirplanemodeActive, contentDescription = null) },
                isError = formState.flightNumberError != null,
                supportingText = formState.flightNumberError?.let { { Text(it) } },
                singleLine = true,
                enabled = !formState.isEditMode || formState.lookupState is LookupState.Idle,
                modifier = Modifier.fillMaxWidth()
            )

            // Date picker
            OutlinedTextField(
                value = formState.departureDateMillis?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                label = { Text("Date") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                readOnly = true,
                isError = formState.departureDateError != null,
                supportingText = formState.departureDateError?.let { { Text(it) } },
                enabled = !formState.isEditMode || formState.lookupState is LookupState.Idle,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { source ->
                    LaunchedEffect(source) {
                        source.interactions.collect { interaction ->
                            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                showDatePicker = true
                            }
                        }
                    }
                }
            )

            // Departure airport (shown when date is >7 days in the future)
            AnimatedVisibility(
                visible = formState.showDepartureAirportLookupField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    OutlinedTextField(
                        value = formState.departureAirportForLookup,
                        onValueChange = viewModel::updateDepartureAirportForLookup,
                        label = { Text(stringResource(R.string.add_flight_departure_airport_label)) },
                        placeholder = { Text(stringResource(R.string.add_flight_departure_airport_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.add_flight_departure_airport_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            // Lookup button (only in add mode or when editing with Idle state)
            if (!formState.isEditMode || formState.lookupState is LookupState.Idle) {
                Button(
                    onClick = viewModel::lookupFlight,
                    enabled = formState.lookupState !is LookupState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (formState.lookupState is LookupState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Looking up flight...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Look Up Flight")
                    }
                }
            }

            // -- Lookup error --
            if (formState.lookupState is LookupState.Error) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (formState.lookupState as LookupState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                TextButton(onClick = viewModel::resetLookup) {
                    Text("Try again")
                }
            }

            // -- Disambiguation: multiple flights found --
            if (formState.lookupState is LookupState.Disambiguate) {
                val routes = (formState.lookupState as LookupState.Disambiguate).routes
                Text(
                    text = "Multiple flights found. Select yours:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                routes.forEach { route ->
                    DisambiguationCard(
                        route = route,
                        onClick = { viewModel.selectRoute(route) }
                    )
                }
                TextButton(onClick = viewModel::resetLookup) {
                    Text("Go back")
                }
            }

            // -- Step 2: Auto-populated details + personal fields --
            val showDetails = formState.lookupState is LookupState.Success || formState.isEditMode
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Flight details card (auto-populated, read-only summary)
                    if (formState.departureCode.isNotBlank() && formState.arrivalCode.isNotBlank()) {
                        FlightDetailsCard(formState)
                    }

                    // Change flight button (only in add mode, after successful lookup)
                    if (!formState.isEditMode && formState.lookupState is LookupState.Success) {
                        TextButton(onClick = viewModel::resetLookup) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change flight")
                        }
                    }

                    // -- Personal details --
                    Text(
                        text = "Personal Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Seat class dropdown
                    SeatClassDropdown(
                        selectedClass = formState.seatClass,
                        onClassSelected = viewModel::updateSeatClass
                    )

                    // Seat number
                    OutlinedTextField(
                        value = formState.seatNumber,
                        onValueChange = viewModel::updateSeatNumber,
                        label = { Text("Seat Number") },
                        placeholder = { Text("12A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Notes
                    OutlinedTextField(
                        value = formState.notes,
                        onValueChange = viewModel::updateNotes,
                        label = { Text("Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save button
                    Button(
                        onClick = viewModel::saveFlight,
                        enabled = !formState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (formState.isEditMode) "Update Flight" else "Save Flight")
                    }

                    // Delete button (edit mode only)
                    if (formState.isEditMode) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete Flight")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.departureDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDepartureDate(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Flight") },
            text = { Text("Are you sure you want to delete this flight? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteFlight()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FlightDetailsCard(formState: LogbookFormState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Route
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.FlightTakeoff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formState.departureCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\u2192",
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    Icons.Default.FlightLand,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formState.arrivalCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Times
            if (formState.departureTimeMillis != null || formState.arrivalTimeMillis != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    val depTime = formState.departureTimeMillis?.let {
                        AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(it, formState.departureTimezone)
                    } ?: "--:--"
                    val arrTime = formState.arrivalTimeMillis?.let {
                        AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(it, formState.arrivalTimezone)
                    } ?: "--:--"
                    Text(
                        text = "$depTime \u2192 $arrTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Aircraft type
            if (formState.aircraftType.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AirplanemodeActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formState.aircraftType,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisambiguationCard(
    route: FlightRoute,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${route.departureIata} \u2192 ${route.arrivalIata}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val depTime = route.departureScheduledUtc?.let {
                    AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(it, route.departureTimezone)
                } ?: "--:--"
                val arrTime = route.arrivalScheduledUtc?.let {
                    AddEditLogbookFlightViewModel.formatUtcMillisToLocalTime(it, route.arrivalTimezone)
                } ?: "--:--"
                Text(
                    text = "$depTime \u2192 $arrTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (route.aircraftType != null) {
                Text(
                    text = route.aircraftType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatClassDropdown(
    selectedClass: String,
    onClassSelected: (String) -> Unit
) {
    val options = listOf("" to "Not specified", "economy" to "Economy", "premium_economy" to "Premium Economy", "business" to "Business", "first" to "First")
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.find { it.first == selectedClass }?.second ?: "Not specified"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Seat Class") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onClassSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
