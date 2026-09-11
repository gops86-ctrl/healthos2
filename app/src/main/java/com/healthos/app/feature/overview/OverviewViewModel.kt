package com.healthos.app.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.BodyMeasurement
import com.healthos.app.domain.model.BodyMeasurementType
import com.healthos.app.domain.model.DataSource
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.LabResult
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.model.NutritionEntry
import com.healthos.app.domain.model.StrengthWorkout
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OverviewViewModel(repository: HealthRepository) : ViewModel() {
    val metrics: StateFlow<List<HealthMetric>> = repository.observeLatestMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activities: StateFlow<List<Activity>> = repository.observeActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts: StateFlow<List<StrengthWorkout>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val nutritionEntries: StateFlow<List<NutritionEntry>> = repository.observeNutritionEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val labResults: StateFlow<List<LabResult>> = repository.observeLabResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bodyMeasurements: StateFlow<List<BodyMeasurement>> = repository.observeBodyMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hrvHistory = history(repository, MetricType.HRV)
    val restingHrHistory = history(repository, MetricType.RESTING_HR)
    val sleepHistory = history(repository, MetricType.SLEEP)
    val vo2History = history(repository, MetricType.VO2_MAX)
    val stepsHistory = history(repository, MetricType.STEPS)
    val weightHistory: StateFlow<List<HealthMetric>> = repository.observeBodyMeasurements()
        .map { measurements ->
            measurements.filter { it.measurementType == BodyMeasurementType.WEIGHT }
                .map { measurement ->
                    HealthMetric(
                        type = MetricType.WEIGHT,
                        value = "%.2f kg".format(java.util.Locale.US, measurement.value),
                        delta = "HealthifyMe",
                        source = DataSource.valueOf(measurement.provenance.source.name),
                        recordedAtMillis = measurement.provenance.recordedAtMillis,
                        importedAtMillis = measurement.provenance.importedAtMillis,
                        sourceRecordId = measurement.provenance.sourceRecordId ?: "weight-${measurement.id}"
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.seedIfEmpty() } }

    private fun history(repository: HealthRepository, type: MetricType) =
        repository.observeHistory(type).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
