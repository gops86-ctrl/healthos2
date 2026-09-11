package com.healthos.app.data.source.hevy

import com.healthos.app.data.local.dao.ExerciseDao
import com.healthos.app.data.local.dao.StrengthWorkoutDao
import com.healthos.app.data.local.dao.WorkoutSetDao
import com.healthos.app.data.local.entity.ExerciseEntity
import com.healthos.app.data.local.entity.StrengthWorkoutEntity
import com.healthos.app.data.local.entity.WorkoutSetEntity
import com.healthos.app.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

class HevySyncImporter(private val client: HevySyncClient, private val workoutDao: StrengthWorkoutDao, private val exerciseDao: ExerciseDao, private val setDao: WorkoutSetDao) {
    data class Progress(val percent: Int, val stage: String, val processed: Int = 0, val total: Int = 0)
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    suspend fun sync(days: Int, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        try {
            onProgress(Progress(0, "Fetching Hevy workouts…"))
            val workouts = client.sync(days)
            var imported = 0
            var skipped = 0
            workouts.forEachIndexed { index, workout ->
                val sourceId = workout.optString("id").takeIf { it.isNotBlank() }
                val title = workout.optString("title").ifBlank { workout.optString("name") }.ifBlank { "Hevy workout" }
                val start = timestampMillis(workout.opt("start_time")) ?: return@forEachIndexed
                val end = timestampMillis(workout.opt("end_time"))
                val exact = sourceId?.let { workoutDao.findBySourceRecordId(DataSource.HEVY.name, it) }
                val duplicate = exact ?: workoutDao.findLikelyDuplicate(DataSource.HEVY.name, title, start - 10 * 60_000L, start + 10 * 60_000L)
                if (duplicate != null) {
                    skipped++
                } else {
                    val exercises = workout.optJSONArray("exercises")
                    val volume = exercises?.let { array ->
                        var total = 0.0
                        for (i in 0 until array.length()) {
                            val sets = array.optJSONObject(i)?.optJSONArray("sets") ?: continue
                            for (j in 0 until sets.length()) {
                                val set = sets.optJSONObject(j) ?: continue
                                val weight = set.optDoubleNullable("weight_kg") ?: continue
                                val reps = set.optDoubleNullable("reps") ?: continue
                                total += weight * reps
                            }
                        }
                        total.takeIf { it > 0.0 }
                    }
                    val workoutId = workoutDao.insert(StrengthWorkoutEntity(name = title, durationSeconds = end?.let { ((it - start) / 1000L).coerceAtLeast(0L) }, totalVolumeKg = volume, source = DataSource.HEVY.name, sourceRecordId = sourceId ?: "$start-${title.lowercase()}", recordedAtMillis = start, importedAtMillis = System.currentTimeMillis()))
                    for (i in 0 until (exercises?.length() ?: 0)) {
                        val exercise = exercises?.optJSONObject(i) ?: continue
                        val name = exercise.optString("title").ifBlank { exercise.optString("name") }.ifBlank { "Exercise" }
                        val exerciseId = exerciseDao.insert(ExerciseEntity(workoutId = workoutId, name = name, orderIndex = i))
                        val sets = exercise.optJSONArray("sets") ?: continue
                        val entities = buildList {
                            for (j in 0 until sets.length()) {
                                val set = sets.optJSONObject(j) ?: continue
                                add(WorkoutSetEntity(exerciseId = exerciseId, orderIndex = j, repetitions = set.optIntNullable("reps"), weightKg = set.optDoubleNullable("weight_kg"), durationSeconds = set.optLongNullable("duration_seconds")))
                            }
                        }
                        if (entities.isNotEmpty()) setDao.insertAll(entities)
                    }
                    imported++
                }
                onProgress(Progress(10 + (index + 1) * 90 / workouts.size.coerceAtLeast(1), "Checking and storing workouts…", index + 1, workouts.size))
            }
            onProgress(Progress(100, "Hevy sync complete", workouts.size, workouts.size))
            Result(imported, skipped)
        } catch (e: Exception) {
            Result(0, 0, e.message ?: "Unable to sync Hevy workouts.")
        }
    }

    private fun timestampMillis(value: Any?): Long? = when (value) {
        is Number -> value.toDouble().let { if (it < 10_000_000_000.0) (it * 1000).toLong() else it.toLong() }
        is String -> value.trim().toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it }
            ?: runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrNull()
        else -> null
    }
}

private fun JSONObject.optDoubleNullable(name: String): Double? = if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeIf { !it.isNaN() }
private fun JSONObject.optIntNullable(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
private fun JSONObject.optLongNullable(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
