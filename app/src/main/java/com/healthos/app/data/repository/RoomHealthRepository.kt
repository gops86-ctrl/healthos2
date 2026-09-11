package com.healthos.app.data.repository

import android.net.Uri
import com.healthos.app.data.local.dao.*
import com.healthos.app.data.local.entity.*
import com.healthos.app.data.source.hevy.HevyCsvImporter
import com.healthos.app.data.source.healthify.HealthifyWeightCsvImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalCsvImporter
import com.healthos.app.domain.model.*
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomHealthRepository(
    private val healthMetricDao: HealthMetricDao,
    private val activityDao: ActivityDao,
    private val strengthWorkoutDao: StrengthWorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val workoutSetDao: WorkoutSetDao,
    private val nutritionEntryDao: NutritionEntryDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val labResultDao: LabResultDao,
    private val hevyCsvImporter: HevyCsvImporter,
    private val healthifyWeightCsvImporter: HealthifyWeightCsvImporter,
    private val myFitnessPalCsvImporter: MyFitnessPalCsvImporter
) : HealthRepository {
    override fun observeLatestMetrics(): Flow<List<HealthMetric>> = healthMetricDao.observeLatestByType().map { it.map(HealthMetricEntity::toDomain) }
    override fun observeHistory(type: MetricType): Flow<List<HealthMetric>> = healthMetricDao.observeHistory(type.name).map { it.map(HealthMetricEntity::toDomain) }
    override suspend fun seedIfEmpty() {
        if (healthMetricDao.count() == 0) {
            val now = System.currentTimeMillis()
            healthMetricDao.insertAll(listOf(
                seed(MetricType.VO2_MAX, "52", "+4.2%", DataSource.GARMIN, now), seed(MetricType.RESTING_HR, "43 bpm", "−3 bpm", DataSource.GARMIN, now),
                seed(MetricType.HRV, "68 ms", "+8.1%", DataSource.GARMIN, now), seed(MetricType.SLEEP, "7h 42m", "+18 min", DataSource.GARMIN, now),
                seed(MetricType.STRESS, "23", "Low", DataSource.GARMIN, now), seed(MetricType.STEPS, "8,421", "+6.4%", DataSource.GARMIN, now),
                seed(MetricType.WEIGHT, "69.1 kg", "−0.3 kg", DataSource.HEALTHIFYME, now), seed(MetricType.CALORIES, "2,840", "7-day avg", DataSource.MYFITNESSPAL, now),
                seed(MetricType.PROTEIN, "168 g", "7-day avg", DataSource.MYFITNESSPAL, now), seed(MetricType.TRAINING_LOAD, "684", "+12%", DataSource.GARMIN, now),
                seed(MetricType.VITAMIN_D, "32 ng/mL", "Latest lab", DataSource.LABS, now)
            ))
        }
        if (activityDao.count() == 0) {
            val now = System.currentTimeMillis()
            activityDao.insertAll(listOf(seedActivity(ActivityType.RUN, "Morning run", 32 * 60L, 6200.0, now), seedActivity(ActivityType.RIDE, "Evening ride", 54 * 60L, 18500.0, now)))
        }
        seedStrengthIfEmpty(); seedNutritionIfEmpty(); seedBodyMeasurementsIfEmpty(); seedLabResultsIfEmpty()
    }
    private suspend fun seedStrengthIfEmpty() {
        if (strengthWorkoutDao.count() != 0) return
        val now = System.currentTimeMillis()
        val workoutId = strengthWorkoutDao.insert(StrengthWorkoutEntity(name = "Push day", durationSeconds = 48 * 60L, totalVolumeKg = 3120.0, source = DataSource.HEVY.name, sourceRecordId = "demo-push-day", recordedAtMillis = now, importedAtMillis = now))
        val exerciseId = exerciseDao.insert(ExerciseEntity(workoutId = workoutId, name = "Bench press", orderIndex = 0))
        workoutSetDao.insertAll(listOf(WorkoutSetEntity(exerciseId = exerciseId, orderIndex = 0, repetitions = 8, weightKg = 60.0, durationSeconds = null), WorkoutSetEntity(exerciseId = exerciseId, orderIndex = 1, repetitions = 6, weightKg = 65.0, durationSeconds = null)))
    }
    private suspend fun seedNutritionIfEmpty() { if (nutritionEntryDao.count() != 0) return; val now = System.currentTimeMillis(); nutritionEntryDao.insertAll(listOf(NutritionEntryEntity(calories = 620.0, proteinGrams = 42.0, carbohydrateGrams = 58.0, fatGrams = 18.0, source = DataSource.MYFITNESSPAL.name, sourceRecordId = "demo-breakfast", recordedAtMillis = now, importedAtMillis = now), NutritionEntryEntity(calories = 780.0, proteinGrams = 55.0, carbohydrateGrams = 70.0, fatGrams = 22.0, source = DataSource.MYFITNESSPAL.name, sourceRecordId = "demo-lunch", recordedAtMillis = now, importedAtMillis = now))) }
    private suspend fun seedBodyMeasurementsIfEmpty() { if (bodyMeasurementDao.count() != 0) return; val now = System.currentTimeMillis(); bodyMeasurementDao.insertAll(listOf(BodyMeasurementEntity(measurementType = BodyMeasurementType.WEIGHT.name, value = 69.1, unit = "kg", source = DataSource.HEALTHIFYME.name, sourceRecordId = "demo-weight", recordedAtMillis = now, importedAtMillis = now), BodyMeasurementEntity(measurementType = BodyMeasurementType.BODY_FAT_PERCENTAGE.name, value = 14.2, unit = "%", source = DataSource.HEALTHIFYME.name, sourceRecordId = "demo-body-fat", recordedAtMillis = now, importedAtMillis = now))) }
    private suspend fun seedLabResultsIfEmpty() = Unit
    override suspend fun addManualMetric(type: MetricType, value: String, unit: String, recordedAtMillis: Long) { healthMetricDao.insertAll(listOf(HealthMetricEntity(metricType = type.name, value = if (unit.isBlank()) value else "$value $unit", delta = "Manual entry", source = DataSource.MANUAL.name, sourceRecordId = "manual-${type.name.lowercase()}-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis))) }
    override fun observeActivities(): Flow<List<Activity>> = activityDao.observeAll().map { it.map(ActivityEntity::toDomain) }
    override fun observeActivity(id: Long): Flow<Activity?> = activityDao.observeAll().map { activities -> activities.firstOrNull { it.id == id }?.toDomain() }
    override suspend fun deleteActivity(id: Long) { activityDao.deleteById(id) }
    override suspend fun addActivity(activityType: ActivityType, name: String?, durationSeconds: Long, distanceMeters: Double?, recordedAtMillis: Long) { activityDao.insertAll(listOf(ActivityEntity(activityType = activityType.name, name = name, durationSeconds = durationSeconds, distanceMeters = distanceMeters, averageHeartRate = null, elevationGainMeters = null, source = DataSource.MANUAL.name, sourceRecordId = "manual-activity-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis))) }
    override fun observeWorkouts(): Flow<List<StrengthWorkout>> = strengthWorkoutDao.observeAll().map { it.map(StrengthWorkoutEntity::toDomain) }
    override fun observeWorkoutDetail(workoutId: Long): Flow<StrengthWorkoutDetail?> = strengthWorkoutDao.observeById(workoutId).flatMapLatest { workoutEntity -> if (workoutEntity == null) flowOf(null) else exerciseDao.observeForWorkout(workoutId).flatMapLatest { exerciseEntities -> if (exerciseEntities.isEmpty()) flowOf(StrengthWorkoutDetail(workoutEntity.toDomain(), emptyList())) else { val setFlows = exerciseEntities.map { exerciseEntity -> workoutSetDao.observeForExercise(exerciseEntity.id).map { sets -> ExerciseWithSets(exerciseEntity.toDomain(), sets.map(WorkoutSetEntity::toDomain)) } }; combine(setFlows) { it.toList() }.map { StrengthWorkoutDetail(workoutEntity.toDomain(), it) } } } }
    override suspend fun deleteWorkout(workoutId: Long) { strengthWorkoutDao.deleteById(workoutId) }
    override suspend fun importHevyWorkouts(uri: Uri, onProgress: (HevyCsvImporter.Progress) -> Unit): HevyCsvImporter.Result = hevyCsvImporter.importWorkoutCsv(uri, onProgress)
    override suspend fun addQuickWorkout(name: String, exerciseName: String, reps: Int, weightKg: Double?, recordedAtMillis: Long) { val workoutId = strengthWorkoutDao.insert(StrengthWorkoutEntity(name = name, durationSeconds = null, totalVolumeKg = weightKg?.let { it * reps }, source = DataSource.MANUAL.name, sourceRecordId = "manual-workout-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis)); val exerciseId = exerciseDao.insert(ExerciseEntity(workoutId = workoutId, name = exerciseName, orderIndex = 0)); workoutSetDao.insertAll(listOf(WorkoutSetEntity(exerciseId = exerciseId, orderIndex = 0, repetitions = reps, weightKg = weightKg, durationSeconds = null))) }
    override fun observeNutritionEntries(): Flow<List<NutritionEntry>> = nutritionEntryDao.observeAll().map { it.map(NutritionEntryEntity::toDomain) }
    override suspend fun addNutritionEntry(calories: Double?, proteinGrams: Double?, carbohydrateGrams: Double?, fatGrams: Double?, recordedAtMillis: Long) { nutritionEntryDao.insertAll(listOf(NutritionEntryEntity(calories = calories, proteinGrams = proteinGrams, carbohydrateGrams = carbohydrateGrams, fatGrams = fatGrams, source = DataSource.MYFITNESSPAL.name, sourceRecordId = "manual-nutrition-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis))) }
    override suspend fun importMyFitnessPalNutrition(uri: Uri, onProgress: (MyFitnessPalCsvImporter.Progress) -> Unit): MyFitnessPalCsvImporter.Result = myFitnessPalCsvImporter.importNutritionCsv(uri, onProgress)
    override fun observeBodyMeasurements(): Flow<List<BodyMeasurement>> = bodyMeasurementDao.observeAll().map { it.map(BodyMeasurementEntity::toDomain) }
    override suspend fun deleteBodyMeasurement(id: Long) { bodyMeasurementDao.deleteById(id); refreshLatestHealthifyWeightMetric() }
    override suspend fun addBodyMeasurement(measurementType: BodyMeasurementType, value: Double, unit: String, recordedAtMillis: Long) {
        val anchorWeight = if (measurementType == BodyMeasurementType.WEIGHT) bodyMeasurementDao.findLatestByType(BodyMeasurementType.WEIGHT.name) else null
        val anchorBodyFat = if (measurementType == BodyMeasurementType.WEIGHT) bodyMeasurementDao.findLatestByTypeExcludingSource(BodyMeasurementType.BODY_FAT_PERCENTAGE.name, DataSource.MANUAL.name) else null
        bodyMeasurementDao.insertAll(listOf(BodyMeasurementEntity(measurementType = measurementType.name, value = value, unit = unit, source = DataSource.MANUAL.name, sourceRecordId = "manual-${measurementType.name.lowercase()}-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis)))
        if (measurementType == BodyMeasurementType.WEIGHT) {
            healthMetricDao.deleteByTypeAndSourceRecord(MetricType.WEIGHT.name, DataSource.MANUAL.name, "manual-latest-weight")
            healthMetricDao.insertAll(listOf(HealthMetricEntity(metricType = MetricType.WEIGHT.name, value = "%.2f kg".format(java.util.Locale.US, value), delta = "Manual entry", source = DataSource.MANUAL.name, sourceRecordId = "manual-latest-weight", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis)))
            if (anchorWeight != null && anchorBodyFat != null && anchorWeight.value > 0.0 && value > 0.0) {
                val leanMassKg = anchorWeight.value * (1.0 - anchorBodyFat.value / 100.0)
                val estimatedBodyFat = ((1.0 - leanMassKg / value) * 100.0).coerceIn(3.0, 60.0)
                bodyMeasurementDao.insertAll(listOf(BodyMeasurementEntity(measurementType = BodyMeasurementType.BODY_FAT_PERCENTAGE.name, value = estimatedBodyFat, unit = "%", source = DataSource.MANUAL.name, sourceRecordId = "manual-bf-estimate-$recordedAtMillis", recordedAtMillis = recordedAtMillis, importedAtMillis = recordedAtMillis)))
            }
        }
    }
    override suspend fun importHealthifyWeights(uri: Uri, onProgress: (HealthifyWeightCsvImporter.Progress) -> Unit): HealthifyWeightCsvImporter.Result { val result = healthifyWeightCsvImporter.importWeightCsv(uri, onProgress); if (result.error == null) refreshLatestHealthifyWeightMetric(); return result }
    private suspend fun refreshLatestHealthifyWeightMetric() { healthMetricDao.deleteByTypeAndSourceRecord(MetricType.WEIGHT.name, DataSource.HEALTHIFYME.name, "healthify-latest-weight"); bodyMeasurementDao.findLatestByType(BodyMeasurementType.WEIGHT.name)?.let { latest -> if (latest.source == DataSource.HEALTHIFYME.name) healthMetricDao.insertAll(listOf(HealthMetricEntity(metricType = MetricType.WEIGHT.name, value = "%.2f kg".format(java.util.Locale.US, latest.value), delta = "HealthifyMe weight history", source = DataSource.HEALTHIFYME.name, sourceRecordId = "healthify-latest-weight", recordedAtMillis = latest.recordedAtMillis, importedAtMillis = System.currentTimeMillis()))) } } }
    override fun observeLabResults(): Flow<List<LabResult>> = labResultDao.observeAll().map { it.map(LabResultEntity::toDomain) }
    override suspend fun addLabResult(testName: String, value: Double, unit: String, referenceRange: String?, recordedAtMillis: Long) { labResultDao.deleteDemoResults(); labResultDao.insertAll(listOf(LabResultEntity(testName = testName, value = value, unit = unit, referenceRange = referenceRange, source = DataSource.MANUAL.name, sourceRecordId = "manual-lab-${UUID.randomUUID()}", recordedAtMillis = recordedAtMillis, importedAtMillis = System.currentTimeMillis(), importedFileId = null))) }
    override suspend fun updateLabResult(id: Long, testName: String, value: Double, unit: String, referenceRange: String?, recordedAtMillis: Long) { labResultDao.updateById(id, testName, value, unit, referenceRange, recordedAtMillis) }
    override suspend fun deleteLabResult(id: Long) { labResultDao.deleteById(id) }
    private fun seed(type: MetricType, value: String, delta: String, source: DataSource, now: Long) = HealthMetricEntity(metricType = type.name, value = value, delta = delta, source = source.name, sourceRecordId = "demo-${type.name.lowercase()}", recordedAtMillis = now, importedAtMillis = now)
    private fun seedActivity(type: ActivityType, name: String, durationSeconds: Long, distanceMeters: Double, now: Long) = ActivityEntity(activityType = type.name, name = name, durationSeconds = durationSeconds, distanceMeters = distanceMeters, averageHeartRate = null, elevationGainMeters = null, source = DataSource.GARMIN.name, sourceRecordId = "demo-${type.name.lowercase()}-activity", recordedAtMillis = now, importedAtMillis = now)
}

private fun HealthMetricEntity.toDomain() = HealthMetric(type = MetricType.valueOf(metricType), value = value, delta = delta, source = DataSource.valueOf(source), recordedAtMillis = recordedAtMillis, importedAtMillis = importedAtMillis, sourceRecordId = sourceRecordId)
private fun ActivityEntity.toDomain() = Activity(id = id, activityType = ActivityType.valueOf(activityType), name = name, durationSeconds = durationSeconds, elapsedDurationSeconds = elapsedDurationSeconds, distanceMeters = distanceMeters, averageHeartRate = averageHeartRate, maxHeartRate = maxHeartRate, averageSpeedMps = averageSpeedMps, elevationGainMeters = elevationGainMeters, calories = calories, routePoints = routePoints, provenance = RecordProvenance(source = DataSource.valueOf(source), sourceRecordId = sourceRecordId, recordedAtMillis = recordedAtMillis, importedAtMillis = importedAtMillis))
private fun StrengthWorkoutEntity.toDomain() = StrengthWorkout(id = id, name = name, durationSeconds = durationSeconds, totalVolumeKg = totalVolumeKg, provenance = RecordProvenance(source = DataSource.valueOf(source), sourceRecordId = sourceRecordId, recordedAtMillis = recordedAtMillis, importedAtMillis = importedAtMillis))
private fun ExerciseEntity.toDomain() = Exercise(id = id, workoutId = workoutId, name = name, orderIndex = orderIndex)
private fun WorkoutSetEntity.toDomain() = WorkoutSet(id = id, exerciseId = exerciseId, orderIndex = orderIndex, repetitions = repetitions, weightKg = weightKg, durationSeconds = durationSeconds)
private fun NutritionEntryEntity.toDomain() = NutritionEntry(id = id, calories = calories, proteinGrams = proteinGrams, carbohydrateGrams = carbohydrateGrams, fatGrams = fatGrams, meal = meal, saturatedFatGrams = saturatedFatGrams, polyunsaturatedFatGrams = polyunsaturatedFatGrams, monounsaturatedFatGrams = monounsaturatedFatGrams, transFatGrams = transFatGrams, cholesterolMg = cholesterolMg, sodiumMg = sodiumMg, potassiumMg = potassiumMg, fiberGrams = fiberGrams, sugarGrams = sugarGrams, vitaminAPercent = vitaminAPercent, vitaminCPercent = vitaminCPercent, calciumPercent = calciumPercent, ironPercent = ironPercent, provenance = RecordProvenance(DataSource.valueOf(source), sourceRecordId, recordedAtMillis, importedAtMillis))
private fun BodyMeasurementEntity.toDomain() = BodyMeasurement(id = id, measurementType = BodyMeasurementType.valueOf(measurementType), value = value, unit = unit, provenance = RecordProvenance(DataSource.valueOf(source), sourceRecordId, recordedAtMillis, importedAtMillis))
private fun LabResultEntity.toDomain() = LabResult(id = id, testName = testName, value = value, unit = unit, referenceRange = referenceRange, provenance = RecordProvenance(DataSource.valueOf(source), sourceRecordId, recordedAtMillis, importedAtMillis), importedFileId = importedFileId)