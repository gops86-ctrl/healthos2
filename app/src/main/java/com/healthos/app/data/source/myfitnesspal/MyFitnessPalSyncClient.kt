package com.healthos.app.data.source.myfitnesspal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MyFitnessPalSyncClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    suspend fun sync(days: Int): List<JSONObject> = withContext(Dispatchers.IO) {
        require(days == 7 || days == 30) { "MyFitnessPal sync supports 7D or 30D" }
        require(baseUrl.isNotBlank()) { "HealthOS API URL is not configured" }
        val url = URL("${baseUrl.trimEnd('/')}/health/mfp/nutrition?days=$days")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("X-HealthOS-API-Key", apiKey)
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("MyFitnessPal sync failed ($status): $body")
            val nutrition = JSONObject(body).optJSONArray("nutrition") ?: JSONArray()
            buildList { for (index in 0 until nutrition.length()) add(nutrition.getJSONObject(index)) }
        } finally {
            connection.disconnect()
        }
    }
}
