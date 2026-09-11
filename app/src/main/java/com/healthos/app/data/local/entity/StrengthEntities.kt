package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "strength_workouts", indices = [Index(value = ["source", "sourceRecordId"], unique = true)])
data class StrengthWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationSeconds: Long?,
    val totalVolumeKg: Double?,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)

@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(
        entity = StrengthWorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    val orderIndex: Int
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val orderIndex: Int,
    val repetitions: Int?,
    val weightKg: Double?,
    val durationSeconds: Long?
)
