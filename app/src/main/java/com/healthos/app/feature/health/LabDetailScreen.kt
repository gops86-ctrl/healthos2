package com.healthos.app.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthos.app.domain.model.LabResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LabDetailScreen(
    result: LabResult,
    onBack: () -> Unit,
    onSave: suspend (String, Double, String, String?, Long) -> Unit,
    onDelete: suspend () -> Unit
) {
    var editing by remember(result.id) { mutableStateOf(false) }
    var name by remember(result.id) { mutableStateOf(result.testName) }
    var value by remember(result.id) { mutableStateOf(result.value.toString()) }
    var unit by remember(result.id) { mutableStateOf(result.unit) }
    var reference by remember(result.id) { mutableStateOf(result.referenceRange.orEmpty()) }
    var showDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            if (!editing) {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        }

        Text("Lab result", style = MaterialTheme.typography.headlineLarge)

        if (editing) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Test name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text("Reference range") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Date: ${DateFormat.getDateInstance().format(Date(result.provenance.recordedAtMillis))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val numeric = value.trim().toDoubleOrNull()
                    if (name.isNotBlank() && numeric != null) {
                        scope.launch {
                            onSave(
                                name.trim(),
                                numeric,
                                unit.trim(),
                                reference.trim().ifBlank { null },
                                result.provenance.recordedAtMillis
                            )
                            editing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }
            TextButton(
                onClick = {
                    editing = false
                    name = result.testName
                    value = result.value.toString()
                    unit = result.unit
                    reference = result.referenceRange.orEmpty()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(result.testName, style = MaterialTheme.typography.headlineMedium)
                    Text("${result.value} ${result.unit}", style = MaterialTheme.typography.headlineSmall)
                    result.referenceRange?.let {
                        Text(
                            "Reference: $it",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        DateFormat.getDateInstance().format(Date(result.provenance.recordedAtMillis)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete result")
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete lab result?") },
            text = { Text("This result will be permanently removed from HealthOS.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDelete()
                            onBack()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
