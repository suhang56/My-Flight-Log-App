package com.flightlog.app.ui.logbook

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val SEAT_CLASS_OPTIONS = listOf("", "Economy", "Premium Economy", "Business", "First")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLogbookFlightScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddEditLogbookFlightViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val departureSuggestions by viewModel.departureSuggestions.collectAsState()
    val arrivalSuggestions by viewModel.arrivalSuggestions.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(form.savedSuccessfully) {
        if (form.savedSuccessfully) onNavigateBack()
    }

    form.duplicateWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            title = { Text("Possible duplicate") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveDespiteDuplicate() }) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete flight?") },
            text = { Text("This will permanently remove the flight from your logbook.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditMode) "Edit Flight" else "Add Flight") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !form.isSaving && !form.isSearching
                    ) {
                        Text("Save")
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
            Spacer(modifier = Modifier.height(4.dp))

            // Flight Search section (add mode only)
            if (!form.isEditMode) {
                Text(
                    "Flight Search",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = form.flightSearchQuery,
                        onValueChange = { viewModel.updateFlightSearchQuery(it) },
                        label = { Text("Flight No.") },
                        placeholder = { Text("JL5") },
                        singleLine = true,
                        enabled = !form.isSearching,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.searchFlight() }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    DatePickerField(
                        date = form.flightSearchDate,
                        onDateSelected = { viewModel.updateFlightSearchDate(it) },
                        label = "Date",
                        enabled = !form.isSearching,
                        modifier = Modifier.weight(1f)
                    )

                    if (form.isSearching) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = { viewModel.searchFlight() },
                            enabled = form.flightSearchQuery.isNotBlank(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search flight")
                        }
                    }
                }

                if (form.searchError != null) {
                    Text(
                        text = form.searchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (form.autoFillApplied) {
                    AssistChip(
                        onClick = { viewModel.dismissAutoFillBanner() },
                        label = { Text("Auto-filled from flight data") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Route section
            Text("Route", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AirportAutocompleteField(
                    value = form.departureCode,
                    onValueChange = { if (it.length <= 3) viewModel.updateDepartureCode(it) },
                    suggestions = departureSuggestions,
                    onAirportSelected = { viewModel.selectDepartureAirport(it) },
                    onDismissSuggestions = { viewModel.dismissDepartureSuggestions() },
                    label = "From",
                    placeholder = "ORD",
                    isError = form.departureCodeError != null,
                    errorText = form.departureCodeError,
                    enabled = !form.isSearching,
                    modifier = Modifier.weight(1f)
                )
                AirportAutocompleteField(
                    value = form.arrivalCode,
                    onValueChange = { if (it.length <= 3) viewModel.updateArrivalCode(it) },
                    suggestions = arrivalSuggestions,
                    onAirportSelected = { viewModel.selectArrivalAirport(it) },
                    onDismissSuggestions = { viewModel.dismissArrivalSuggestions() },
                    label = "To",
                    placeholder = "CMH",
                    isError = form.arrivalCodeError != null,
                    errorText = form.arrivalCodeError,
                    enabled = !form.isSearching,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = form.flightNumber,
                onValueChange = { viewModel.updateFlightNumber(it) },
                label = { Text("Flight Number") },
                placeholder = { Text("AA11") },
                singleLine = true,
                enabled = !form.isSearching,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Date & Time section
            Spacer(modifier = Modifier.height(4.dp))
            Text("Date & Time", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            DatePickerField(
                date = form.date,
                onDateSelected = { viewModel.updateDate(it) },
                label = "Date",
                enabled = !form.isSearching
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimePickerField(
                    time = form.departureTime,
                    onTimeSelected = { viewModel.updateDepartureTime(it) },
                    label = "Departure",
                    modifier = Modifier.weight(1f)
                )
                TimePickerField(
                    time = form.arrivalTime,
                    onTimeSelected = { viewModel.updateArrivalTime(it) },
                    label = "Arrival",
                    modifier = Modifier.weight(1f)
                )
            }

            // Details section
            Spacer(modifier = Modifier.height(4.dp))
            Text("Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = form.aircraftType,
                onValueChange = { viewModel.updateAircraftType(it) },
                label = { Text("Aircraft Type") },
                placeholder = { Text("Boeing 737-800") },
                singleLine = true,
                enabled = !form.isSearching,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            SeatClassDropdown(
                selected = form.seatClass,
                onSelected = { viewModel.updateSeatClass(it) }
            )

            OutlinedTextField(
                value = form.seatNumber,
                onValueChange = { viewModel.updateSeatNumber(it) },
                label = { Text("Seat Number") },
                placeholder = { Text("12A") },
                singleLine = true,
                enabled = !form.isSearching,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Notes section
            Spacer(modifier = Modifier.height(4.dp))
            Text("Notes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = form.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes") },
                placeholder = { Text("Add any notes about this flight...") },
                enabled = !form.isSearching,
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // Delete button (edit mode only)
            if (form.isEditMode) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Flight", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Date picker field ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var showDialog by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    LaunchedEffect(date) {
        datePickerState.selectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    OutlinedTextField(
        value = date.format(DISPLAY_DATE_FORMATTER),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }, enabled = enabled) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
            }
        },
        modifier = modifier
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onDateSelected(selected)
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Time picker field ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    time: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    val displayTime = time ?: LocalTime.of(12, 0)
    val timePickerState = rememberTimePickerState(
        initialHour = displayTime.hour,
        initialMinute = displayTime.minute,
        is24Hour = true
    )

    OutlinedTextField(
        value = time?.format(DISPLAY_TIME_FORMATTER) ?: "",
        onValueChange = {},
        label = { Text(label) },
        placeholder = { Text("HH:mm") },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Schedule, contentDescription = "Pick time")
            }
        },
        modifier = modifier
    )

    if (showDialog) {
        TimePickerDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                showDialog = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

// ── Time picker dialog wrapper ──────────────────────────────────────────────────

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { content() }
    )
}

// ── Airport autocomplete field ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirportAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<com.flightlog.app.data.local.entity.Airport>,
    onAirportSelected: (com.flightlog.app.data.local.entity.Airport) -> Unit,
    onDismissSuggestions: () -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val expanded = suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!it) onDismissSuggestions() },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = isError,
            supportingText = errorText?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissSuggestions
        ) {
            suggestions.forEach { airport ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "${airport.iata} — ${airport.city}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = airport.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onAirportSelected(airport) }
                )
            }
        }
    }
}

// ── Seat class dropdown ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatClassDropdown(
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.ifEmpty { "Not specified" },
            onValueChange = {},
            label = { Text("Seat Class") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SEAT_CLASS_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { "Not specified" }) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
