package com.healthos.app.feature.metric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.domain.model.HealthMetric
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MetricDetailViewModel(repository: HealthRepository, type: MetricType) : ViewModel() {
    val history: StateFlow<List<HealthMetric>> = repository.observeHistory(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
