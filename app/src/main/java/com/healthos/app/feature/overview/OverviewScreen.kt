package com.healthos.app.feature.overview

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.model.NutritionEntry
import com.healthos.app.domain.model.StrengthWorkout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OverviewScreen(
    onMetricClick: (HealthMetric) -> Unit,
    onWeightClick: () -> Unit,
    onNutritionClick: () -> Unit,
    onActivityClick: (Activity) -> Unit,
    onWorkoutClick: (StrengthWorkout) -> Unit,
    viewModel: OverviewViewModel
) {
    val metrics by viewModel.metrics.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val workouts by viewModel.workouts.collectAsState()
    val nutritionEntries by viewModel.nutritionEntries.collectAsState()
    val labs by viewModel.labResults.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val hrv by viewModel.hrvHistory.collectAsState()
    val rhr by viewModel.restingHrHistory.collectAsState()
    val sleep by viewModel.sleepHistory.collectAsState()
    val vo2 by viewModel.vo2History.collectAsState()
    val steps by viewModel.stepsHistory.collectAsState()
    val profile = LocalUserProfile.current
    val profileClick = LocalProfileClick.current

    val m = metrics.associateBy { it.type }
    val latestWeight = weightHistory.maxByOrNull { it.recordedAtMillis }
    val nutritionStart = LocalDate.now().minusDays(6)
    val recentNutrition = nutritionEntries.filter { localDate(it.provenance.recordedAtMillis) >= nutritionStart }
    val caloriesAverage = dailyAverage(recentNutrition) { it.calories }
    val proteinAverage = dailyAverage(recentNutrition) { it.proteinGrams }
    val macroCalories = MacroCalories(
        protein = recentNutrition.mapNotNull { it.proteinGrams }.sum() * 4.0,
        carbs = recentNutrition.mapNotNull { it.carbohydrateGrams }.sum() * 4.0,
        fat = recentNutrition.mapNotNull { it.fatGrams }.sum() * 9.0
    )
    val recentActivities = activities.sortedByDescending { it.provenance.recordedAtMillis }.take(2)
    val recentWorkouts = workouts.sortedByDescending { it.provenance.recordedAtMillis }.take(2)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Overview",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (profileClick != null) {
                    Surface(
                        modifier = Modifier.size(28.dp).clip(MaterialTheme.shapes.extraLarge).clickable(onClick = profileClick),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (profile.photoUri.isNullOrBlank()) {
                            Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            AsyncImage(
                                model = Uri.parse(profile.photoUri),
                                contentDescription = "Profile photo",
                                modifier = Modifier.size(28.dp).clip(MaterialTheme.shapes.extraLarge),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            Text("Your health, training and nutrition at a glance", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            SectionHeader("Today")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetric(m[MetricType.RESTING_HR], Modifier.weight(1f), onMetricClick)
                CompactMetric(m[MetricType.SLEEP], Modifier.weight(1f), onMetricClick)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightCard(latestWeight, Modifier.weight(1f).fillMaxHeight(), onWeightClick)
                CaloriesCard(caloriesAverage, m[MetricType.CALORIES]?.value, proteinAverage, macroCalories, Modifier.weight(1f).fillMaxHeight(), onNutritionClick)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetric(m[MetricType.VO2_MAX], Modifier.weight(1f), onMetricClick)
                CompactMetric(m[MetricType.STEPS], Modifier.weight(1f), onMetricClick)
            }
        }

        item {
            SectionHeader("Trends", "7D · tap for details")
            Spacer(Modifier.height(8.dp))
            CompactTrend("Resting HR", rhr, m[MetricType.RESTING_HR], onMetricClick)
            CompactTrend("Sleep", sleep, m[MetricType.SLEEP], onMetricClick)
            CompactTrend("VO₂ Max", vo2, m[MetricType.VO2_MAX], onMetricClick)
            CompactTrend("Steps", steps, m[MetricType.STEPS], onMetricClick)
            latestWeight?.let { CompactTrend("Weight", weightHistory, null, onMetricClick, onWeightClick) }
        }

        item { ActivityTrend(activities, onActivityClick) }

        item {
            SectionHeader("Latest activities")
            Spacer(Modifier.height(8.dp))
            val hasAnything = recentActivities.isNotEmpty() || recentWorkouts.isNotEmpty()
            if (!hasAnything) EmptyCard("No activities imported yet")
            else {
                recentActivities.forEach { ActivitySummary(it) { onActivityClick(it) } }
                recentWorkouts.forEach { WorkoutSummary(it) { onWorkoutClick(it) } }
            }
        }

        item {
            SectionHeader("Latest labs")
            Spacer(Modifier.height(8.dp))
            if (labs.isEmpty()) EmptyCard("No lab results yet")
            else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column {
                    labs.take(3).forEachIndexed { index, lab ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lab.testName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(lab.provenance.recordedAtMillis).atZone(ZoneId.systemDefault())), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${lab.value} ${lab.unit}".trim(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        if (index < minOf(labs.size, 3) - 1) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightCard(weight: BodyMeasurement?, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text("Weight", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(weight?.let { "%.2f kg".format(Locale.US, it.value) } ?: "No data", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class MacroCalories(val protein: Double, val carbs: Double, val fat: Double) {
    val total: Double get() = protein + carbs + fat
}

private fun dailyAverage(entries: List<NutritionEntry>, selector: (NutritionEntry) -> Double?): Double? {
    val dailyTotals = entries.groupBy { localDate(it.provenance.recordedAtMillis) }
        .mapValues { (_, dayEntries) -> dayEntries.mapNotNull(selector).sum().takeIf { dayEntries.any { selector(it) != null } } }
        .values.mapNotNull { it }
    return dailyTotals.takeIf { it.isNotEmpty() }?.average()
}

@Composable
private fun CompactMetric(metric: HealthMetric?, modifier: Modifier, onClick: (HealthMetric) -> Unit) {
    if (metric == null) return
    Surface(modifier.clickable { onClick(metric) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(metric.type.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(metric.value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CaloriesCard(calories: Double?, fallback: String?, protein: Double?, macro: MacroCalories, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MacroDonut(macro, Modifier.size(36.dp))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text("Calories", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(calories?.let { "${it.toInt()} kcal" } ?: fallback ?: "No data", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(protein?.let { "${it.toInt()} g protein" } ?: "7-day avg", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MacroDonut(macro: MacroCalories, modifier: Modifier) {
    val total = macro.total
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        if (total <= 0.0) return@Canvas
        val stroke = 6.dp.toPx()
        var start = -90f
        listOf(macro.protein to primary, macro.carbs to secondary, macro.fat to tertiary).forEach { (value, color) ->
            val sweep = (value / total * 360.0).toFloat()
            drawArc(color, start, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun CompactTrend(title: String, history: List<HealthMetric>, metric: HealthMetric?, onMetricClick: (HealthMetric) -> Unit, onClickOverride: (() -> Unit)? = null) {
    if (history.isEmpty()) return
    val latest = metric ?: history.maxByOrNull { it.recordedAtMillis } ?: return
    val points = history.takeLast(7).mapNotNull { parseNumber(it.value) }
    Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { (onClickOverride ?: { onMetricClick(latest) })() }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(78.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(latest.value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MiniSparkline(points, Modifier.weight(1f).height(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(latest.delta, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ActivityTrend(activities: List<Activity>, onActivityClick: (Activity) -> Unit) {
    val start = LocalDate.now().minusDays(6)
    val days = (0L..6L).map { start.plusDays(it) }
    val counts = days.map { day -> activities.count { localDate(it.provenance.recordedAtMillis) == day } }
    val distances = days.map { day -> activities.filter { localDate(it.provenance.recordedAtMillis) == day }.sumOf { it.distanceMeters ?: 0.0 } / 1000.0 }
    val totalDistance = distances.sum()
    val totalActivities = counts.sum()
    val latest = activities.maxByOrNull { it.provenance.recordedAtMillis }

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Activity trend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Last 7 days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalActivities activities", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("%.1f km".format(Locale.US, totalDistance), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ActivityBars(counts, Modifier.fillMaxWidth().height(72.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(days.first().dayOfMonth.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(days.last().dayOfMonth.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            latest?.let { activity ->
                Text("Latest: ${activity.name ?: activity.activityType.displayName()} · ${formatDuration(activity.durationSeconds)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onActivityClick(activity) })
            }
        }
    }
}

@Composable
private fun ActivityBars(values: List<Int>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = size.width / values.size
        val barWidth = (slot * 0.55f).coerceAtLeast(5f)
        drawLine(outline, androidx.compose.ui.geometry.Offset(0f, size.height - 2f), androidx.compose.ui.geometry.Offset(size.width, size.height - 2f), 1f)
        values.forEachIndexed { index, value ->
            val height = if (value == 0) 2f else (value.toFloat() / max) * (size.height - 8f)
            val x = slot * index + slot / 2f
            drawRoundRect(line, androidx.compose.ui.geometry.Offset(x - barWidth / 2f, size.height - height), androidx.compose.ui.geometry.Size(barWidth, height), androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f))
        }
    }
}

@Composable
private fun ActivitySummary(activity: Activity, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.name ?: activity.activityType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(activitySummary(activity), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WorkoutSummary(workout: StrengthWorkout, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(workout.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(workout.durationSeconds?.let(::formatDuration) ?: "Strength workout", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun parseNumber(value: String): Float? = value.replace(",", ".").replace(Regex("[^0-9.\\-]"), "").toFloatOrNull()

private fun formatDuration(seconds: Long?): String {
    if (seconds == null) return ""
    val minutes = seconds / 60
    val remaining = seconds % 60
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m ${remaining}s"
}

private fun activitySummary(activity: Activity): String {
    val distance = activity.distanceMeters?.let { "%.2f km".format(Locale.US, it / 1000.0) }
    val duration = formatDuration(activity.durationSeconds)
    return listOfNotNull(distance, duration.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { activity.activityType.name }
}