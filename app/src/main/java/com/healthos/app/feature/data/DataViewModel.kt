package com.healthos.app.feature.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.*
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DataViewModel(private val repository: HealthRepository) : ViewModel() {
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    private val _hevyState = MutableStateFlow(HevyImportState())
    val hevyState: StateFlow<HevyImportState> = _hevyState.asStateFlow()
    private val _healthifyState = MutableStateFlow(HealthifyImportState())
    val healthifyState: StateFlow<HealthifyImportState> = _healthifyState.asStateFlow()
    private val _mfpState = MutableStateFlow(MyFitnessPalImportState())
    val mfpState: StateFlow<MyFitnessPalImportState> = _mfpState.asStateFlow()
    private val _labResults = MutableStateFlow<List<LabResult>>(emptyList())
    val labResults: StateFlow<List<LabResult>> = _labResults.asStateFlow()

    init {
        repository.observeLabResults()
            .map { results -> results.firstOrNull()?.let(::listOf).orEmpty() }
            .onEach { _labResults.value = it }
            .launchIn(viewModelScope)
    }

    fun importHevyCsv(uri: Uri) { if (_hevyState.value.importing) return; viewModelScope.launch { _hevyState.value = HevyImportState(importing = true, progress = 0, stage = "Starting Hevy import…"); val result = repository.importHevyWorkouts(uri) { p -> _hevyState.value = HevyImportState(importing = p.percent < 100, progress = p.percent, stage = p.stage, processed = p.processed, total = p.total) }; _hevyState.value = HevyImportState(progress = 100, stage = if (result.error == null) "Hevy import complete" else "Hevy import failed", imported = result.imported, skipped = result.skipped, message = result.error ?: "Imported ${result.imported} workouts; ${result.skipped} already existed.") } }
    fun importHealthifyCsv(uri: Uri) { if (_healthifyState.value.importing) return; viewModelScope.launch { _healthifyState.value = HealthifyImportState(importing = true, progress = 0, stage = "Starting HealthifyMe import…"); val result = repository.importHealthifyWeights(uri) { p -> _healthifyState.value = HealthifyImportState(importing = p.percent < 100, progress = p.percent, stage = p.stage, processed = p.processed, total = p.total) }; _healthifyState.value = HealthifyImportState(progress = 100, stage = if (result.error == null) "HealthifyMe import complete" else "HealthifyMe import failed", imported = result.imported, skipped = result.skipped, message = result.error ?: "Imported ${result.imported} weight readings. Estimates are calculated from the personalized model.") } }
    fun importMyFitnessPalCsv(uri: Uri) { if (_mfpState.value.importing) return; viewModelScope.launch { _mfpState.value = MyFitnessPalImportState(importing = true, progress = 0, stage = "Starting MyFitnessPal import…"); val result = repository.importMyFitnessPalNutrition(uri) { p -> _mfpState.value = MyFitnessPalImportState(importing = p.percent < 100, progress = p.percent, stage = p.stage, processed = p.processed, total = p.total) }; _mfpState.value = MyFitnessPalImportState(progress = 100, stage = if (result.error == null) "MyFitnessPal import complete" else "MyFitnessPal import failed", imported = result.imported, skipped = result.skipped, message = result.error ?: "Imported ${result.imported} nutrition rows${if (result.skipped > 0) "; skipped ${result.skipped}" else ""}.") } }
    fun saveManualMetric(type: MetricType, value: String, unit: String) { if (value.trim().toDoubleOrNull() == null) { _saveState.value = SaveState.Error("Enter a numeric value."); return }; viewModelScope.launch { repository.addManualMetric(type, value, unit, System.currentTimeMillis()); _saveState.value = SaveState.Saved("${type.label} saved locally.") } }
    fun saveActivity(activityType: ActivityType, name: String, durationMinutes: String, distanceKm: String) { val minutes = durationMinutes.trim().toDoubleOrNull(); if (minutes == null || minutes <= 0.0) { _saveState.value = SaveState.Error("Enter a duration in minutes."); return }; val distanceMeters = distanceKm.trim().toDoubleOrNull()?.times(1000.0); viewModelScope.launch { repository.addActivity(activityType, name.ifBlank { null }, (minutes * 60).toLong(), distanceMeters, System.currentTimeMillis()); _saveState.value = SaveState.Saved("Activity saved locally.") } }
    fun saveQuickWorkout(workoutName: String, exerciseName: String, reps: String, weightKg: String) { if (workoutName.isBlank() || exerciseName.isBlank()) { _saveState.value = SaveState.Error("Enter a workout name and exercise name."); return }; val repsInt = reps.trim().toIntOrNull(); if (repsInt == null || repsInt <= 0) { _saveState.value = SaveState.Error("Enter a whole number of reps."); return }; viewModelScope.launch { repository.addQuickWorkout(workoutName, exerciseName, repsInt, weightKg.trim().toDoubleOrNull(), System.currentTimeMillis()); _saveState.value = SaveState.Saved("Workout saved locally.") } }
    fun saveNutritionEntry(calories: String, protein: String, carbs: String, fat: String) { val c = calories.trim().toDoubleOrNull(); val p = protein.trim().toDoubleOrNull(); val cb = carbs.trim().toDoubleOrNull(); val f = fat.trim().toDoubleOrNull(); if (c == null && p == null && cb == null && f == null) { _saveState.value = SaveState.Error("Enter at least one value."); return }; viewModelScope.launch { repository.addNutritionEntry(c, p, cb, f, System.currentTimeMillis()); _saveState.value = SaveState.Saved("Nutrition entry saved locally.") } }
    fun saveBodyMeasurement(measurementType: BodyMeasurementType, value: String, unit: String, recordedAtMillis: Long = System.currentTimeMillis()) { val numericValue = value.trim().toDoubleOrNull(); if (numericValue == null) { _saveState.value = SaveState.Error("Enter a numeric value."); return }; viewModelScope.launch { repository.addBodyMeasurement(measurementType, numericValue, unit, recordedAtMillis); _saveState.value = SaveState.Saved("Body measurement saved locally.") } }
    fun saveLabResult(testName: String, value: String, unit: String, referenceRange: String, recordedAtMillis: Long) { if (testName.isBlank()) { _saveState.value = SaveState.Error("Enter a test name."); return }; val numericValue = value.trim().toDoubleOrNull(); if (numericValue == null) { _saveState.value = SaveState.Error("Enter a numeric value."); return }; viewModelScope.launch { repository.addLabResult(testName.trim(), numericValue, unit.trim(), referenceRange.trim().ifBlank { null }, recordedAtMillis); _saveState.value = SaveState.Saved("Lab result saved locally.") } }
    fun deleteLabResult(id: Long) { viewModelScope.launch { repository.deleteLabResult(id); _saveState.value = SaveState.Saved("Lab result deleted.") } }
    fun clearSaveState() { _saveState.value = SaveState.Idle }
}

data class HevyImportState(val importing: Boolean = false, val progress: Int = 0, val stage: String? = null, val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val skipped: Int = 0, val message: String? = null)
data class HealthifyImportState(val importing: Boolean = false, val progress: Int = 0, val stage: String? = null, val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val skipped: Int = 0, val message: String? = null)
data class MyFitnessPalImportState(val importing: Boolean = false, val progress: Int = 0, val stage: String? = null, val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val skipped: Int = 0, val message: String? = null)
sealed interface SaveState { data object Idle : SaveState; data class Saved(val message: String) : SaveState; data class Error(val message: String) : SaveState }
