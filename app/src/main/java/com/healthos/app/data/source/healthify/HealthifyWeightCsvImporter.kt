package com.healthos.app.data.source.healthify

import android.content.Context
import android.net.Uri
import com.healthos.app.data.local.dao.BodyMeasurementDao
import com.healthos.app.data.local.entity.BodyMeasurementEntity
import com.healthos.app.domain.model.BodyMeasurementType
import com.healthos.app.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class HealthifyWeightCsvImporter(
    private val context: Context,
    private val bodyMeasurementDao: BodyMeasurementDao
) {
    data class Progress(val percent: Int, val processed: Int, val total: Int, val stage: String)
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    suspend fun importWeightCsv(uri: Uri, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.drop(1).mapNotNull { parseRow(it) }.toList()
            } ?: return@withContext Result(0, 0, "Unable to open the selected file.")

            if (rows.isEmpty()) return@withContext Result(0, 0, "No valid weight rows found. Expected columns: weight, image_url, datestamp.")

            val now = System.currentTimeMillis()
            val measurements = rows.mapIndexed { index, row ->
                onProgress(Progress(((index + 1) * 100 / rows.size), index + 1, rows.size, "Importing weight history…"))
                BodyMeasurementEntity(
                    measurementType = BodyMeasurementType.WEIGHT.name,
                    value = row.weightKg,
                    unit = "kg",
                    source = DataSource.HEALTHIFYME.name,
                    sourceRecordId = "healthify-weight-${row.recordedAtMillis}",
                    recordedAtMillis = row.recordedAtMillis,
                    importedAtMillis = now
                )
            }
            bodyMeasurementDao.insertAll(measurements)
            Result(measurements.size, 0)
        } catch (e: Exception) {
            Result(0, 0, e.message ?: "HealthifyMe import failed.")
        }
    }

    private data class WeightRow(val weightKg: Double, val recordedAtMillis: Long)

    private fun parseRow(line: String): WeightRow? {
        val columns = splitCsv(line)
        if (columns.size < 3) return null
        val weight = columns[0].trim().toDoubleOrNull() ?: return null
        val date = parseDate(columns[2].trim()) ?: return null
        return WeightRow(weight, date)
    }

    private fun parseDate(value: String): Long? {
        val formats = listOf("MMMM d, yyyy", "MMM d, yyyy", "yyyy-MM-dd")
        for (pattern in formats) {
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') { current.append('"'); i++ }
                    else quoted = !quoted
                }
                ',' -> if (quoted) current.append(c) else { result += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
