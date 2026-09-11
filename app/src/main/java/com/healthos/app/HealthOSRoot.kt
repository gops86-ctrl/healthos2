package com.healthos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.healthos.app.core.navigation.HealthOSNavHost
import com.healthos.app.data.local.database.HealthOSDatabase
import com.healthos.app.data.local.entity.ActivityEntity
import com.healthos.app.data.local.entity.HealthMetricEntity
import com.healthos.app.data.repository.RoomHealthRepository
import com.healthos.app.data.source.garmin.GarminSyncClient
import com.healthos.app.data.source.healthapi.HealthApiClient
import com.healthos.app.data.source.hevy.HevyCsvImporter
import com.healthos.app.data.source.hevy.HevySyncClient
import com.healthos.app.data.source.hevy.HevySyncImporter
import com.healthos.app.data.source.healthify.HealthifyWeightCsvImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalCsvImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalSyncClient
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalSyncImporter
import com.healthos.app.domain.model.DataSource
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.MetricType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@Composable
fun HealthOSRoot() {
    val context = LocalContext.current
    val database = remember(context) { HealthOSDatabase.create(context) }
    val repository = remember(context, database) {
        val hevyImporter = HevyCsvImporter(context, database.strengthWorkoutDao(), database.exerciseDao(), database.workoutSetDao())
        val healthifyWeightImporter = HealthifyWeightCsvImporter(context, database.bodyMeasurementDao())
        val myFitnessPalImporter = MyFitnessPalCsvImporter(context, database.nutritionEntryDao())
        RoomHealthRepository(
            healthMetricDao = database.healthMetricDao(),
            activityDao = database.activityDao(),
            strengthWorkoutDao = database.strengthWorkoutDao(),
            exerciseDao = database.exerciseDao(),
            workoutSetDao = database.workoutSetDao(),
            nutritionEntryDao = database.nutritionEntryDao(),
            bodyMeasurementDao = database.bodyMeasurementDao(),
            labResultDao = database.labResultDao(),
            hevyCsvImporter = hevyImporter,
            healthifyWeightCsvImporter = healthifyWeightImporter,
            myFitnessPalCsvImporter = myFitnessPalImporter
        )
    }

    LaunchedEffect(repository) {
        repository.seedIfEmpty()
        cleanupGarminStravaDuplicates(database)
        merge(
            database.healthMetricDao().observeAllHistory().map { Unit },
            repository.observeActivities().map { Unit },
            repository.observeWorkouts().map { Unit },
            repository.observeNutritionEntries().map { Unit },
            repository.observeBodyMeasurements().map { Unit },
            repository.observeLabResults().map { Unit }
        ).debounce(1000).collect {
            val result = HealthApiClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY).uploadSnapshot(
                metrics = database.healthMetricDao().observeAllHistory().first().map { it.toDomain() },
                activities = repository.observeActivities().first(),
                workouts = repository.observeWorkouts().first(),
                nutrition = repository.observeNutritionEntries().first(),
                bodyMeasurements = repository.observeBodyMeasurements().first(),
                labs = repository.observeLabResults().first()
            )
            result.onFailure { error -> android.util.Log.w("HealthOS", "Canonical snapshot upload failed", error) }
        }
    }

    val syncGarminActivities: suspend (String, (Int, String) -> Unit) -> Int = { range, onProgress ->
        val baseUrl = BuildConfig.GARMIN_API_BASE_URL
        require(baseUrl.isNotBlank()) { "Garmin API URL is not configured" }
        val end = LocalDate.now()
        val start = if (range == "30D") end.minusDays(29) else end.minusDays(6)
        val client = GarminSyncClient(baseUrl, BuildConfig.GARMIN_API_KEY)
        onProgress(0, "Fetching Garmin activities…")
        val candidates = client.syncActivities(start, end)
        onProgress(70, "Checking against Strava…")
        var imported = 0
        candidates.forEachIndexed { index, activity ->
            val windowStart = activity.recordedAtMillis - 24 * 60 * 60 * 1000L
            val windowEnd = activity.recordedAtMillis + 24 * 60 * 60 * 1000L
            val stravaCandidates = database.activityDao().findBySourceBetween("STRAVA", windowStart, windowEnd)
            val duplicate = stravaCandidates.any { isLikelySameActivity(it, activity) }
            val existingGarmin = database.activityDao().findBySourceRecordId("GARMIN", activity.sourceRecordId)
            if (duplicate) existingGarmin?.let { database.activityDao().deleteById(it.id) }
            else { database.activityDao().insertAll(listOf(activity)); imported++ }
            onProgress(70 + ((index + 1) * 30 / candidates.size.coerceAtLeast(1)), "Checking activities ${index + 1} / ${candidates.size}…")
        }
        cleanupGarminStravaDuplicates(database)
        imported
    }

    val syncGarmin: suspend (String, (Int, String) -> Unit) -> Int = { range, onProgress ->
        val baseUrl = BuildConfig.GARMIN_API_BASE_URL
        require(baseUrl.isNotBlank()) { "Garmin API URL is not configured" }
        val end = LocalDate.now()
        val start = when (range) {
            "30D" -> end.minusDays(29)
            "3M" -> end.minusMonths(3).plusDays(1)
            "1Y" -> end.minusYears(1).plusDays(1)
            "ALL" -> end.minusYears(5).plusDays(1)
            else -> end.minusDays(6)
        }
        val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val client = GarminSyncClient(baseUrl, BuildConfig.GARMIN_API_KEY)
        var completedDays = 0
        var totalMetrics = 0
        database.healthMetricDao().deleteDemoMetricsForSource("GARMIN")
        onProgress(0, "Preparing Garmin sync…")
        var chunkStart = start
        while (!chunkStart.isAfter(end)) {
            val chunkEnd = minOf(chunkStart.plusDays(27), end)
            onProgress(((completedDays.toDouble() / totalDays) * 100).toInt(), "Syncing ${chunkStart} → ${chunkEnd}…")
            val metrics = client.sync(chunkStart, chunkEnd)
            if (metrics.isNotEmpty()) database.healthMetricDao().insertAll(metrics)
            totalMetrics += metrics.size
            completedDays += ChronoUnit.DAYS.between(chunkStart, chunkEnd).toInt() + 1
            onProgress(((completedDays.toDouble() / totalDays) * 70).toInt().coerceAtMost(70), "Synced ${completedDays.coerceAtMost(totalDays)} / $totalDays days")
            chunkStart = chunkEnd.plusDays(1)
        }
        if (range == "7D" || range == "30D") syncGarminActivities(range) { progress, stage -> onProgress(70 + (progress * 30 / 100), stage) }
        else {
            cleanupGarminStravaDuplicates(database)
            onProgress(100, "Garmin sync complete")
        }
        HealthApiClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY).uploadSnapshot(
            metrics = database.healthMetricDao().observeAllHistory().first().map { it.toDomain() },
            activities = repository.observeActivities().first(),
            workouts = repository.observeWorkouts().first(),
            nutrition = repository.observeNutritionEntries().first(),
            bodyMeasurements = repository.observeBodyMeasurements().first(),
            labs = repository.observeLabResults().first()
        ).onFailure { error -> android.util.Log.w("HealthOS", "Post-sync canonical snapshot upload failed", error) }
        totalMetrics
    }

    val syncHevy: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result = { range, onProgress ->
        val days = when (range) { "30D" -> 30 else -> 7 }
        val importer = HevySyncImporter(
            HevySyncClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY),
            database.strengthWorkoutDao(), database.exerciseDao(), database.workoutSetDao()
        )
        val result = importer.sync(days) { progress -> onProgress(progress.percent, progress.stage) }
        if (result.error == null) {
            HealthApiClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY).uploadSnapshot(
                metrics = database.healthMetricDao().observeAllHistory().first().map { it.toDomain() },
                activities = repository.observeActivities().first(),
                workouts = repository.observeWorkouts().first(),
                nutrition = repository.observeNutritionEntries().first(),
                bodyMeasurements = repository.observeBodyMeasurements().first(),
                labs = repository.observeLabResults().first()
            ).onFailure { error -> android.util.Log.w("HealthOS", "Post-Hevy canonical snapshot upload failed", error) }
        }
        result
    }

    val syncMyFitnessPal: suspend (String, (Int, String) -> Unit) -> MyFitnessPalSyncImporter.Result = { range, onProgress ->
        val days = when (range) { "30D", "30" -> 30 else -> 7 }
        val importer = MyFitnessPalSyncImporter(
            MyFitnessPalSyncClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY),
            database.nutritionEntryDao()
        )
        val result = importer.sync(days) { progress -> onProgress(progress.percent, progress.stage) }
        if (result.error == null) {
            HealthApiClient(BuildConfig.HEALTHOS_API_BASE_URL, BuildConfig.HEALTHOS_API_KEY).uploadSnapshot(
                metrics = database.healthMetricDao().observeAllHistory().first().map { it.toDomain() },
                activities = repository.observeActivities().first(),
                workouts = repository.observeWorkouts().first(),
                nutrition = repository.observeNutritionEntries().first(),
                bodyMeasurements = repository.observeBodyMeasurements().first(),
                labs = repository.observeLabResults().first()
            ).onFailure { error -> android.util.Log.w("HealthOS", "Post-MyFitnessPal canonical snapshot upload failed", error) }
        }
        result
    }

    HealthOSNavHost(repository = repository, onGarminSync = syncGarmin, onHevySync = syncHevy, onMyFitnessPalSync = syncMyFitnessPal, onDeleteNutritionDay = { date ->
        val start = Calendar.getInstance().apply { timeInMillis = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        database.nutritionEntryDao().deleteByDateRange(start.timeInMillis, end.timeInMillis)
    })
}

private suspend fun cleanupGarminStravaDuplicates(database: HealthOSDatabase) {
    val activities = database.activityDao().getAll()
    val strava = activities.filter { it.source.equals("STRAVA", ignoreCase = true) }
    val garmin = activities.filter { it.source.equals("GARMIN", ignoreCase = true) }
    if (strava.isEmpty() || garmin.isEmpty()) return

    garmin.forEach { garminActivity ->
        val duplicate = strava.any { stravaActivity -> isLikelySameActivity(stravaActivity, garminActivity) }
        if (duplicate) database.activityDao().deleteById(garminActivity.id)
    }
}

private fun HealthMetricEntity.toDomain(): HealthMetric = HealthMetric(type = MetricType.valueOf(metricType), value = value, delta = delta, source = DataSource.valueOf(source), recordedAtMillis = recordedAtMillis, importedAtMillis = importedAtMillis, sourceRecordId = sourceRecordId)

private fun isLikelySameActivity(strava: ActivityEntity, garmin: ActivityEntity): Boolean {
    val startDifferenceSeconds = abs(strava.recordedAtMillis - garmin.recordedAtMillis) / 1000L
    if (startDifferenceSeconds > 24 * 60 * 60L) return false
    val durationDifference = abs(strava.durationSeconds - garmin.durationSeconds).toDouble()
    val maxDuration = max(strava.durationSeconds, garmin.durationSeconds).toDouble()
    if (maxDuration <= 0.0) return false
    val elapsedDifference = if (strava.elapsedDurationSeconds != null && garmin.elapsedDurationSeconds != null) abs(strava.elapsedDurationSeconds - garmin.elapsedDurationSeconds).toDouble() else null
    val durationMatch = durationDifference <= max(5 * 60.0, maxDuration * 0.08) || (elapsedDifference != null && elapsedDifference <= max(5 * 60.0, max(strava.elapsedDurationSeconds ?: 0L, garmin.elapsedDurationSeconds ?: 0L) * 0.08))
    if (!durationMatch) return false
    val distanceDifference = if (strava.distanceMeters != null && garmin.distanceMeters != null && strava.distanceMeters > 100.0 && garmin.distanceMeters > 100.0) abs(strava.distanceMeters - garmin.distanceMeters) / max(strava.distanceMeters, garmin.distanceMeters) else null
    if (distanceDifference != null) return distanceDifference <= 0.05
    return activityCategory(strava) == activityCategory(garmin) && activityCategory(strava) !in setOf("UNKNOWN", "OTHER")
}

private fun activityCategory(activity: ActivityEntity): String {
    val type = activity.activityType.uppercase(); val name = activity.name.orEmpty().lowercase()
    return when {
        type == "SWIM" || name.contains("swim") || name.contains("pool") -> "SWIM"
        type == "RUN" || type == "WALK" || name.contains("run") || name.contains("walk") || name.contains("treadmill") -> "LOCOMOTION"
        type == "RIDE" || name.contains("ride") || name.contains("cycling") || name.contains("bike") -> "RIDE"
        type == "HIKE" || name.contains("hike") || name.contains("trek") -> "HIKE"
        type == "STRENGTH" || type == "WORKOUT" || name.contains("strength") || name.contains("workout") || name.contains("weight") -> "STRENGTH"
        type in setOf("SOCCER", "ROCK_CLIMB", "CANOE") -> type
        type == "OTHER" -> inferOtherCategory(name)
        else -> "OTHER"
    }
}

private fun inferOtherCategory(name: String): String = when {
    name.contains("basket") || name.contains("court") -> "BASKETBALL"
    name.contains("football") -> "SOCCER"
    name.contains("tennis") -> "TENNIS"
    name.contains("badminton") -> "BADMINTON"
    name.contains("elliptical") || name.contains("cross") -> "ELLIPTICAL"
    else -> "OTHER"
}
