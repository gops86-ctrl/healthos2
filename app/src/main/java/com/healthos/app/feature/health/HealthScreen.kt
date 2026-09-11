package com.healthos.app.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthos.app.core.ui.components.LabResultRow
import com.healthos.app.core.ui.components.MetricCard
import com.healthos.app.core.ui.components.SectionTitle
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.BodyMeasurementType
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.LabResult
import com.healthos.app.domain.model.MetricType
import java.util.Locale

@Composable
fun HealthScreen(
    metrics: List<HealthMetric>,
    bodyMeasurements: List<BodyMeasurement>,
    labResults: List<LabResult>,
    onMetricClick: (HealthMetric) -> Unit,
    onWeightClick: () -> Unit,
    onLabClick: (LabResult) -> Unit
) {
    val latestWeight = bodyMeasurements.firstOrNull { it.measurementType == BodyMeasurementType.WEIGHT }
    val weightMetric = metrics.firstOrNull { it.type == MetricType.WEIGHT }?.let { metric -> latestWeight?.let { weight -> metric.copy(value = "%.1f kg".format(Locale.US, weight.value), recordedAtMillis = weight.provenance.recordedAtMillis) } }
    val healthMetrics = metrics.filter { it.type in setOf(MetricType.RESTING_HR, MetricType.HRV, MetricType.SLEEP, MetricType.STRESS, MetricType.VO2_MAX, MetricType.STEPS) }.toMutableList().apply { weightMetric?.let(::add) }
    val visibleLabs = labResults.filterNot { it.provenance.sourceRecordId == "demo-hba1c" }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Health", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface); Text("Recovery, body, and laboratory measurements stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (healthMetrics.isNotEmpty()) {
            item { SectionTitle("Recovery") }
            items(healthMetrics, key = { "metric-${it.type.name}" }) { metric -> MetricCard(metric, Modifier.fillMaxWidth()) { if (metric.type == MetricType.WEIGHT) onWeightClick() else onMetricClick(metric) } }
        }
        item { SectionTitle("Labs") }
        if (visibleLabs.isEmpty()) item { Text("No lab results recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(visibleLabs, key = { "lab-${it.id}" }) { result -> LabResultRow(result = result, onClick = { onLabClick(result) }) }
    }
}
