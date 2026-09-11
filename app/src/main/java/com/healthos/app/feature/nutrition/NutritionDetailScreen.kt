package com.healthos.app.feature.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.core.ui.components.SectionTitle
import com.healthos.app.domain.model.NutritionEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDetailScreen(
    date: Long,
    entries: List<NutritionEntry>,
    onBack: () -> Unit,
    onDeleteDay: suspend (Long) -> Unit = {}
) {
    val dayEntries = entries.filter { dayKey(it.provenance.recordedAtMillis) == date }
    val calories = dayEntries.sumOf { it.calories ?: 0.0 }
    val protein = dayEntries.sumOf { it.proteinGrams ?: 0.0 }
    val carbs = dayEntries.sumOf { it.carbohydrateGrams ?: 0.0 }
    val fat = dayEntries.sumOf { it.fatGrams ?: 0.0 }
    val fiber = dayEntries.sumOf { it.fiberGrams ?: 0.0 }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nutrition") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (dayEntries.isNotEmpty()) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete this day")
                    }
                }
            }
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(formatDate(date), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("MyFitnessPal nutrition details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                SectionTitle("Daily totals")
                Spacer(Modifier.height(10.dp))
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Calories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${calories.roundToInt()} kcal", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Text("${protein.roundToInt()} g protein", color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat("Carbs", "${carbs.roundToInt()} g", Modifier.weight(1f))
                            Stat("Fat", "${fat.roundToInt()} g", Modifier.weight(1f))
                            Stat("Fiber", "${fiber.roundToInt()} g", Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                SectionTitle("Nutrition")
                Spacer(Modifier.height(10.dp))
                DetailCard {
                    detailRowIfPresent(dayEntries.sumOfNullable { it.sodiumMg }, "Sodium", "%.0f mg")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.potassiumMg }, "Potassium", "%.0f mg")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.cholesterolMg }, "Cholesterol", "%.0f mg")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.sugarGrams }, "Sugar", "%.1f g")
                }
            }

            item {
                SectionTitle("Fat breakdown")
                Spacer(Modifier.height(10.dp))
                DetailCard {
                    detailRowIfPresent(dayEntries.sumOfNullable { it.saturatedFatGrams }, "Saturated fat", "%.1f g")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.polyunsaturatedFatGrams }, "Polyunsaturated fat", "%.1f g")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.monounsaturatedFatGrams }, "Monounsaturated fat", "%.1f g")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.transFatGrams }, "Trans fat", "%.1f g")
                }
            }

            item {
                SectionTitle("Vitamins & minerals")
                Spacer(Modifier.height(10.dp))
                DetailCard {
                    detailRowIfPresent(dayEntries.sumOfNullable { it.vitaminAPercent }, "Vitamin A", "%.0f%%")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.vitaminCPercent }, "Vitamin C", "%.0f%%")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.calciumPercent }, "Calcium", "%.0f%%")
                    detailRowIfPresent(dayEntries.sumOfNullable { it.ironPercent }, "Iron", "%.0f%%")
                }
            }

            item { SectionTitle("Meals") }
            if (dayEntries.isEmpty()) {
                item { Text("No nutrition data for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val meals = dayEntries.groupBy { it.meal?.ifBlank { null } ?: "Other" }.toList().sortedBy { it.first }
                items(meals, key = { it.first }) { (meal, mealEntries) ->
                    DetailCard(title = meal) {
                        DetailRow("Calories", "${mealEntries.sumOf { it.calories ?: 0.0 }.roundToInt()} kcal")
                        DetailRow("Protein", "${mealEntries.sumOf { it.proteinGrams ?: 0.0 }.roundToInt()} g")
                        DetailRow("Carbohydrates", "${mealEntries.sumOf { it.carbohydrateGrams ?: 0.0 }.roundToInt()} g")
                        DetailRow("Fat", "${mealEntries.sumOf { it.fatGrams ?: 0.0 }.roundToInt()} g")
                        mealEntries.forEach { entry ->
                            entry.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Text("Source: MyFitnessPal CSV import · ${dayEntries.size} imported rows", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this day's nutrition?") },
            text = { Text("This will remove all nutrition entries for ${formatDate(date)} from HealthOS. It will not change your original MyFitnessPal data.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        onDeleteDay(date)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ColumnScope.detailRowIfPresent(value: Double?, label: String, format: String) {
    value?.let { DetailRow(label, String.format(Locale.US, format, it)) }
}

private fun Iterable<NutritionEntry>.sumOfNullable(selector: (NutritionEntry) -> Double?): Double? {
    var total = 0.0
    var found = false
    for (entry in this) selector(entry)?.let { total += it; found = true }
    return total.takeIf { found }
}

private fun dayKey(millis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun formatDate(millis: Long): String = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date(millis))
