package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "running_activities",
    indices = [
        Index(
            value = ["source", "externalId"],
            unique = true
        )
    ]
)
data class RunningActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val source: String,
    val externalId: String,

    val name: String,
    val activityType: String,
    val startedAt: String,

    val distanceMeters: Double?,
    val movingTimeSeconds: Long?,
    val elapsedTimeSeconds: Long?,

    val elevationGainMeters: Double?,
    val averageSpeedMps: Double?,
    val maxSpeedMps: Double?,

    val averageHeartRateBpm: Double?,
    val maxHeartRateBpm: Double?,

    val calories: Double?
)
