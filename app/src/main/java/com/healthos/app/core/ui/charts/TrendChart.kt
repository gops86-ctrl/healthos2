package com.healthos.app.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrendPoint(val date: LocalDate, val label: String, val value: Float)

private enum class ChartRange(val label: String) {
    DAYS_7("7D"), DAYS_30("30D"), MONTHS_3("3M"), YEAR_1("1Y"), ALL("All")
}

private data class DisplayPoint(
    val date: LocalDate,
    val label: String,
    val value: Float,
    val detailLabel: String,
    val sourcePoints: List<TrendPoint>
)

@Composable
fun TrendChart(title: String, value: String, dataPoints: List<TrendPoint>) {
    var selectedRange by remember { mutableStateOf(ChartRange.DAYS_7) }
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    val rangeStart = when (selectedRange) {
        ChartRange.DAYS_7 -> today.minusDays(6)
        ChartRange.DAYS_30 -> today.minusDays(29)
        ChartRange.MONTHS_3 -> today.minusMonths(3).plusDays(1)
        ChartRange.YEAR_1 -> today.minusYears(1).plusDays(1)
        ChartRange.ALL -> null
    }
    val filtered = dataPoints.filter { rangeStart == null || it.date >= rangeStart }.sortedBy { it.date }
    val displayPoints = remember(filtered, selectedRange) { aggregatePoints(filtered, selectedRange) }

    LaunchedEffect(selectedRange) { expandedDate = null }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartRange.values().forEach { range ->
                    FilterChip(selected = selectedRange == range, onClick = { selectedRange = range }, label = { Text(range.label) })
                }
            }
            if (displayPoints.size < 2) {
                Spacer(Modifier.height(16.dp))
                Text("Historical samples will appear here as data is imported.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val values = displayPoints.map { it.value }
                val minimum = values.minOrNull() ?: 0f
                val maximum = values.maxOrNull() ?: 1f
                val rawRange = maximum - minimum
                val step = when {
                    rawRange <= 0f -> 1f
                    rawRange <= 2f -> 0.5f
                    rawRange <= 10f -> 2f
                    rawRange <= 25f -> 5f
                    rawRange <= 50f -> 10f
                    else -> kotlin.math.ceil(rawRange / 5f / 10f) * 10f
                }
                val chartMin = kotlin.math.floor(minimum / step) * step
                val chartMax = kotlin.math.ceil(maximum / step) * step
                val chartRange = (chartMax - chartMin).takeIf { it > 0f } ?: step
                val labels = listOf(chartMax, chartMin + chartRange / 2f, chartMin)

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(48.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        labels.forEach { label -> Text(formatAxis(label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight()) {
                        val horizontalStep = size.width / (displayPoints.size - 1).coerceAtLeast(1)
                        fun point(index: Int): Offset {
                            val x = horizontalStep * index
                            val normalized = ((displayPoints[index].value - chartMin) / chartRange).coerceIn(0f, 1f)
                            val y = size.height - normalized * (size.height - 16.dp.toPx()) - 8.dp.toPx()
                            return Offset(x, y)
                        }
                        for (fraction in 0..2) {
                            val y = 8.dp.toPx() + (size.height - 16.dp.toPx()) * fraction / 2f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                        val path = Path().apply {
                            displayPoints.indices.forEach { index ->
                                val p = point(index)
                                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                        }
                        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        displayPoints.indices.forEach { index ->
                            val p = point(index)
                            drawCircle(lineColor, radius = 4.dp.toPx(), center = p)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(displayPoints.first().label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (displayPoints.size > 2) Text(displayPoints[displayPoints.size / 2].label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayPoints.last().label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when (selectedRange) {
                        ChartRange.DAYS_7, ChartRange.DAYS_30 -> "${displayPoints.size} recorded days"
                        ChartRange.MONTHS_3, ChartRange.YEAR_1 -> "${displayPoints.size} weekly averages"
                        ChartRange.ALL -> "${displayPoints.size} monthly averages"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    when (selectedRange) {
                        ChartRange.DAYS_7, ChartRange.DAYS_30 -> "Recorded values"
                        ChartRange.MONTHS_3, ChartRange.YEAR_1 -> "Weekly averages"
                        ChartRange.ALL -> "Monthly averages"
                    },
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                displayPoints.asReversed().take(12).forEach { point ->
                    val expanded = expandedDate == point.date
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            expandedDate = if (expanded) null else point.date
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(point.detailLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    if (selectedRange != ChartRange.DAYS_7 && selectedRange != ChartRange.DAYS_30) {
                                        Text("Average · tap to see recorded values", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("Recorded · tap for details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(formatValue(point.value), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            if (expanded) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                point.sourcePoints.asReversed().forEach { sample ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(sample.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatValue(sample.value), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

private fun aggregatePoints(points: List<TrendPoint>, range: ChartRange): List<DisplayPoint> {
    if (points.isEmpty()) return emptyList()
    return when (range) {
        ChartRange.DAYS_7, ChartRange.DAYS_30 -> points.map { point ->
            DisplayPoint(point.date, point.label, point.value, point.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), listOf(point))
        }
        ChartRange.MONTHS_3, ChartRange.YEAR_1 -> points.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap().map { (week, values) ->
                val average = values.map { it.value }.average().toFloat()
                val end = values.maxOf { it.date }
                DisplayPoint(week, week.format(DateTimeFormatter.ofPattern("d MMM")), average, "${week.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", values.sortedBy { it.date })
            }
        ChartRange.ALL -> points.groupBy { it.date.withDayOfMonth(1) }
            .toSortedMap().map { (month, values) ->
                val average = values.map { it.value }.average().toFloat()
                DisplayPoint(month, month.format(DateTimeFormatter.ofPattern("MMM yy")), average, month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), values.sortedBy { it.date })
            }
    }
}

private fun formatAxis(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
private fun formatValue(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
