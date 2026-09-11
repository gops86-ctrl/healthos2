package com.healthos.app.feature.strength

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(viewModel: WorkoutDetailViewModel, onBack: () -> Unit) {
    val detail by viewModel.detail.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.workout?.name ?: "Workout") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete workout")
                    }
                }
            )
        }
    ) { padding ->
        val currentDetail = detail
        if (currentDetail == null) {
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp)) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            currentDetail.workout.durationSeconds?.let { WorkoutSummary("Duration", formatDuration(it), Modifier.weight(1f)) }
                            currentDetail.workout.totalVolumeKg?.let { WorkoutSummary("Volume", String.format(Locale.US, "%.0f kg", it), Modifier.weight(1f)) }
                            WorkoutSummary("Exercises", currentDetail.exercises.size.toString(), Modifier.weight(1f))
                        }
                    }
                }
                if (currentDetail.exercises.isEmpty()) {
                    item { Text("No exercises recorded for this workout.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(currentDetail.exercises) { exerciseWithSets ->
                        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(exerciseWithSets.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
                                    Text("REPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text("WEIGHT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f))
                                }
                                exerciseWithSets.sets.forEachIndexed { index, set ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${index + 1}", modifier = Modifier.weight(0.7f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(set.repetitions?.toString() ?: "–", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Text(set.weightKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "–", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the workout from HealthOS only. It will not delete the original workout from Hevy.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteWorkout(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WorkoutSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds) else String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}
