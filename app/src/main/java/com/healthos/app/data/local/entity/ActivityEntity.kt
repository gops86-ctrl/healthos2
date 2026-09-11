package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    indices = [Index(value = ["source", "sourceRecordId"], unique = true)]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val name: String?,
    val durationSeconds: Long,
    val elapsedDurationSeconds: Long? = null,
    val distanceMeters: Double?,
    val averageHeartRate: Int?,
    val maxHeartRate: Int? = null,
    val averageSpeedMps: Double? = null,
    val elevationGainMeters: Double?,
    val calories: Double? = null,
    val routePoints: String? = null,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)
