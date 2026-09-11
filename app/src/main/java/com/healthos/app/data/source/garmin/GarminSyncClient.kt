package com.healthos.app.data.source.garmin

import com.healthos.app.data.local.entity.ActivityEntity
import com.healthos.app.data.local.entity.HealthMetricEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

class GarminSyncClient(
    private val baseUrl: String,
    private val apiKey: String = ""
) {
    suspend fun sync(start: LocalDate, end: LocalDate): List<HealthMetricEntity> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(baseUrl.isNotBlank()) { "Garmin API URL is not configured" }
            val connection = openConnection("/garmin/sync")
            try {
                writeRange(connection, start, end)
                val body = readResponse(connection)
                parseMetrics(JSONObject(body))
            } finally {
                connection.disconnect()
            }
        }

    suspend fun syncActivities(start: LocalDate, end: LocalDate): List<ActivityEntity> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(baseUrl.isNotBlank()) { "Garmin API URL is not configured" }
            val connection = openConnection("/garmin/activities")
            try {
                writeRange(connection, start, end)
                val body = readResponse(connection)
                parseActivities(JSONObject(body))
            } finally {
                connection.disconnect()
            }
        }

    private fun openConnection(path: String): HttpURLConnection =
        (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // Garmin can take a while for a range query, especially when
            // refreshing an account's activity history.
            readTimeout = 600_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("X-HealthOS-API-Key", apiKey)
        }

    private fun writeRange(connection: HttpURLConnection, start: LocalDate, end: LocalDate) {
        connection.outputStream.use { it.write("{\"start\":\"$start\",\"end\":\"$end\"}".toByteArray()) }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException("Garmin sync failed ($status): $body")
        return body
    }

    private fun parseMetrics(root: JSONObject): List<HealthMetricEntity> {
        val metrics = root.optJSONArray("metrics") ?: return emptyList()
        val importedAt = System.currentTimeMillis()
        return buildList {
            for (index in 0 until metrics.length()) {
                val item = metrics.getJSONObject(index)
                val type = item.getString("type")
                val value = item.getDouble("value")
                val unit = item.optString("unit")
                val day = LocalDate.parse(item.getString("date"))
                val sourceRecordId = item.getString("sourceRecordId")
                add(
                    HealthMetricEntity(
                        metricType = type,
                        value = displayValue(type, value, unit),
                        delta = "Garmin sync",
                        source = "GARMIN",
                        sourceRecordId = sourceRecordId,
                        recordedAtMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        importedAtMillis = importedAt
                    )
                )
            }
        }
    }

    private fun parseActivities(root: JSONObject): List<ActivityEntity> {
        val activities = root.optJSONArray("activities") ?: return emptyList()
        val importedAt = System.currentTimeMillis()
        return buildList {
            for (index in 0 until activities.length()) {
                val item = activities.getJSONObject(index)
                val name = item.optString("name").takeIf { it.isNotBlank() }
                val type = normalizeActivityType(item.optString("activityType", "OTHER"), name)
                // Strength/workout remains Hevy-authoritative for V1.
                if (type == "STRENGTH" || type == "WORKOUT") continue
                val sourceRecordId = item.optString("sourceRecordId").takeIf { it.isNotBlank() } ?: continue
                val recordedAt = item.optLong("recordedAtMillis", 0L)
                val duration = item.optLong("durationSeconds", 0L)
                if (recordedAt <= 0L || duration <= 0L) continue
                add(
                    ActivityEntity(
                        activityType = type,
                        name = name,
                        durationSeconds = duration,
                        elapsedDurationSeconds = item.optLongOrNull("elapsedDurationSeconds"),
                        distanceMeters = item.optDoubleOrNull("distanceMeters"),
                        averageHeartRate = item.optIntOrNull("averageHeartRate"),
                        maxHeartRate = item.optIntOrNull("maxHeartRate"),
                        averageSpeedMps = item.optDoubleOrNull("averageSpeedMps"),
                        elevationGainMeters = item.optDoubleOrNull("elevationGainMeters"),
                        calories = item.optDoubleOrNull("calories"),
                        routePoints = item.optString("routePoints").takeIf { it.isNotBlank() },
                        source = "GARMIN",
                        sourceRecordId = sourceRecordId,
                        recordedAtMillis = recordedAt,
                        importedAtMillis = importedAt
                    )
                )
            }
        }
    }

    private fun normalizeActivityType(rawType: String, name: String?): String {
        val type = rawType.uppercase()
        if (type != "OTHER") return type
        val normalizedName = name.orEmpty().lowercase()
        return when {
            "swim" in normalizedName || "pool" in normalizedName -> "SWIM"
            "treadmill" in normalizedName || "run" in normalizedName -> "RUN"
            "walk" in normalizedName -> "WALK"
            "ride" in normalizedName || "cycling" in normalizedName || "bike" in normalizedName -> "RIDE"
            "hike" in normalizedName || "trek" in normalizedName -> "HIKE"
            else -> "OTHER"
        }
    }

    private fun displayValue(type: String, value: Double, unit: String): String = when (type) {
        "STEPS" -> "${value.toLong()}"
        "SLEEP" -> {
            val totalMinutes = (value / 60.0).toLong()
            "${totalMinutes / 60}h ${totalMinutes % 60}m"
        }
        "RESTING_HR" -> "${value.toInt()} bpm"
        "HRV" -> "${value.toInt()} ms"
        "VO2_MAX" -> "%.1f ml/kg/min".format(java.util.Locale.US, value)
        "STRESS" -> "${value.toInt()}"
        else -> if (unit.isBlank()) value.toString() else "$value $unit"
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)
