package com.healthos.app.feature.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.data.local.UserProfile
import com.healthos.app.domain.model.ActivityType
import com.healthos.app.domain.model.HealthMetric
import java.util.Locale

val LocalWhatChangedClick = compositionLocalOf<(() -> Unit)?> { null }
val LocalProfileClick = compositionLocalOf<(() -> Unit)?> { null }
val LocalUserProfile = compositionLocalOf<UserProfile> { UserProfile() }

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    val whatChangedClick = LocalWhatChangedClick.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        trailing?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = if (title == "Trends" && whatChangedClick != null) Modifier.clickable(onClick = whatChangedClick) else Modifier) }
    }
}

@Composable
fun MiniSparkline(points: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(gridColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: return@Canvas
        val max = points.maxOrNull() ?: return@Canvas
        val range = (max - min).coerceAtLeast(0.001f)
        val step = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        val lastX = (points.size - 1) * step
        val lastY = size.height - ((points.last() - min) / range) * size.height
        drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
    }
}

@Composable
fun WeightCard(weight: HealthMetric?, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text("Weight", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(weight?.let { formatWeight(it.value) } ?: "No data", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatWeight(value: String): String {
    val number = value.replace("kg", "", ignoreCase = true).trim().replace(',', '.').toDoubleOrNull()
    return number?.let { "%.2f kg".format(Locale.US, it) } ?: value
}

fun ActivityType.displayName(): String = when (this) {
    ActivityType.RUN -> "Run"
    ActivityType.RIDE -> "Ride"
    ActivityType.WALK -> "Walk"
    ActivityType.HIKE -> "Hike"
    ActivityType.SWIM -> "Swim"
    ActivityType.STRENGTH -> "Strength"
    ActivityType.SOCCER -> "Soccer"
    ActivityType.WORKOUT -> "Workout"
    ActivityType.ROCK_CLIMB -> "Rock climb"
    ActivityType.CANOE -> "Canoe"
    ActivityType.OTHER -> "Other"
}
