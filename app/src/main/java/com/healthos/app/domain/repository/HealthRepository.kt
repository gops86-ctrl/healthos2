package com.healthos.app.domain.repository

import android.net.Uri
import com.healthos.app.data.source.hevy.HevyCsvImporter
import com.healthos.app.data.source.healthify.HealthifyWeightCsvImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalCsvImporter
import com.healthos.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface HealthRepository {
    fun observeLatestMetrics(): Flow<List<HealthMetric>>
    fun observeHistory(type: MetricType): Flow<List<HealthMetric>>
    suspend fun seedIfEmpty()
    suspend fun addManualMetric(type: MetricType, value: String, unit: String, recordedAtMillis: Long)
    fun observeActivities(): Flow<List<Activity>>
    fun observeActivity(id: Long): Flow<Activity?>
    suspend fun deleteActivity(id: Long)
    suspend fun addActivity(activityType: ActivityType, name: String?, durationSeconds: Long, distanceMeters: Double?, recordedAtMillis: Long)
    fun observeWorkouts(): Flow<List<StrengthWorkout>>
    fun observeWorkoutDetail(workoutId: Long): Flow<StrengthWorkoutDetail?>
    suspend fun deleteWorkout(workoutId: Long)
    suspend fun addQuickWorkout(name: String, exerciseName: String, reps: Int, weightKg: Double?, recordedAtMillis: Long)
    suspend fun importHevyWorkouts(uri: Uri, onProgress: (HevyCsvImporter.Progress) -> Unit = {}): HevyCsvImporter.Result
    fun observeNutritionEntries(): Flow<List<NutritionEntry>>
    suspend fun addNutritionEntry(calories: Double?, proteinGrams: Double?, carbohydrateGrams: Double?, fatGrams: Double?, recordedAtMillis: Long)
    suspend fun importMyFitnessPalNutrition(uri: Uri, onProgress: (MyFitnessPalCsvImporter.Progress) -> Unit = {}): MyFitnessPalCsvImporter.Result
    fun observeBodyMeasurements(): Flow<List<BodyMeasurement>>
    suspend fun deleteBodyMeasurement(id: Long)
    suspend fun addBodyMeasurement(measurementType: BodyMeasurementType, value: Double, unit: String, recordedAtMillis: Long)
    suspend fun importHealthifyWeights(uri: Uri, onProgress: (HealthifyWeightCsvImporter.Progress) -> Unit = {}): HealthifyWeightCsvImporter.Result
    fun observeLabResults(): Flow<List<LabResult>>
    suspend fun addLabResult(testName: String, value: Double, unit: String, referenceRange: String?, recordedAtMillis: Long)
    suspend fun updateLabResult(id: Long, testName: String, value: Double, unit: String, referenceRange: String?, recordedAtMillis: Long)
    suspend fun deleteLabResult(id: Long)
}
