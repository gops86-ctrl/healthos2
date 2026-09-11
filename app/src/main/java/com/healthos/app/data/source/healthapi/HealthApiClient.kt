package com.healthos.app.data.source.healthapi

import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.LabResult
import com.healthos.app.domain.model.NutritionEntry
import com.healthos.app.domain.model.StrengthWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HealthApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    suspend fun uploadSnapshot(
        metrics: List<HealthMetric>,
        activities: List<Activity>,
        workouts: List<StrengthWorkout>,
        nutrition: List<NutritionEntry>,
        bodyMeasurements: List<BodyMeasurement>,
        labs: List<LabResult>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.isNotBlank()) { "HealthOS API URL is not configured" }

            val root = JSONObject().apply {
                put("metrics", JSONArray().apply { metrics.forEach { put(it.toJson()) } })
                put("activities", JSONArray().apply { activities.forEach { put(it.toJson()) } })
                put("workouts", JSONArray().apply { workouts.forEach { put(it.toJson()) } })
                put("nutrition", JSONArray().apply { nutrition.forEach { put(it.toJson()) } })
                put("body_measurements", JSONArray().apply { bodyMeasurements.forEach { put(it.toJson()) } })
                put("labs", JSONArray().apply { labs.forEach { put(it.toJson()) } })
                // Profile is intentionally omitted until profile persistence is
                // exposed through the domain repository. The API accepts it as optional.
                put("profile", JSONObject())
            }

            val connection = (URL("${baseUrl.trimEnd('/')}/health/snapshot").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("X-HealthOS-API-Key", apiKey)
            }

            try {
                connection.outputStream.use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error("HealthOS API returned HTTP $code${if (error.isBlank()) "" else ": $error"}")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

private fun HealthMetric.toJson() = JSONObject().apply {
    put("metricType", type.name)
    put("value", value)
    put("delta", delta)
    put("source", source.name)
    put("sourceRecordId", sourceRecordId)
    put("recordedAtMillis", recordedAtMillis)
    put("importedAtMillis", importedAtMillis)
}

private fun Activity.toJson() = JSONObject().apply {
    put("id", id)
    put("activityType", activityType.name)
    put("name", name)
    put("durationSeconds", durationSeconds)
    put("elapsedDurationSeconds", elapsedDurationSeconds)
    put("distanceMeters", distanceMeters)
    put("averageHeartRate", averageHeartRate)
    put("maxHeartRate", maxHeartRate)
    put("averageSpeedMps", averageSpeedMps)
    put("elevationGainMeters", elevationGainMeters)
    put("calories", calories)
    put("routePoints", routePoints)
    put("source", provenance.source.name)
    put("sourceRecordId", provenance.sourceRecordId)
    put("recordedAtMillis", provenance.recordedAtMillis)
    put("importedAtMillis", provenance.importedAtMillis)
}

private fun StrengthWorkout.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("durationSeconds", durationSeconds)
    put("totalVolumeKg", totalVolumeKg)
    put("source", provenance.source.name)
    put("sourceRecordId", provenance.sourceRecordId)
    put("recordedAtMillis", provenance.recordedAtMillis)
    put("importedAtMillis", provenance.importedAtMillis)
}

private fun NutritionEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("calories", calories)
    put("proteinGrams", proteinGrams)
    put("carbohydrateGrams", carbohydrateGrams)
    put("fatGrams", fatGrams)
    put("meal", meal)
    put("saturatedFatGrams", saturatedFatGrams)
    put("polyunsaturatedFatGrams", polyunsaturatedFatGrams)
    put("monounsaturatedFatGrams", monounsaturatedFatGrams)
    put("transFatGrams", transFatGrams)
    put("cholesterolMg", cholesterolMg)
    put("sodiumMg", sodiumMg)
    put("potassiumMg", potassiumMg)
    put("fiberGrams", fiberGrams)
    put("sugarGrams", sugarGrams)
    put("vitaminAPercent", vitaminAPercent)
    put("vitaminCPercent", vitaminCPercent)
    put("calciumPercent", calciumPercent)
    put("ironPercent", ironPercent)
    put("note", note)
    put("source", provenance.source.name)
    put("sourceRecordId", provenance.sourceRecordId)
    put("recordedAtMillis", provenance.recordedAtMillis)
    put("importedAtMillis", provenance.importedAtMillis)
}

private fun BodyMeasurement.toJson() = JSONObject().apply {
    put("id", id)
    put("measurementType", measurementType.name)
    put("value", value)
    put("unit", unit)
    put("source", provenance.source.name)
    put("sourceRecordId", provenance.sourceRecordId)
    put("recordedAtMillis", provenance.recordedAtMillis)
    put("importedAtMillis", provenance.importedAtMillis)
}

private fun LabResult.toJson() = JSONObject().apply {
    put("id", id)
    put("testName", testName)
    put("value", value)
    put("unit", unit)
    put("referenceRange", referenceRange)
    put("source", provenance.source.name)
    put("sourceRecordId", provenance.sourceRecordId)
    put("recordedAtMillis", provenance.recordedAtMillis)
    put("importedAtMillis", provenance.importedAtMillis)
    put("importedFileId", importedFileId)
}
