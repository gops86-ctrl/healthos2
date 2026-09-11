package com.healthos.app.domain.model

/** Shared provenance carried by every normalized record imported into HealthOS. */
data class RecordProvenance(
    val source: DataSource,
    val sourceRecordId: String? = null,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)

data class MetricSample(
    val metricType: MetricType,
    val value: Double,
    val unit: String,
    val provenance: RecordProvenance
)

data class Activity(
    val id: Long,
    val activityType: ActivityType,
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
    val provenance: RecordProvenance
)

enum class ActivityType { RUN, RIDE, WALK, HIKE, SWIM, STRENGTH, SOCCER, WORKOUT, ROCK_CLIMB, CANOE, OTHER }

data class StrengthWorkout(
    val id: Long,
    val name: String,
    val durationSeconds: Long?,
    val totalVolumeKg: Double?,
    val provenance: RecordProvenance
)

data class Exercise(val id: Long, val workoutId: Long, val name: String, val orderIndex: Int)
data class WorkoutSet(val id: Long, val exerciseId: Long, val orderIndex: Int, val repetitions: Int?, val weightKg: Double?, val durationSeconds: Long?)

data class NutritionEntry(
    val id: Long,
    val calories: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val meal: String? = null,
    val saturatedFatGrams: Double? = null,
    val polyunsaturatedFatGrams: Double? = null,
    val monounsaturatedFatGrams: Double? = null,
    val transFatGrams: Double? = null,
    val cholesterolMg: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val vitaminAPercent: Double? = null,
    val vitaminCPercent: Double? = null,
    val calciumPercent: Double? = null,
    val ironPercent: Double? = null,
    val note: String? = null,
    val provenance: RecordProvenance
)

data class BodyMeasurement(val id: Long, val measurementType: BodyMeasurementType, val value: Double, val unit: String, val provenance: RecordProvenance)
enum class BodyMeasurementType { WEIGHT, BODY_FAT_PERCENTAGE, WAIST_CIRCUMFERENCE, OTHER }

data class LabResult(val id: Long, val testName: String, val value: Double, val unit: String, val referenceRange: String?, val provenance: RecordProvenance, val importedFileId: Long?)
data class UserAttribute(val id: Long, val attributeType: UserAttributeType, val value: String, val updatedAtMillis: Long)
enum class UserAttributeType { HEIGHT, DATE_OF_BIRTH, GOAL, OTHER }
data class SyncRecord(val id: Long, val source: DataSource, val status: SyncStatus, val startedAtMillis: Long, val completedAtMillis: Long?, val detail: String?)
enum class SyncStatus { STARTED, COMPLETED, FAILED }
data class ImportedFile(val id: Long, val displayName: String, val mimeType: String?, val uri: String, val importedAtMillis: Long)
