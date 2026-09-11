package com.healthos.app.feature.fitness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.ActivityType
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Composable
fun FitnessScreen(viewModel: FitnessViewModel = viewModel(), onActivityClick: (Activity) -> Unit = {}) {
    val activities by viewModel.activities.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<ActivityType?>(null) }
    var selectedDateRange by remember { mutableStateOf(DateRange.ALL_TIME) }
    var selectedGraphMetric by remember { mutableStateOf(GraphMetric.DISTANCE) }
    val filteredActivities = remember(activities, query, selectedType, selectedDateRange) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        val cutoff = selectedDateRange.cutoffMillis()
        activities.filter { activity ->
            val searchable = "${activity.name.orEmpty()} ${activity.activityType.displayName()} ${activity.provenance.source}".lowercase(Locale.getDefault())
            (normalizedQuery.isBlank() || searchable.contains(normalizedQuery)) && (selectedType == null || activity.activityType == selectedType) && (cutoff == null || activity.provenance.recordedAtMillis >= cutoff)
        }
    }
    val runs = filteredActivities.filter { it.activityType == ActivityType.RUN }
    val totalRunDistanceKm = runs.sumOf { it.distanceMeters ?: 0.0 } / 1000.0
    val totalRunDuration = runs.sumOf { it.durationSeconds }
    val hasFilters = query.isNotBlank() || selectedType != null || selectedDateRange != DateRange.ALL_TIME

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            if (searchOpen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search activities") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
                    IconButton(onClick = { searchOpen = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Fitness", style = MaterialTheme.typography.headlineLarge)
                        Text("Your complete training history from connected and imported sources.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row { IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search activities") }; IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Default.FilterList, "Filter activities") } }
                }
            }
        }
        if (showFilters) item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Activity type", fontWeight = FontWeight.SemiBold)
                    FilterChipRow(listOf(null to "All", ActivityType.RUN to "Run", ActivityType.RIDE to "Ride", ActivityType.WALK to "Walk", ActivityType.HIKE to "Hike", ActivityType.SWIM to "Swim", ActivityType.STRENGTH to "Strength", ActivityType.WORKOUT to "Workout"), selectedType) { selectedType = it }
                    Text("Date", fontWeight = FontWeight.SemiBold)
                    FilterChipRow(DateRange.entries.map { it to it.label }, selectedDateRange) { selectedDateRange = it }
                    if (hasFilters) Text("${filteredActivities.size} activities match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { FitnessHistoryGraph(filteredActivities, selectedGraphMetric, { selectedGraphMetric = it }, onActivityClick) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SummaryCard("Runs", runs.size.toString(), Modifier.weight(1f)); SummaryCard("Distance", formatDistance(totalRunDistanceKm), Modifier.weight(1f)); SummaryCard("Time", formatDurationShort(totalRunDuration), Modifier.weight(1f)) } }
        item { Text(if (hasFilters) "Filtered activities · ${filteredActivities.size}" else "All activities · ${activities.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (filteredActivities.isEmpty()) item { Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (activities.isEmpty()) "No activities yet" else "No matching activities", fontWeight = FontWeight.Medium); Text(if (activities.isEmpty()) "Import a fitness data source from More → Data Sources to populate your history." else "Try a different search or filter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        else items(filteredActivities, key = { it.id }) { activity -> ActivityCard(activity) { onActivityClick(activity) } }
    }
}

private enum class GraphMetric(val label: String) { DISTANCE("Distance"), TIME("Time"), ELEVATION("Elevation") }

@Composable
private fun FitnessHistoryGraph(activities: List<Activity>, metric: GraphMetric, onMetricSelected: (GraphMetric) -> Unit, onActivityClick: (Activity) -> Unit) {
    val currentWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val latestActivityWeek = activities.maxByOrNull { it.provenance.recordedAtMillis }?.let { instantDate(it.provenance.recordedAtMillis).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val currentWindowStart = currentWeek.minusWeeks(11)
    val graphEndWeek = when { latestActivityWeek == null -> currentWeek; latestActivityWeek.isAfter(currentWeek) -> latestActivityWeek; latestActivityWeek.isBefore(currentWindowStart) -> latestActivityWeek; else -> currentWeek }
    val weeks = remember(graphEndWeek) { (11 downTo 0).map { graphEndWeek.minusWeeks(it.toLong()) } }
    val values = remember(activities, weeks, metric) { weeks.map { weekStart -> val weekActivities = activitiesForWeek(activities, weekStart); when (metric) { GraphMetric.DISTANCE -> weekActivities.sumOf { it.distanceMeters ?: 0.0 } / 1000.0; GraphMetric.TIME -> weekActivities.sumOf { it.durationSeconds }.toDouble() / 3600.0; GraphMetric.ELEVATION -> weekActivities.sumOf { it.elevationGainMeters ?: 0.0 } } } }
    var selectedWeekIndex by remember(graphEndWeek, metric, activities) { mutableStateOf(weeks.lastIndex) }
    val selectedWeek = weeks[selectedWeekIndex]
    val selectedWeekActivities = activitiesForWeek(activities, selectedWeek)
    val selectedValue = values[selectedWeekIndex]
    val unit = when (metric) { GraphMetric.DISTANCE -> "km"; GraphMetric.TIME -> "h"; GraphMetric.ELEVATION -> "m" }
    val maxValue = max(values.maxOrNull() ?: 0.0, 1.0)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedBarColor = primaryColor.copy(alpha = 0.38f)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = surfaceColor) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Training history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("Last 12 weeks · ${formatGraphTotal(values.sum(), metric, unit)}", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariantColor) }
                Column(horizontalAlignment = Alignment.End) { Text(formatWeekLabel(selectedWeek), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor); Text("${formatGraphValue(selectedValue, metric)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { GraphMetric.entries.forEach { option -> FilterChip(metric == option, { onMetricSelected(option) }, label = { Text(option.label) }) } }
            Canvas(Modifier.fillMaxWidth().height(220.dp).pointerInput(values, maxValue) { detectTapGestures { tapOffset -> val left = 8f; val right = size.width - 8f; val slotWidth = (right - left) / values.size; selectedWeekIndex = ((tapOffset.x - left) / slotWidth).toInt().coerceIn(0, values.lastIndex) } }) {
                val left = 8f; val right = size.width - 8f; val top = 12f; val bottom = size.height - 34f; val chartHeight = bottom - top; val slotWidth = (right - left) / values.size; val barWidth = (slotWidth * 0.54f).coerceIn(6f, 30f)
                repeat(4) { index -> val y = top + chartHeight * index / 3f; drawLine(outlineColor, Offset(left, y), Offset(right, y), 1f) }
                values.forEachIndexed { index, value -> val barHeight = if (value > 0.0) (value / maxValue).toFloat() * chartHeight else 2f; val centerX = left + slotWidth * index + slotWidth / 2f; val barTop = bottom - barHeight; val color = if (index == selectedWeekIndex) primaryColor else mutedBarColor; drawRoundRect(color, Offset(centerX - barWidth / 2f, barTop), Size(barWidth, barHeight), CornerRadius(barWidth / 2f, barWidth / 2f)); if (index == selectedWeekIndex) { drawCircle(surfaceColor, 4f, Offset(centerX, barTop)); drawCircle(color, 2.5f, Offset(centerX, barTop)) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatWeekLabel(weeks.first()), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor); Text(formatWeekLabel(weeks[weeks.size / 2]), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor); Text("Latest", style = MaterialTheme.typography.labelSmall, color = if (selectedWeekIndex == weeks.lastIndex) primaryColor else onSurfaceVariantColor) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { GraphStat("Distance", formatGraphDistance(selectedWeekActivities), onSurfaceColor, onSurfaceVariantColor); GraphStat("Time", formatDurationShort(selectedWeekActivities.sumOf { it.durationSeconds }), onSurfaceColor, onSurfaceVariantColor); GraphStat("Activities", selectedWeekActivities.size.toString(), onSurfaceColor, onSurfaceVariantColor) }
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = surfaceColor.copy(alpha = 0.55f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Week activities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(selectedWeekActivities.size.toString(), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor) }
                    if (selectedWeekActivities.isEmpty()) Text("No activities in this week", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariantColor)
                    else { selectedWeekActivities.take(5).forEach { activity -> Row(Modifier.fillMaxWidth().clickable { onActivityClick(activity) }, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(activity.name ?: activity.activityType.displayName(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Text(formatDate(activity.provenance.recordedAtMillis), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor) }; Text(formatDuration(activity.durationSeconds), style = MaterialTheme.typography.labelMedium) } }; if (selectedWeekActivities.size > 5) Text("+${selectedWeekActivities.size - 5} more in this week", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor) }
                }
            }
        }
    }
}

private fun activitiesForWeek(activities: List<Activity>, weekStart: LocalDate): List<Activity> { val weekEnd = weekStart.plusWeeks(1); return activities.filter { val date = instantDate(it.provenance.recordedAtMillis); !date.isBefore(weekStart) && date.isBefore(weekEnd) }.sortedByDescending { it.provenance.recordedAtMillis } }

@Composable private fun RowScope.GraphStat(label: String, value: String, valueColor: Color, labelColor: Color) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor); Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = valueColor) } }
private fun formatGraphDistance(activities: List<Activity>): String = String.format(Locale.US, "%.1f km", activities.sumOf { it.distanceMeters ?: 0.0 } / 1000.0)
private fun instantDate(millis: Long): LocalDate = Date(millis).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
private fun formatGraphTotal(total: Double, metric: GraphMetric, unit: String): String = "${formatGraphValue(total, metric)} $unit total"
private fun formatGraphValue(value: Double, metric: GraphMetric): String = when (metric) { GraphMetric.DISTANCE, GraphMetric.TIME -> if (value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value); GraphMetric.ELEVATION -> String.format(Locale.US, "%.0f", value) }
private fun formatWeekLabel(date: LocalDate): String = "${date.dayOfMonth} ${date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }}"
@Composable private fun <T> FilterChipRow(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelected(value) }, label = { Text(label) }) } } }
private enum class DateRange(val label: String) { ALL_TIME("All time"), LAST_30_DAYS("30 days"), LAST_90_DAYS("90 days"), LAST_YEAR("1 year"); fun cutoffMillis(): Long? = when (this) { ALL_TIME -> null; LAST_30_DAYS -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30); LAST_90_DAYS -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90); LAST_YEAR -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365) } }
@Composable private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) { Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } } }

@Composable
private fun ActivityCard(activity: Activity, onClick: () -> Unit) {
    val distanceKm = activity.distanceMeters?.div(1000.0)
    val isStrength = activity.activityType == ActivityType.STRENGTH
    val pace = if (activity.activityType == ActivityType.RUN && distanceKm != null && distanceKm > 0.0) formatPace(activity.durationSeconds / distanceKm) else null
    val routePoints = remember(activity.routePoints) { parseRoutePoints(activity.routePoints) }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(activity.name ?: activity.activityType.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(formatDate(activity.provenance.recordedAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(activity.activityType.displayName(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (routePoints.size >= 2) RoutePreview(routePoints, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
            if (isStrength) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StrengthStat("Duration", formatDuration(activity.durationSeconds), Modifier.weight(1f))
                    StrengthStat("Session", "Strength", Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { distanceKm?.let { Text(String.format(Locale.US, "%.2f km", it), fontWeight = FontWeight.Medium) }; Text(formatDuration(activity.durationSeconds), fontWeight = FontWeight.Medium); pace?.let { Text(it) }; activity.averageHeartRate?.let { Text("$it bpm") } }
                activity.elevationGainMeters?.takeIf { it > 0 }?.let { Text("${it.toInt()} m elevation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun StrengthStat(label: String, value: String, modifier: Modifier = Modifier) { Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } } }

@Composable
private fun RoutePreview(points: List<Pair<Double, Double>>, backgroundColor: Color, gridColor: Color, routeColor: Color, endpointColor: Color) {
    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
        val padding = 14f; val minLat = points.minOf { it.first }; val maxLat = points.maxOf { it.first }; val minLon = points.minOf { it.second }; val maxLon = points.maxOf { it.second }; val latSpan = (maxLat - minLat).coerceAtLeast(0.000001); val lonSpan = (maxLon - minLon).coerceAtLeast(0.000001); val scale = minOf((size.width - padding * 2f) / lonSpan.toFloat(), (size.height - padding * 2f) / latSpan.toFloat()); val contentWidth = lonSpan.toFloat() * scale; val contentHeight = latSpan.toFloat() * scale; val offsetX = (size.width - contentWidth) / 2f; val offsetY = (size.height - contentHeight) / 2f
        drawRoundRect(backgroundColor, Offset.Zero, size, CornerRadius(12f, 12f)); for (i in 1..3) { val x = size.width * i / 4f; val y = size.height * i / 4f; drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f); drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f) }; val screenPoints = points.map { (lat, lon) -> Offset(offsetX + (lon - minLon).toFloat() * scale, offsetY + (maxLat - lat).toFloat() * scale) }; screenPoints.zipWithNext().forEach { (start, end) -> drawLine(routeColor, start, end, 4.5f) }; screenPoints.firstOrNull()?.let { drawCircle(backgroundColor, 6f, it); drawCircle(routeColor, 4f, it) }; screenPoints.lastOrNull()?.let { drawCircle(backgroundColor, 6f, it); drawCircle(endpointColor, 4f, it) }
    }
}
private fun parseRoutePoints(routePoints: String?): List<Pair<Double, Double>> { if (routePoints.isNullOrBlank()) return emptyList(); return routePoints.split(';').mapNotNull { entry -> val parts = entry.split(','); if (parts.size != 2) return@mapNotNull null; val lat = parts[0].toDoubleOrNull(); val lon = parts[1].toDoubleOrNull(); if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) null else lat to lon } }
private fun ActivityType.displayName(): String = when (this) { ActivityType.RUN -> "Run"; ActivityType.RIDE -> "Ride"; ActivityType.WALK -> "Walk"; ActivityType.HIKE -> "Hike"; ActivityType.SWIM -> "Swim"; ActivityType.STRENGTH -> "Strength"; ActivityType.SOCCER -> "Football"; ActivityType.WORKOUT -> "Workout"; ActivityType.ROCK_CLIMB -> "Rock climb"; ActivityType.CANOE -> "Canoe"; ActivityType.OTHER -> "Activity" }
private fun formatDistance(km: Double): String = when { km >= 1000 -> String.format(Locale.US, "%.0fk", km / 1000.0); km >= 100 -> String.format(Locale.US, "%.0f km", km); else -> String.format(Locale.US, "%.1f km", km) }
private fun formatPace(secondsPerKm: Double): String { val totalSeconds = secondsPerKm.toInt().coerceAtLeast(0); return String.format(Locale.US, "%d:%02d/km", totalSeconds / 60, totalSeconds % 60) }
private fun formatDurationShort(seconds: Long): String { val hours = seconds / 3600; val minutes = (seconds % 3600) / 60; return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m" }
private fun formatDuration(seconds: Long): String { val hours = seconds / 3600; val minutes = (seconds % 3600) / 60; val remainingSeconds = seconds % 60; return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds) else String.format(Locale.US, "%d:%02d", minutes, remainingSeconds) }
private fun formatDate(millis: Long): String = SimpleDateFormat("d MMM yyyy · h:mm a", Locale.US).format(Date(millis))
