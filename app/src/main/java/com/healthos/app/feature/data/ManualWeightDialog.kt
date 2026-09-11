package com.healthos.app.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun ManualWeightDialog(
    viewModel: DataViewModel,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf("") }
    var weightError by remember { mutableStateOf(false) }
    var recordedAtMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your weight. HealthOS stores the reading and runs the internal body-composition estimate when a prior body-fat anchor is available.")
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it; weightError = false },
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("e.g. 69.4") },
                    isError = weightError,
                    supportingText = { if (weightError) Text("Enter a valid weight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${DateFormat.getDateInstance().format(Date(recordedAtMillis))}")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = weight.trim().toDoubleOrNull()
                weightError = value == null || value <= 0.0 || value > 500.0
                if (!weightError) {
                    viewModel.saveBodyMeasurement(com.healthos.app.domain.model.BodyMeasurementType.WEIGHT, value!!, "kg", recordedAtMillis)
                    onDismiss()
                }
            }) { Text("Save weight") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = recordedAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { recordedAtMillis = it }
                    showDatePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
