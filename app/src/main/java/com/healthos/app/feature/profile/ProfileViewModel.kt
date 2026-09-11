package com.healthos.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.data.local.ProfilePreferences
import com.healthos.app.data.local.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val preferences: ProfilePreferences) : ViewModel() {
    val profile: StateFlow<UserProfile> = preferences.profile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserProfile()
    )

    fun save(profile: UserProfile) {
        viewModelScope.launch { preferences.save(profile) }
    }
}
