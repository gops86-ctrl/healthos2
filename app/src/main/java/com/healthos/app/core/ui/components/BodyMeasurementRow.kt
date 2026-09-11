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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.BodyMeasurementType
import java.text.DateFormat
import java.util.Date

@Composable
fun BodyMeasurementRow(measurement: BodyMeasurement, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(measurement.measurementType.displayLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    DateFormat.getDateInstance().format(Date(measurement.provenance.recordedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("%.1f %s".format(measurement.value, measurement.unit), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val BodyMeasurementType.displayLabel: String get() = when (this) {
    BodyMeasurementType.WEIGHT -> "Weight"
    BodyMeasurementType.BODY_FAT_PERCENTAGE -> "Body fat"
    BodyMeasurementType.WAIST_CIRCUMFERENCE -> "Waist"
    BodyMeasurementType.OTHER -> "Measurement"
}
