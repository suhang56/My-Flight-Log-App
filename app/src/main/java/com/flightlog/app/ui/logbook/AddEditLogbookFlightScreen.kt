package com.flightlog.app.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    var showDepartureTimePicker by remember { mutableStateOf(false) }
    var showArrivalTimePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
            // Airport codes row
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formState.departureCode,
                    onValueChange = viewModel::updateDepartureCode,
                    label = { Text("Departure") },
                    placeholder = { Text("NRT") },
                    isError = formState.departureCodeError != null,
                    supportingText = formState.departureCodeError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = formState.arrivalCode,
                    onValueChange = viewModel::updateArrivalCode,
                    label = { Text("Arrival") },
                    placeholder = { Text("HND") },
                    isError = formState.arrivalCodeError != null,
                    supportingText = formState.arrivalCodeError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Date picker
            OutlinedTextField(
                value = formState.departureDateMillis?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                label = { Text("Departure Date") },
                readOnly = true,
                isError = formState.departureDateError != null,
                supportingText = formState.departureDateError?.let { { Text(it) } },
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

            // Time pickers row
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formState.departureTimeMillis?.let { timeFormat.format(Date(it)) } ?: "",
                    onValueChange = {},
                    label = { Text("Dep. Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showDepartureTimePicker = true
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = formState.arrivalTimeMillis?.let { timeFormat.format(Date(it)) } ?: "",
                    onValueChange = {},
                    label = { Text("Arr. Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showArrivalTimePicker = true
                                }
                            }
                        }
                    }
                )
            }

            // Flight number
            OutlinedTextField(
                value = formState.flightNumber,
                onValueChange = viewModel::updateFlightNumber,
                label = { Text("Flight Number") },
                placeholder = { Text("NH211") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Aircraft type
            OutlinedTextField(
                value = formState.aircraftType,
                onValueChange = viewModel::updateAircraftType,
                label = { Text("Aircraft Type") },
                placeholder = { Text("B787-9") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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

    // Departure time picker dialog
    if (showDepartureTimePicker) {
        TimePickerDialog(
            onDismiss = { showDepartureTimePicker = false },
            onConfirm = { hour, minute ->
                val baseDate = formState.departureDateMillis ?: System.currentTimeMillis()
                val cal = Calendar.getInstance().apply {
                    timeInMillis = baseDate
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.updateDepartureTime(cal.timeInMillis)
                showDepartureTimePicker = false
            }
        )
    }

    // Arrival time picker dialog
    if (showArrivalTimePicker) {
        TimePickerDialog(
            onDismiss = { showArrivalTimePicker = false },
            onConfirm = { hour, minute ->
                val baseDate = formState.departureDateMillis ?: System.currentTimeMillis()
                val cal = Calendar.getInstance().apply {
                    timeInMillis = baseDate
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.updateArrivalTime(cal.timeInMillis)
                showArrivalTimePicker = false
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
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
