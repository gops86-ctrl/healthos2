package com.healthos.app.feature.fitness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.ActivityType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(viewModel: ActivityDetailViewModel, onBack: () -> Unit) {
    val activity by viewModel.activity.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(activity?.name ?: "Activity") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (activity != null) {
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete activity")
                    }
                }
            }
        )
    }) { padding ->
        val current = activity
        if (current == null) Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) { Text("Activity not found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else ActivityDetailContent(current, padding)
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete activity?") },
            text = { Text("This removes the activity from HealthOS only. It will not delete the original activity from its source app.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteActivity(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActivityDetailContent(activity: Activity, padding: PaddingValues) {
    val distanceKm = activity.distanceMeters?.div(1000.0)
    val displayDuration = activity.durationSeconds.takeIf { it > 0 } ?: activity.elapsedDurationSeconds ?: 0L
    val pace = if (activity.activityType == ActivityType.RUN && distanceKm != null && distanceKm > 0) formatPace(displayDuration / distanceKm) else null

    LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(activity.activityType.displayName(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(activity.name ?: "Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(formatDate(activity.provenance.recordedAtMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailRow("Duration", formatDuration(displayDuration))
                    activity.elapsedDurationSeconds?.takeIf { it != displayDuration }?.let { DetailRow("Elapsed time", formatDuration(it)) }
                    distanceKm?.let { DetailRow("Distance", String.format(Locale.US, "%.2f km", it)) }
                    pace?.let { DetailRow("Pace", it) }
                    activity.averageSpeedMps?.let { DetailRow("Average speed", String.format(Locale.US, "%.2f m/s", it)) }
                    activity.averageHeartRate?.let { DetailRow("Average heart rate", "$it bpm") }
                    activity.maxHeartRate?.let { DetailRow("Max heart rate", "$it bpm") }
                    activity.elevationGainMeters?.let { DetailRow("Elevation gain", String.format(Locale.US, "%.0f m", it)) }
                    activity.calories?.let { DetailRow("Calories", String.format(Locale.US, "%.0f kcal", it)) }
                }
            }
        }
        activity.routePoints?.let { route -> item { RouteMapCard(route) } }
        item { Text("Source: ${activity.provenance.source.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun RouteMapCard(routePoints: String) {
    val points = routePoints.split(';').mapNotNull { point ->
        val parts = point.split(',')
        if (parts.size != 2) return@mapNotNull null
        val lat = parts[0].toFloatOrNull()
        val lon = parts[1].toFloatOrNull()
        if (lat != null && lon != null) Offset(lon, lat) else null
    }
    if (points.size < 2) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("GPS route · ${points.size} points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(220.dp)) { RouteCanvas(points) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("START", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("END", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RouteCanvas(points: List<Offset>) {
    val accent = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxSize()) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val rangeX = max(maxX - minX, 0.000001f)
        val rangeY = max(maxY - minY, 0.000001f)
        val scale = minOf((size.width - 28.dp.toPx()) / rangeX, (size.height - 28.dp.toPx()) / rangeY)
        val offsetX = (size.width - rangeX * scale) / 2f
        val offsetY = (size.height - rangeY * scale) / 2f
        fun project(point: Offset): Offset = Offset(offsetX + (point.x - minX) * scale, offsetY + (maxY - point.y) * scale)
        val path = Path().apply {
            val first = project(points.first())
            moveTo(first.x, first.y)
            points.drop(1).forEach { point -> val p = project(point); lineTo(p.x, p.y) }
        }
        drawRoundRect(color = surfaceVariant, cornerRadius = CornerRadius(18.dp.toPx()))
        drawPath(path, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(accent, radius = 7.dp.toPx(), center = project(points.first()))
        drawCircle(accent, radius = 7.dp.toPx(), center = project(points.last()))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun ActivityType.displayName(): String = when (this) {
    ActivityType.RUN -> "Run"
    ActivityType.RIDE -> "Ride"
    ActivityType.WALK -> "Walk"
    ActivityType.HIKE -> "Hike"
    ActivityType.SWIM -> "Swim"
    ActivityType.STRENGTH -> "Strength"
    ActivityType.SOCCER -> "Football"
    ActivityType.WORKOUT -> "Workout"
    ActivityType.ROCK_CLIMB -> "Rock climb"
    ActivityType.CANOE -> "Canoe"
    ActivityType.OTHER -> "Activity"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds) else String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}

private fun formatPace(secondsPerKm: Double): String {
    val totalSeconds = secondsPerKm.toInt().coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d/km", totalSeconds / 60, totalSeconds % 60)
}

private fun formatDate(millis: Long): String = SimpleDateFormat("d MMM yyyy · h:mm a", Locale.US).format(Date(millis))
