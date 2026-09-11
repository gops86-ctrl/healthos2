package com.healthos.app.feature.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.StrengthWorkoutDetail
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(
    private val repository: HealthRepository,
    private val workoutId: Long
) : ViewModel() {
    val detail: StateFlow<StrengthWorkoutDetail?> = repository.observeWorkoutDetail(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteWorkout(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            onDeleted()
        }
    }
}
