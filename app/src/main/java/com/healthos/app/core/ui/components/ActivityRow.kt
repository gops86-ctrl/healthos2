package com.healthos.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.ActivityType
import java.text.DateFormat
import java.util.Date

@Composable
fun ActivityRow(activity: Activity, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(activity.name ?: activity.activityType.displayLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    DateFormat.getDateInstance().format(Date(activity.provenance.recordedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(activity.durationSeconds), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                activity.distanceMeters?.let {
                    Text(formatDistance(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDistance(meters: Double): String {
    val km = meters / 1000.0
    return "%.1f km".format(km)
}

private val ActivityType.displayLabel: String get() = when (this) {
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
    ActivityType.OTHER -> "Activity"
}
