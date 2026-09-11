package com.healthos.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthos.app.data.local.ProfilePreferences

class ProfileViewModelFactory(private val preferences: ProfilePreferences) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) return ProfileViewModel(preferences) as T
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}
