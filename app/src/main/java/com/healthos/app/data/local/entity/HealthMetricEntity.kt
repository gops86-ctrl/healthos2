package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Provider-neutral metric sample. Values stay display-ready for this first database
 * iteration; the source record and timestamps make future normalization/deduplication safe.
 */
@Entity(
    tableName = "health_metrics",
    indices = [Index(value = ["source", "sourceRecordId"], unique = true)]
)
data class HealthMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricType: String,
    val value: String,
    val delta: String,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)
