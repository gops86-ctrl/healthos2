package com.healthos.app.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.Activity
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityDetailViewModel(
    private val repository: HealthRepository,
    private val activityId: Long
) : ViewModel() {
    val activity: StateFlow<Activity?> = repository.observeActivity(activityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteActivity(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteActivity(activityId)
            onDeleted()
        }
    }
}
