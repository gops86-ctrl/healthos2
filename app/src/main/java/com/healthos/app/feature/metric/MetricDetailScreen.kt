package com.healthos.app.feature.metric

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.core.ui.charts.TrendChart
import com.healthos.app.core.ui.charts.TrendPoint
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailScreen(metric: HealthMetric, viewModel: MetricDetailViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    val trend = history.mapNotNull { sample ->
        metricTrendValue(sample.type, sample.value)?.let { value ->
            val date = Instant.ofEpochMilli(sample.recordedAtMillis).atZone(zone).toLocalDate()
            TrendPoint(date, date.format(formatter), value)
        }
    }.sortedBy { it.date }.distinctBy { it.date }

    Scaffold(topBar = { TopAppBar(title = { Text(metric.type.label) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(metric.value, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Recorded via ${metric.source.label} on ${java.text.DateFormat.getDateInstance().format(Date(metric.recordedAtMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TrendChart(metric.type.label, metric.value, trend)
        }
    }
}

private fun metricTrendValue(type: MetricType, value: String): Float? {
    val normalized = value.trim()
    return when (type) {
        MetricType.SLEEP -> {
            val hours = Regex("(\\d+(?:\\.\\d+)?)\\s*h").find(normalized)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val minutes = Regex("(\\d+(?:\\.\\d+)?)\\s*m").find(normalized)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val totalMinutes = if (hours > 0f || minutes > 0f) hours * 60f + minutes else normalized.toFloatOrNull() ?: return null
            totalMinutes / 60f
        }
        MetricType.RESTING_HR, MetricType.HRV, MetricType.VO2_MAX -> normalized.substringBefore(' ').toFloatOrNull()
        else -> normalized.substringBefore(' ').replace(",", "").toFloatOrNull()
    }
}
