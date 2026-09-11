package com.healthos.app.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.model.ActivityType
import com.healthos.app.domain.model.RecordProvenance
import com.healthos.app.domain.model.StrengthWorkout
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FitnessViewModel(
    private val repository: HealthRepository
) : ViewModel() {
    val activities: StateFlow<List<Activity>> = combine(
        repository.observeActivities(),
        repository.observeWorkouts()
    ) { activities, workouts ->
        val strengthActivities = workouts.map { it.toActivity() }
        (activities + strengthActivities).sortedByDescending { it.provenance.recordedAtMillis }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private fun StrengthWorkout.toActivity(): Activity = Activity(
        id = -id,
        activityType = ActivityType.STRENGTH,
        name = name,
        durationSeconds = durationSeconds ?: 0L,
        elapsedDurationSeconds = durationSeconds,
        distanceMeters = null,
        averageHeartRate = null,
        maxHeartRate = null,
        averageSpeedMps = null,
        elevationGainMeters = null,
        calories = null,
        routePoints = null,
        provenance = RecordProvenance(
            source = provenance.source,
            sourceRecordId = provenance.sourceRecordId,
            recordedAtMillis = provenance.recordedAtMillis,
            importedAtMillis = provenance.importedAtMillis
        )
    )
}
