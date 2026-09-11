package com.healthos.app.data.source.myfitnesspal

import android.content.Context
import android.net.Uri
import com.healthos.app.data.local.dao.NutritionEntryDao
import com.healthos.app.data.local.entity.NutritionEntryEntity
import com.healthos.app.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MyFitnessPalCsvImporter(
    private val context: Context,
    private val nutritionEntryDao: NutritionEntryDao
) {
    data class Progress(val percent: Int, val processed: Int, val total: Int, val stage: String)
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    suspend fun importNutritionCsv(uri: Uri, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                ?: return@withContext Result(0, 0, "Unable to open the selected file.")
            if (lines.size < 2) return@withContext Result(0, 0, "The selected file is empty.")

            val header = splitCsv(lines.first()).map { normalize(it) }
            fun index(vararg names: String): Int = names.firstNotNullOfOrNull { name -> header.indexOf(normalize(name)).takeIf { it >= 0 } } ?: -1
            val dateIndex = index("Date")
            val mealIndex = index("Meal")
            val caloriesIndex = index("Calories")
            val fatIndex = index("Fat (g)")
            val satFatIndex = index("Saturated Fat")
            val polyFatIndex = index("Polyunsaturated Fat")
            val monoFatIndex = index("Monounsaturated Fat")
            val transFatIndex = index("Trans Fat")
            val cholesterolIndex = index("Cholesterol")
            val sodiumIndex = index("Sodium (mg)")
            val potassiumIndex = index("Potassium")
            val carbsIndex = index("Carbohydrates (g)")
            val fiberIndex = index("Fiber")
            val sugarIndex = index("Sugar")
            val proteinIndex = index("Protein (g)")
            val vitaminAIndex = index("Vitamin A")
            val vitaminCIndex = index("Vitamin C")
            val calciumIndex = index("Calcium")
            val ironIndex = index("Iron")
            val noteIndex = index("Note")
            if (dateIndex < 0 || caloriesIndex < 0 || proteinIndex < 0 || carbsIndex < 0 || fatIndex < 0) {
                return@withContext Result(0, 0, "Unsupported MyFitnessPal export. Expected Date, Meal, Calories, Fat (g), Carbohydrates (g) and Protein (g).")
            }

            val rows = mutableListOf<NutritionEntryEntity>()
            val now = System.currentTimeMillis()
            var skipped = 0
            lines.drop(1).forEachIndexed { offset, line ->
                val columns = splitCsv(line)
                val date = columns.getOrNull(dateIndex)?.trim()?.let(::parseDate)
                if (date == null) { skipped++; return@forEachIndexed }
                val meal = columns.getOrNull(mealIndex)?.trim()?.ifBlank { null }
                val sourceId = "mfp-${date}-${meal ?: "unknown"}-${offset + 2}"
                rows += NutritionEntryEntity(
                    calories = value(columns, caloriesIndex), proteinGrams = value(columns, proteinIndex), carbohydrateGrams = value(columns, carbsIndex), fatGrams = value(columns, fatIndex),
                    meal = meal, saturatedFatGrams = value(columns, satFatIndex), polyunsaturatedFatGrams = value(columns, polyFatIndex), monounsaturatedFatGrams = value(columns, monoFatIndex),
                    transFatGrams = value(columns, transFatIndex), cholesterolMg = value(columns, cholesterolIndex), sodiumMg = value(columns, sodiumIndex), potassiumMg = value(columns, potassiumIndex),
                    fiberGrams = value(columns, fiberIndex), sugarGrams = value(columns, sugarIndex), vitaminAPercent = value(columns, vitaminAIndex), vitaminCPercent = value(columns, vitaminCIndex),
                    calciumPercent = value(columns, calciumIndex), ironPercent = value(columns, ironIndex), note = columns.getOrNull(noteIndex)?.trim()?.ifBlank { null },
                    source = DataSource.MYFITNESSPAL.name, sourceRecordId = sourceId, recordedAtMillis = date, importedAtMillis = now
                )
                onProgress(Progress((offset + 1) * 100 / (lines.size - 1), offset + 1, lines.size - 1, "Importing MyFitnessPal nutrition…"))
            }
            if (rows.isEmpty()) return@withContext Result(0, skipped, "No valid nutrition rows found.")
            nutritionEntryDao.deleteBySource(DataSource.MYFITNESSPAL.name)
            nutritionEntryDao.insertAll(rows)
            Result(rows.size, skipped)
        } catch (e: Exception) {
            Result(0, 0, e.message ?: "MyFitnessPal import failed.")
        }
    }

    private fun value(columns: List<String>, index: Int): Double? = if (index < 0) null else columns.getOrNull(index)?.trim()?.replace("%", "")?.toDoubleOrNull()
    private fun normalize(value: String) = value.trim().lowercase(Locale.US)
    private fun parseDate(value: String): Long? = listOf("yyyy-MM-dd", "M/d/yyyy", "MM/dd/yyyy", "MMM d, yyyy", "MMMM d, yyyy").firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)?.time }.getOrNull() }

    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>(); val current = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') { current.append('"'); i++ } else quoted = !quoted
                ',' -> if (quoted) current.append(c) else { result += current.toString(); current.clear() }
                else -> current.append(c)
            }; i++
        }
        result += current.toString(); return result
    }
}
