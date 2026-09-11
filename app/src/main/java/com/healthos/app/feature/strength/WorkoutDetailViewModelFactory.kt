package com.healthos.app.feature.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthos.app.domain.repository.HealthRepository

class WorkoutDetailViewModelFactory(
    private val repository: HealthRepository,
    private val workoutId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java))
        return WorkoutDetailViewModel(repository, workoutId) as T
    }
}
