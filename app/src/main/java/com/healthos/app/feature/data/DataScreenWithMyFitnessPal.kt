package com.healthos.app.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthos.app.data.source.hevy.HevySyncImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalSyncImporter
import kotlinx.coroutines.launch

@Composable
fun DataScreenWithMyFitnessPal(
    viewModel: DataViewModel,
    onGarminSync: suspend (String, (Int, String) -> Unit) -> Int,
    onHevySync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result,
    onMyFitnessPalSync: suspend (String, (Int, String) -> Unit) -> MyFitnessPalSyncImporter.Result
) {
    var showSync by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        DataScreen(viewModel, onGarminSync, onHevySync)
        ExtendedFloatingActionButton(
            onClick = { showSync = true },
            icon = { Icon(Icons.Default.Sync, contentDescription = null) },
            text = { Text("Sync MyFitnessPal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }
    if (showSync) {
        MyFitnessPalSyncDialog(
            onDismiss = { showSync = false },
            onSync = onMyFitnessPalSync
        )
    }
}

@Composable
private fun MyFitnessPalSyncDialog(
    onDismiss: () -> Unit,
    onSync: suspend (String, (Int, String) -> Unit) -> MyFitnessPalSyncImporter.Result
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("7D") }
    var syncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf("Choose a sync window") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!syncing) onDismiss() },
        title = { Text("Sync MyFitnessPal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Fetch recent nutrition directly from MyFitnessPal. CSV-imported nutrition remains in the local database.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selected == "7D", onClick = { if (!syncing) selected = "7D" }, label = { Text("7D") })
                    FilterChip(selected = selected == "30D", onClick = { if (!syncing) selected = "30D" }, label = { Text("30D") })
                }
                if (syncing || resultText != null) {
                    Text(stage, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                resultText?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            Button(enabled = !syncing, onClick = {
                scope.launch {
                    syncing = true
                    error = false
                    progress = 0
                    resultText = null
                    stage = "Starting MyFitnessPal sync…"
                    try {
                        val result = onSync(selected) { p, s -> progress = p.coerceIn(0, 100); stage = s }
                        progress = 100
                        if (result.error != null) {
                            error = true
                            stage = "Sync failed"
                            resultText = result.error
                        } else {
                            stage = "Sync complete"
                            resultText = "${result.imported} nutrition entries synced${if (result.skipped > 0) "; ${result.skipped} skipped" else ""}."
                        }
                    } catch (e: Throwable) {
                        error = true
                        stage = "Sync failed"
                        resultText = e.message ?: "Unable to sync MyFitnessPal."
                    } finally {
                        syncing = false
                    }
                }
            }) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Text("Sync")
            }
        },
        dismissButton = { TextButton(enabled = !syncing, onClick = onDismiss) { Text("Close") } }
    )
}
