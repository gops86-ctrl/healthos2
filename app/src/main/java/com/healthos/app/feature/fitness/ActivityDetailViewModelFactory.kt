package com.healthos.app.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthos.app.domain.repository.HealthRepository

class ActivityDetailViewModelFactory(
    private val repository: HealthRepository,
    private val activityId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ActivityDetailViewModel::class.java))
        return ActivityDetailViewModel(repository, activityId) as T
    }
}
