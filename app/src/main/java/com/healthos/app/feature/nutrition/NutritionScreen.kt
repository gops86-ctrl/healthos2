package com.healthos.app.feature.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.core.ui.components.SectionTitle
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.NutritionEntry
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private enum class NutritionRange(val label: String, val days: Int?) {
    SEVEN("7D", 7), THIRTY("30D", 30), THREE_MONTHS("3M", 90), ALL("All", null)
}

private data class DailyNutrition(
    val date: Long,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sodium: Double
)

@Composable
fun NutritionScreen(
    metrics: List<HealthMetric>,
    entries: List<NutritionEntry>,
    onMetricClick: (HealthMetric) -> Unit,
    onDayClick: (Long) -> Unit = {}
) {
    var range by remember { mutableStateOf(NutritionRange.SEVEN) }
    val days = remember(entries) {
        entries.groupBy { dayKey(it.provenance.recordedAtMillis) }
            .map { (date, rows) ->
                DailyNutrition(
                    date = date,
                    calories = rows.sumOf { it.calories ?: 0.0 },
                    protein = rows.sumOf { it.proteinGrams ?: 0.0 },
                    carbs = rows.sumOf { it.carbohydrateGrams ?: 0.0 },
                    fat = rows.sumOf { it.fatGrams ?: 0.0 },
                    fiber = rows.sumOf { it.fiberGrams ?: 0.0 },
                    sodium = rows.sumOf { it.sodiumMg ?: 0.0 }
                )
            }
            .sortedByDescending { it.date }
    }

    val cutoff = range.days?.let { System.currentTimeMillis() - it * 86_400_000L }
    val filtered = days.filter { cutoff == null || it.date >= cutoff }
    val latest = filtered.firstOrNull()
    val avg = if (filtered.isNotEmpty()) {
        DailyNutrition(
            date = 0,
            calories = filtered.map { it.calories }.average(),
            protein = filtered.map { it.protein }.average(),
            carbs = filtered.map { it.carbs }.average(),
            fat = filtered.map { it.fat }.average(),
            fiber = filtered.map { it.fiber }.average(),
            sodium = filtered.map { it.sodium }.average()
        )
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nutrition", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Your nutrition and intake at a glance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NutritionRange.values().forEach { item ->
                    FilterChip(
                        selected = range == item,
                        onClick = { range = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        if (latest != null) {
            item { SectionTitle("Latest day", formatDate(latest.date)) }
            item { NutritionChartCard(title = "Today's intake", nutrition = latest) }
        }

        if (avg != null) {
            item { SectionTitle("${range.label} daily average") }
            item { NutritionChartCard(title = "Average intake", nutrition = avg) }
        }

        item { SectionTitle("Daily history") }
        if (filtered.isEmpty()) {
            item {
                Text("No nutrition data in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(filtered, key = { it.date }) { day ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onDayClick(day.date) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                formatDate(day.date),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${day.calories.roundToInt()} kcal  •  ${day.protein.roundToInt()} g protein",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${day.carbs.roundToInt()} g carbs  •  ${day.fat.roundToInt()} g fat  •  ${day.fiber.roundToInt()} g fiber",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open nutrition details")
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionChartCard(title: String, nutrition: DailyNutrition) {
    val proteinCalories = nutrition.protein * 4.0
    val carbCalories = nutrition.carbs * 4.0
    val fatCalories = nutrition.fat * 9.0
    val totalMacroCalories = proteinCalories + carbCalories + fatCalories

    val proteinPercent = if (totalMacroCalories > 0) proteinCalories / totalMacroCalories else 0.0
    val carbPercent = if (totalMacroCalories > 0) carbCalories / totalMacroCalories else 0.0
    val fatPercent = if (totalMacroCalories > 0) fatCalories / totalMacroCalories else 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${nutrition.calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("Protein", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${nutrition.protein.roundToInt()} g", fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                MacroDonutChart(
                    protein = proteinPercent,
                    carbs = carbPercent,
                    fat = fatPercent,
                    modifier = Modifier.size(150.dp)
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    MacroLegendRow("Carbs", nutrition.carbs, carbPercent, MaterialTheme.colorScheme.primary)
                    MacroLegendRow("Protein", nutrition.protein, proteinPercent, MaterialTheme.colorScheme.secondary)
                    MacroLegendRow("Fat", nutrition.fat, fatPercent, MaterialTheme.colorScheme.tertiary)
                }
            }

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniMetric("Fiber", "${nutrition.fiber.roundToInt()} g")
                MiniMetric("Sodium", "${nutrition.sodium.roundToInt()} mg")
                MiniMetric("Macro kcal", "${totalMacroCalories.roundToInt()}")
            }
        }
    }
}

@Composable
private fun MacroLegendRow(label: String, grams: Double, fraction: Double, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = MaterialTheme.shapes.small,
            color = color
        ) {}
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${grams.roundToInt()} g", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MacroDonutChart(
    protein: Double,
    carbs: Double,
    fat: Double,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val proteinColor = MaterialTheme.colorScheme.secondary
    val carbColor = MaterialTheme.colorScheme.primary
    val fatColor = MaterialTheme.colorScheme.tertiary

    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = size.minDimension * 0.18f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            var startAngle = -90f
            listOf(
                carbs to carbColor,
                protein to proteinColor,
                fat to fatColor
            ).forEach { (fraction, color) ->
                val sweep = (fraction * 360.0).toFloat()
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("Macros", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("100%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun dayKey(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun formatDate(millis: Long) =
    SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date(millis))
