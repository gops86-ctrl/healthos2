package com.healthos.app.feature.data

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthos.app.data.source.hevy.HevySyncImporter
import kotlinx.coroutines.launch

enum class HevySyncRange(val label: String, val key: String) { DAYS_7("7D", "7D"), DAYS_30("30D", "30D") }

@Composable
fun DataScreenWithHevy(viewModel: DataViewModel, onGarminSync: suspend (String, (Int, String) -> Unit) -> Int, onHevySync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result) {
    var showHevy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        DataScreen(viewModel, onGarminSync)
        FloatingActionButton(onClick = { showHevy = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.FitnessCenter, contentDescription = "Sync Hevy")
        }
    }
    if (showHevy) HevySyncDialog({ showHevy = false }, onHevySync)
}

@Composable
private fun HevySyncDialog(onDismiss: () -> Unit, onSync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(HevySyncRange.DAYS_7) }
    var syncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf("Choose a sync window") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = { if (!syncing) onDismiss() }, title = { Text("Sync Hevy") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Fetch recent Hevy workouts, deduplicate them against local records, then update the canonical HealthOS snapshot.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HevySyncRange.values().forEach { range -> FilterChip(selected = selected == range, onClick = { if (!syncing) selected = range }, label = { Text(range.label) }) } }
            if (syncing || resultText != null) {
                Text(stage, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            resultText?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }, confirmButton = {
        Button(enabled = !syncing, onClick = {
            scope.launch {
                syncing = true; error = false; progress = 0; resultText = null; stage = "Starting Hevy sync…"
                try {
                    val result = onSync(selected.key) { p, s -> progress = p.coerceIn(0, 100); stage = s }
                    progress = 100
                    if (result.error != null) { error = true; stage = "Sync failed"; resultText = result.error }
                    else { stage = "Sync complete"; resultText = "${result.imported} added, ${result.skipped} already present." }
                } catch (e: Throwable) { error = true; stage = "Sync failed"; resultText = e.message ?: "Unable to sync Hevy." }
                finally { syncing = false }
            }
        }) { Icon(Icons.Default.Sync, null); Text(" Sync") }
    }, dismissButton = { TextButton(enabled = !syncing, onClick = onDismiss) { Text("Close") } })
}
