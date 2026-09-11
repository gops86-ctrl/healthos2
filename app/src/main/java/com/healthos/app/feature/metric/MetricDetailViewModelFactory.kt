package com.healthos.app.feature.metric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.repository.HealthRepository

class MetricDetailViewModelFactory(
    private val repository: HealthRepository,
    private val type: MetricType
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MetricDetailViewModel::class.java))
        return MetricDetailViewModel(repository, type) as T
    }
}
