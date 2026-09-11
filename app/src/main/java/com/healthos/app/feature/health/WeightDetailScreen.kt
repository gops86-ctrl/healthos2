package com.healthos.app.feature.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthos.app.core.ui.charts.WeightTrendChart
import com.healthos.app.data.source.healthify.HealthifyWeightEstimator
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.BodyMeasurementType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class WeightTrendRange(val label: String) {
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    ALL_TIME("All")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDetailScreen(
    bodyMeasurements: List<BodyMeasurement>,
    onBack: () -> Unit,
    onDelete: suspend (Long) -> Unit
) {
    val weights = bodyMeasurements
        .filter { it.measurementType == BodyMeasurementType.WEIGHT }
        .sortedByDescending { it.provenance.recordedAtMillis }
    var selectedRange by remember { mutableStateOf(WeightTrendRange.ALL_TIME) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    val filteredWeights = remember(weights, selectedRange) {
        if (selectedRange == WeightTrendRange.ALL_TIME) {
            weights
        } else {
            val cutoff = Calendar.getInstance().apply {
                when (selectedRange) {
                    WeightTrendRange.THREE_MONTHS -> add(Calendar.MONTH, -3)
                    WeightTrendRange.SIX_MONTHS -> add(Calendar.MONTH, -6)
                    WeightTrendRange.ONE_YEAR -> add(Calendar.YEAR, -1)
                    WeightTrendRange.ALL_TIME -> Unit
                }
            }.timeInMillis
            weights.filter { it.provenance.recordedAtMillis >= cutoff }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("HealthifyMe", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text("${filteredWeights.size} readings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Weight history with personalized Smart Scale estimates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Estimated values are derived from your historical HealthifyMe readings and are not direct Smart Scale measurements.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeightTrendRange.entries.forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { selectedRange = range },
                            label = { Text(range.label) }
                        )
                    }
                }
            }

            if (filteredWeights.size >= 2) {
                item {
                    WeightTrendChart(weights = filteredWeights.map { it.provenance.recordedAtMillis to it.value })
                }
            } else if (filteredWeights.isEmpty()) {
                item {
                    Text("No weight readings in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(filteredWeights, key = { it.id }) { measurement ->
                val estimate = HealthifyWeightEstimator.estimate(measurement.value)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("%.2f kg".format(Locale.US, measurement.value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(measurement.provenance.recordedAtMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                Text("Estimated", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { pendingDeleteId = measurement.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete weight reading")
                                }
                            }
                        }
                        EstimateRow("Body fat", "%.1f%%".format(Locale.US, estimate.bodyFatPercent), "Fat mass %.1f kg".format(Locale.US, estimate.fatMassKg))
                        EstimateRow("Lean body mass", "%.1f kg".format(Locale.US, estimate.leanBodyMassKg), "BMR %d Cal".format(Locale.US, estimate.bmrCalories))
                        EstimateRow("Muscle mass", "%.1f kg".format(Locale.US, estimate.muscleMassKg), "%.1f%%".format(Locale.US, estimate.muscleMassPercent))
                        EstimateRow("Hydration", "%.1f%%".format(Locale.US, estimate.hydrationPercent), "Protein %.1f%%".format(Locale.US, estimate.proteinPercent))
                        EstimateRow("Skeletal muscle", "%.1f%%".format(Locale.US, estimate.skeletalMusclePercent), "Subcutaneous fat %.1f%%".format(Locale.US, estimate.subcutaneousFatPercent))
                        EstimateRow("Visceral fat", "Level %d".format(Locale.US, estimate.visceralFatLevel), "Bone mass %.1f%%".format(Locale.US, estimate.boneMassPercent))
                        EstimateRow("Metabolic age", "%d years".format(Locale.US, estimate.metabolicAge), "Personalized estimate")
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete weight reading?") },
            text = { Text("This removes the reading and its HealthOS history. It will not change the original HealthifyMe data.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    scope.launch { onDelete(id) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EstimateRow(label: String, value: String, secondary: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
