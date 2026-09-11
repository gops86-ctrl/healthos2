package com.healthos.app.data.source.hevy

import android.content.Context
import android.net.Uri
import com.healthos.app.data.local.dao.ExerciseDao
import com.healthos.app.data.local.dao.StrengthWorkoutDao
import com.healthos.app.data.local.dao.WorkoutSetDao
import com.healthos.app.data.local.entity.ExerciseEntity
import com.healthos.app.data.local.entity.StrengthWorkoutEntity
import com.healthos.app.data.local.entity.WorkoutSetEntity
import com.healthos.app.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class HevyCsvImporter(
    private val context: Context,
    private val workoutDao: StrengthWorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val setDao: WorkoutSetDao
) {
    data class Progress(val percent: Int, val stage: String, val processed: Int = 0, val total: Int = 0)
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    private data class CsvWorkout(val key: String, val title: String, val startMillis: Long, val endMillis: Long?, val rows: List<Map<String, String>>)

    suspend fun importWorkoutCsv(uri: Uri, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        try {
            onProgress(Progress(0, "Reading Hevy workout export…"))
            workoutDao.repairCorruptedDurations()
            val csv = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: return@withContext Result(0, 0, "Unable to open the selected Hevy CSV.")
            val parsed = parseCsv(csv)
            if (parsed.size < 2) return@withContext Result(0, 0, "The Hevy workout CSV is empty.")
            val headers = parsed.first().map { it.trim() }
            val rows = parsed.drop(1).mapNotNull { row ->
                if (row.all(String::isBlank)) null else headers.mapIndexedNotNull { index, header -> row.getOrNull(index)?.let { header to it.trim() } }.toMap()
            }
            val workouts = groupWorkouts(rows)
            if (workouts.isEmpty()) return@withContext Result(0, 0, "No valid Hevy workouts were found.")
            onProgress(Progress(15, "Preparing workouts…", 0, workouts.size))
            val importedAt = System.currentTimeMillis()
            var imported = 0
            var skipped = 0
            workouts.forEachIndexed { index, workout ->
                val existing = workoutDao.findBySourceRecordId(DataSource.HEVY.name, workout.key)
                if (existing != null) skipped++ else {
                    val duration = workout.endMillis?.let { (it - workout.startMillis).coerceAtLeast(0L) / 1000L }
                    val totalVolume = workout.rows.sumOf { row -> (row["weight_kg"]?.toDoubleOrNull() ?: 0.0) * (row["reps"]?.toDoubleOrNull() ?: 0.0) }.takeIf { it > 0.0 }
                    val workoutId = workoutDao.insert(StrengthWorkoutEntity(name = workout.title.ifBlank { "Hevy workout" }, durationSeconds = duration, totalVolumeKg = totalVolume, source = DataSource.HEVY.name, sourceRecordId = workout.key, recordedAtMillis = workout.startMillis, importedAtMillis = importedAt))
                    workout.rows.groupBy { it["exercise_title"].orEmpty().ifBlank { "Exercise" } }.entries.forEachIndexed { exerciseIndex, (exerciseName, exerciseRows) ->
                        val exerciseId = exerciseDao.insert(ExerciseEntity(workoutId = workoutId, name = exerciseName, orderIndex = exerciseIndex))
                        val sets = exerciseRows.mapIndexed { setIndex, row -> WorkoutSetEntity(exerciseId = exerciseId, orderIndex = row["set_index"]?.toIntOrNull() ?: setIndex, repetitions = row["reps"]?.toDoubleOrNull()?.toInt(), weightKg = row["weight_kg"]?.toDoubleOrNull(), durationSeconds = row["duration_seconds"]?.toDoubleOrNull()?.toLong()) }
                        if (sets.isNotEmpty()) setDao.insertAll(sets)
                    }
                    imported++
                }
                val processed = index + 1
                onProgress(Progress(15 + processed * 82 / workouts.size.coerceAtLeast(1), "Importing workouts…", processed, workouts.size))
            }
            onProgress(Progress(100, "Hevy import complete", workouts.size, workouts.size))
            Result(imported, skipped)
        } catch (exception: Exception) {
            Result(0, 0, exception.message ?: "Unable to import the Hevy workout CSV.")
        }
    }

    private fun groupWorkouts(rows: List<Map<String, String>>): List<CsvWorkout> {
        val grouped = linkedMapOf<String, MutableList<Map<String, String>>>()
        rows.forEach { row ->
            val title = row["title"].orEmpty()
            val start = parseDateMillis(row["start_time"])
            if (title.isBlank() || start == null) return@forEach
            grouped.getOrPut("${start}|${title.trim()}") { mutableListOf() }.add(row)
        }
        return grouped.mapNotNull { (key, workoutRows) ->
            val first = workoutRows.firstOrNull() ?: return@mapNotNull null
            val start = parseDateMillis(first["start_time"]) ?: return@mapNotNull null
            CsvWorkout(key, first["title"].orEmpty(), start, parseDateMillis(first["end_time"]), workoutRows)
        }.sortedByDescending { it.startMillis }
    }

    private fun parseDateMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        listOf("d MMM yyyy, HH:mm", "d MMM yyyy, H:mm", "dd MMM yyyy, HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss").forEach { pattern ->
            runCatching { return SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }.parse(value.trim())?.time }
        }
        return null
    }

    private fun parseCsv(csv: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                c == '"' && quoted && i + 1 < csv.length && csv[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += field.toString(); field.clear() }
                (c == '\n' || c == '\r') && !quoted -> { if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++; row += field.toString(); field.clear(); if (row.any { it.isNotBlank() }) result += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); if (row.any { it.isNotBlank() }) result += row }
        return result
    }
}
