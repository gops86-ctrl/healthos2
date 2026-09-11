package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.ExerciseEntity
import com.healthos.app.data.local.entity.StrengthWorkoutEntity
import com.healthos.app.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrengthWorkoutDao {
    @Query("SELECT * FROM strength_workouts ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<StrengthWorkoutEntity>>

    @Query("SELECT * FROM strength_workouts WHERE id = :id")
    fun observeById(id: Long): Flow<StrengthWorkoutEntity?>

    @Query("SELECT * FROM strength_workouts WHERE source = :source AND sourceRecordId = :sourceRecordId LIMIT 1")
    suspend fun findBySourceRecordId(source: String, sourceRecordId: String): StrengthWorkoutEntity?

    @Query("SELECT COUNT(*) FROM strength_workouts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workout: StrengthWorkoutEntity): Long

    @Query("DELETE FROM strength_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE strength_workouts SET durationSeconds = durationSeconds / 1000 WHERE source = 'HEVY' AND durationSeconds > 86400")
    suspend fun repairCorruptedDurations()
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY orderIndex ASC")
    fun observeForWorkout(workoutId: Long): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity): Long
}

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY orderIndex ASC")
    fun observeForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSetEntity>)
}
