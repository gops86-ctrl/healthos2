package com.healthos.app.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthos.app.domain.repository.HealthRepository
import com.healthos.app.feature.data.DataViewModel
import com.healthos.app.feature.fitness.FitnessViewModel
import com.healthos.app.feature.overview.OverviewViewModel

class HealthOSViewModelFactory(private val repository: HealthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(OverviewViewModel::class.java) -> OverviewViewModel(repository) as T
        modelClass.isAssignableFrom(DataViewModel::class.java) -> DataViewModel(repository) as T
        modelClass.isAssignableFrom(FitnessViewModel::class.java) -> FitnessViewModel(repository) as T
        else -> throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}
