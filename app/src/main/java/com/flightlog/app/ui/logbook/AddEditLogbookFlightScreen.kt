package com.flightlog.app.ui.logbook

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
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
                        enabled = !form.isSaving
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

            // Route section
            Text("Route", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.departureCode,
                    onValueChange = { if (it.length <= 3) viewModel.updateDepartureCode(it) },
                    label = { Text("From") },
                    placeholder = { Text("ORD") },
                    isError = form.departureCodeError != null,
                    supportingText = form.departureCodeError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.arrivalCode,
                    onValueChange = { if (it.length <= 3) viewModel.updateArrivalCode(it) },
                    label = { Text("To") },
                    placeholder = { Text("CMH") },
                    isError = form.arrivalCodeError != null,
                    supportingText = form.arrivalCodeError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = form.flightNumber,
                onValueChange = { viewModel.updateFlightNumber(it) },
                label = { Text("Flight Number") },
                placeholder = { Text("AA11") },
                singleLine = true,
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
                label = "Date"
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
    label: String
) {
    var showDialog by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    OutlinedTextField(
        value = date.format(DISPLAY_DATE_FORMATTER),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
            }
        },
        modifier = Modifier.fillMaxWidth()
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
