package com.healthos.app.feature.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.model.NutritionEntry
import com.healthos.app.domain.model.Activity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private enum class ChangeRange(val label: String, val days: Int?) {
    DAYS_7("7D", 7), DAYS_30("30D", 30), ALL("All", null)
}

private enum class ChangeStatus { IMPROVED, DECLINED, STABLE, CHANGED }

private data class ChangeItem(
    val title: String,
    val current: String,
    val baseline: String,
    val delta: String,
    val status: ChangeStatus,
    val metric: HealthMetric? = null,
    val neutral: Boolean = false
)

@Composable
fun WhatChangedScreen(
    viewModel: OverviewViewModel,
    onBack: () -> Unit,
    onMetricClick: (HealthMetric) -> Unit,
    onWeightClick: () -> Unit,
    onNutritionClick: () -> Unit
) {
    val metrics by viewModel.metrics.collectAsState()
    val nutritionEntries by viewModel.nutritionEntries.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val rhr by viewModel.restingHrHistory.collectAsState()
    val hrv by viewModel.hrvHistory.collectAsState()
    val sleep by viewModel.sleepHistory.collectAsState()
    val vo2 by viewModel.vo2History.collectAsState()
    val steps by viewModel.stepsHistory.collectAsState()
    var selectedRange by remember { mutableStateOf(ChangeRange.DAYS_7) }

    val items = buildChangeItems(
        range = selectedRange,
        metrics = metrics,
        nutritionEntries = nutritionEntries,
        activities = activities,
        weightHistory = weightHistory,
        rhr = rhr,
        hrv = hrv,
        sleep = sleep,
        vo2 = vo2,
        steps = steps
    )
    val directional = items.filterNot { it.neutral }
    val improved = directional.count { it.status == ChangeStatus.IMPROVED }
    val declined = directional.count { it.status == ChangeStatus.DECLINED }
    val stable = directional.count { it.status == ChangeStatus.STABLE }
    val changed = items.count { it.neutral && it.status == ChangeStatus.CHANGED }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("What Changed", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("Compared with the previous period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChangeRange.values().forEach { range ->
                    FilterChip(selected = selectedRange == range, onClick = { selectedRange = range }, label = { Text(range.label) })
                }
            }
        }

        item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryStat(improved.toString(), "improved", MaterialTheme.colorScheme.primary)
                    SummaryStat(stable.toString(), "stable", MaterialTheme.colorScheme.onSurfaceVariant)
                    SummaryStat((declined + changed).toString(), "changed", MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Text("Key changes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("A simple comparison of your stored data — no health score or guesswork.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (items.isEmpty()) {
            item {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Text("Not enough history yet to calculate changes.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items.forEach { item ->
                item {
                    ChangeRow(item) {
                        when {
                            item.metric != null -> onMetricClick(item.metric)
                            item.title == "Weight" -> onWeightClick()
                            item.title == "Calories" || item.title == "Protein" -> onNutritionClick()
                        }
                    }
                }
            }
        }

        item {
            ActivityChangeCard(activities, selectedRange)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChangeRow(item: ChangeItem, onClick: () -> Unit) {
    val clickable = item.metric != null || item.title == "Weight" || item.title == "Calories" || item.title == "Protein"
    val deltaColor = when (item.status) {
        ChangeStatus.IMPROVED -> MaterialTheme.colorScheme.primary
        ChangeStatus.DECLINED -> MaterialTheme.colorScheme.error
        ChangeStatus.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        ChangeStatus.CHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp)) {
                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(item.current, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(item.delta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = deltaColor)
                Text("vs ${item.baseline}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MiniSparkline(changeSparkline(item), Modifier.width(74.dp).height(32.dp))
        }
    }
}

@Composable
private fun ActivityChangeCard(activities: List<Activity>, range: ChangeRange) {
    val end = LocalDate.now()
    val currentStart = range.days?.let { end.minusDays((it - 1).toLong()) } ?: end.minusDays(29)
    val previousEnd = currentStart.minusDays(1)
    val previousStart = range.days?.let { previousEnd.minusDays((it - 1).toLong()) } ?: previousEnd.minusDays(29)
    val current = activities.filter { localDate(it.provenance.recordedAtMillis) in currentStart..end }
    val previous = activities.filter { localDate(it.provenance.recordedAtMillis) in previousStart..previousEnd }
    val distanceCurrent = current.sumOf { it.distanceMeters ?: 0.0 } / 1000.0
    val distancePrevious = previous.sumOf { it.distanceMeters ?: 0.0 } / 1000.0
    val durationCurrent = current.sumOf { it.durationSeconds ?: 0L }
    val durationPrevious = previous.sumOf { it.durationSeconds ?: 0L }
    val countDelta = current.size - previous.size
    val distanceDelta = distanceCurrent - distancePrevious
    val durationDelta = durationCurrent - durationPrevious

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("This period vs previous period", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ActivityStat("${current.size}", "activities", formatSignedInt(countDelta))
                ActivityStat("%.1f km".format(Locale.US, distanceCurrent), "distance", formatSigned(distanceDelta, "km"))
                ActivityStat(formatDuration(durationCurrent), "time", formatDurationDelta(durationDelta))
            }
        }
    }
}

@Composable
private fun ActivityStat(value: String, label: String, delta: String) {
    Column(Modifier.width(96.dp)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(delta, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}

private fun buildChangeItems(
    range: ChangeRange,
    metrics: List<HealthMetric>,
    nutritionEntries: List<NutritionEntry>,
    activities: List<Activity>,
    weightHistory: List<HealthMetric>,
    rhr: List<HealthMetric>,
    hrv: List<HealthMetric>,
    sleep: List<HealthMetric>,
    vo2: List<HealthMetric>,
    steps: List<HealthMetric>
): List<ChangeItem> {
    val result = mutableListOf<ChangeItem>()
    addMetricChange(result, "Resting HR", rhr, range, "bpm", higherIsBetter = false, metrics.firstOrNull { it.type == MetricType.RESTING_HR })
    addMetricChange(result, "Sleep", sleep, range, "sleep", higherIsBetter = true, metrics.firstOrNull { it.type == MetricType.SLEEP })
    addMetricChange(result, "HRV", hrv, range, "ms", higherIsBetter = true, metrics.firstOrNull { it.type == MetricType.HRV })
    addMetricChange(result, "VO₂ Max", vo2, range, "vo2", higherIsBetter = true, metrics.firstOrNull { it.type == MetricType.VO2_MAX })
    addMetricChange(result, "Steps", steps, range, "steps", higherIsBetter = true, metrics.firstOrNull { it.type == MetricType.STEPS })
    addMetricChange(result, "Weight", weightHistory, range, "kg", higherIsBetter = true, null, neutral = true)

    val nutrition = nutritionChange(nutritionEntries, range)
    nutrition.first?.let { (current, previous) ->
        result += ChangeItem("Calories", formatCalories(current), formatCalories(previous), formatSigned(current - previous, "kcal"), statusForNeutral(current, previous), null, true)
    }
    nutrition.second?.let { (current, previous) ->
        result += ChangeItem("Protein", formatGrams(current), formatGrams(previous), formatSigned(current - previous, "g"), statusForNeutral(current, previous), null, true)
    }
    return result
}

private fun addMetricChange(
    output: MutableList<ChangeItem>,
    title: String,
    history: List<HealthMetric>,
    range: ChangeRange,
    kind: String,
    higherIsBetter: Boolean,
    metric: HealthMetric?,
    neutral: Boolean = false
) {
    val split = splitPeriods(history.mapNotNull { parseMetricValue(it.value, kind)?.let { value -> localDate(it.recordedAtMillis) to value } }, range) ?: return
    val current = split.first
    val previous = split.second
    val delta = current - previous
    val status = if (neutral) statusForNeutral(current, previous) else statusForDirection(delta, higherIsBetter)
    output += ChangeItem(title, formatMetric(current, kind), formatMetric(previous, kind), formatMetricDelta(delta, kind), status, metric, neutral)
}

private fun splitPeriods(values: List<Pair<LocalDate, Double>>, range: ChangeRange): Pair<Double, Double>? {
    if (values.isEmpty()) return null
    val end = LocalDate.now()
    val days = range.days ?: 30
    val currentStart = end.minusDays((days - 1).toLong())
    val previousEnd = currentStart.minusDays(1)
    val previousStart = previousEnd.minusDays((days - 1).toLong())
    val currentValues = values.filter { it.first in currentStart..end }.map { it.second }
    val previousValues = values.filter { it.first in previousStart..previousEnd }.map { it.second }
    if (range == ChangeRange.ALL) {
        val sorted = values.sortedBy { it.first }
        if (sorted.size < 2) return null
        val splitIndex = sorted.size / 2
        val first = sorted.take(splitIndex).map { it.second }
        val second = sorted.drop(splitIndex).map { it.second }
        return second.average() to first.average()
    }
    if (currentValues.isEmpty() || previousValues.isEmpty()) return null
    return currentValues.average() to previousValues.average()
}

private fun nutritionChange(entries: List<NutritionEntry>, range: ChangeRange): Pair<Pair<Double, Double>?, Pair<Double, Double>?> {
    val end = LocalDate.now()
    val days = range.days ?: 30
    val currentStart = end.minusDays((days - 1).toLong())
    val previousEnd = currentStart.minusDays(1)
    val previousStart = previousEnd.minusDays((days - 1).toLong())
    val daily = entries.groupBy { localDate(it.provenance.recordedAtMillis) }
    val allDays = daily.keys.sorted()
    fun average(selector: (NutritionEntry) -> Double?): Pair<Double, Double>? {
        if (range == ChangeRange.ALL) {
            if (allDays.size < 2) return null
            val split = allDays.size / 2
            val first = allDays.take(split).flatMap { daily[it].orEmpty() }.mapNotNull(selector)
            val second = allDays.drop(split).flatMap { daily[it].orEmpty() }.mapNotNull(selector)
            if (first.isEmpty() || second.isEmpty()) return null
            return second.average() to first.average()
        }
        val current = daily.filterKeys { it in currentStart..end }.values.flatten().mapNotNull(selector)
        val previous = daily.filterKeys { it in previousStart..previousEnd }.values.flatten().mapNotNull(selector)
        if (current.isEmpty() || previous.isEmpty()) return null
        return current.average() to previous.average()
    }
    return average { it.calories } to average { it.proteinGrams }
}

private fun statusForDirection(delta: Double, higherIsBetter: Boolean): ChangeStatus {
    if (abs(delta) < 0.01) return ChangeStatus.STABLE
    return if ((delta > 0) == higherIsBetter) ChangeStatus.IMPROVED else ChangeStatus.DECLINED
}

private fun statusForNeutral(current: Double, previous: Double): ChangeStatus = if (abs(current - previous) < 0.01) ChangeStatus.STABLE else ChangeStatus.CHANGED

private fun parseMetricValue(value: String, kind: String): Double? {
    if (kind == "sleep") {
        val hours = Regex("(\\d+(?:\\.\\d+)?)\\s*h").find(value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val minutes = Regex("(\\d+)\\s*m").find(value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return if (hours > 0 || minutes > 0) hours + minutes / 60.0 else value.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
    }
    return value.replace(",", ".").replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()
}

private fun formatMetric(value: Double, kind: String): String = when (kind) {
    "sleep" -> formatSleep(value)
    "vo2" -> "%.1f".format(Locale.US, value)
    "steps" -> "%,.0f".format(Locale.US, value)
    "kg" -> "%.2f kg".format(Locale.US, value)
    else -> "%.0f %s".format(Locale.US, value, if (kind == "ms") "ms" else "bpm")
}

private fun formatMetricDelta(value: Double, kind: String): String = when (kind) {
    "sleep" -> signedSleep(value)
    "vo2" -> formatSigned(value, "")
    "steps" -> formatSigned(value, "")
    "kg" -> formatSigned(value, "kg")
    else -> formatSigned(value, if (kind == "ms") "ms" else "bpm")
}

private fun changeSparkline(item: ChangeItem): List<Float> {
    val current = parseMetricValue(item.current, when (item.title) {
        "Sleep" -> "sleep"
        "VO₂ Max" -> "vo2"
        "Weight" -> "kg"
        "HRV" -> "ms"
        "Steps" -> "steps"
        else -> "bpm"
    }) ?: 0.0
    val baseline = parseMetricValue(item.baseline, when (item.title) {
        "Sleep" -> "sleep"
        "VO₂ Max" -> "vo2"
        "Weight" -> "kg"
        "HRV" -> "ms"
        "Steps" -> "steps"
        else -> "bpm"
    }) ?: current
    return listOf(baseline.toFloat(), current.toFloat())
}

private fun formatSleep(hours: Double): String {
    val totalMinutes = (hours * 60).toInt()
    return "${totalMinutes / 60}h ${totalMinutes % 60}m"
}

private fun signedSleep(hours: Double): String {
    val totalMinutes = kotlin.math.round(hours * 60).toInt()
    val sign = if (totalMinutes >= 0) "↑ " else "↓ "
    return "$sign${abs(totalMinutes) / 60}h ${abs(totalMinutes) % 60}m"
}

private fun formatSigned(value: Double, unit: String): String {
    val sign = if (value >= 0) "↑ " else "↓ "
    val number = if (abs(value) >= 10) "%.0f".format(Locale.US, abs(value)) else "%.1f".format(Locale.US, abs(value))
    return "$sign$number${if (unit.isNotBlank()) " $unit" else ""}"
}

private fun formatSignedInt(value: Int): String = if (value >= 0) "↑ $value" else "↓ ${abs(value)}"

private fun formatDuration(seconds: Long): String = "${seconds / 3600}h ${(seconds % 3600) / 60}m"

private fun formatDurationDelta(seconds: Long): String = if (seconds == 0L) "→ 0m" else "${if (seconds > 0) "↑" else "↓"} ${abs(seconds) / 3600}h ${(abs(seconds) % 3600) / 60}m"

private fun formatCalories(value: Double): String = "${value.toInt()} kcal"
private fun formatGrams(value: Double): String = "${value.toInt()} g"

private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
