package com.healthos.app.data.source.myfitnesspal

import com.healthos.app.data.local.dao.NutritionEntryDao
import com.healthos.app.data.local.entity.NutritionEntryEntity
import com.healthos.app.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

class MyFitnessPalSyncImporter(
    private val client: MyFitnessPalSyncClient,
    private val nutritionEntryDao: NutritionEntryDao
) {
    data class Progress(val percent: Int, val processed: Int, val total: Int, val stage: String)
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    suspend fun sync(days: Int, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        try {
            onProgress(Progress(0, 0, 0, "Fetching MyFitnessPal nutrition…"))
            val records = client.sync(days)
            if (records.isEmpty()) {
                onProgress(Progress(100, 0, 0, "MyFitnessPal sync complete"))
                return@withContext Result(0, 0)
            }
            val now = System.currentTimeMillis()
            val entries = records.mapNotNull { record ->
                val sourceId = record.optString("sourceRecordId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val recordedAt = record.optLong("recordedAtMillis", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return@mapNotNull null
                NutritionEntryEntity(
                    calories = record.optDoubleNullable("calories"),
                    proteinGrams = record.optDoubleNullable("proteinGrams"),
                    carbohydrateGrams = record.optDoubleNullable("carbohydrateGrams"),
                    fatGrams = record.optDoubleNullable("fatGrams"),
                    meal = record.optString("meal").takeIf { it.isNotBlank() },
                    saturatedFatGrams = record.optDoubleNullable("saturatedFatGrams"),
                    polyunsaturatedFatGrams = record.optDoubleNullable("polyunsaturatedFatGrams"),
                    monounsaturatedFatGrams = record.optDoubleNullable("monounsaturatedFatGrams"),
                    transFatGrams = record.optDoubleNullable("transFatGrams"),
                    cholesterolMg = record.optDoubleNullable("cholesterolMg"),
                    sodiumMg = record.optDoubleNullable("sodiumMg"),
                    potassiumMg = record.optDoubleNullable("potassiumMg"),
                    fiberGrams = record.optDoubleNullable("fiberGrams"),
                    sugarGrams = record.optDoubleNullable("sugarGrams"),
                    vitaminAPercent = record.optDoubleNullable("vitaminAPercent"),
                    vitaminCPercent = record.optDoubleNullable("vitaminCPercent"),
                    calciumPercent = record.optDoubleNullable("calciumPercent"),
                    ironPercent = record.optDoubleNullable("ironPercent"),
                    note = record.optString("foodName").takeIf { it.isNotBlank() },
                    source = DataSource.MYFITNESSPAL.name,
                    sourceRecordId = sourceId,
                    recordedAtMillis = recordedAt,
                    importedAtMillis = now
                )
            }
            val skipped = records.size - entries.size
            entries.forEachIndexed { index, _ ->
                onProgress(Progress((index + 1) * 100 / entries.size.coerceAtLeast(1), index + 1, entries.size, "Storing MyFitnessPal nutrition…"))
            }

            // A live MFP sync is authoritative for the dates it covers. Remove any
            // existing MFP entries in that window first, including CSV-imported rows,
            // so the same day's CSV + live data cannot be double-counted.
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(days - 1))
            }
            val startMillis = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, days)
            val endMillis = calendar.timeInMillis
            nutritionEntryDao.deleteBySourceAndDateRange(DataSource.MYFITNESSPAL.name, startMillis, endMillis)
            nutritionEntryDao.insertAll(entries)
            Result(entries.size, skipped)
        } catch (e: Exception) {
            Result(0, 0, e.message ?: "MyFitnessPal sync failed.")
        }
    }
}

private fun JSONObject.optDoubleNullable(name: String): Double? = if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeIf { !it.isNaN() }
