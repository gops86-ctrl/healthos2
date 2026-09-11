package com.healthos.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profile_preferences")

data class UserProfile(
    val name: String = "",
    val gender: String = "",
    val heightCm: String = "",
    val dateOfBirth: String = "",
    val photoUri: String? = null
)

class ProfilePreferences(private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val gender = stringPreferencesKey("gender")
        val heightCm = stringPreferencesKey("height_cm")
        val dateOfBirth = stringPreferencesKey("date_of_birth")
        val photoUri = stringPreferencesKey("photo_uri")
    }

    val profile: Flow<UserProfile> = context.profileDataStore.data.map { prefs ->
        UserProfile(
            name = prefs[Keys.name].orEmpty(),
            gender = prefs[Keys.gender].orEmpty(),
            heightCm = prefs[Keys.heightCm].orEmpty(),
            dateOfBirth = prefs[Keys.dateOfBirth].orEmpty(),
            photoUri = prefs[Keys.photoUri]
        )
    }

    suspend fun save(profile: UserProfile) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.name] = profile.name.trim()
            prefs[Keys.gender] = profile.gender.trim()
            prefs[Keys.heightCm] = profile.heightCm.trim()
            prefs[Keys.dateOfBirth] = profile.dateOfBirth.trim()
            if (profile.photoUri.isNullOrBlank()) prefs.remove(Keys.photoUri)
            else prefs[Keys.photoUri] = profile.photoUri
        }
    }
}
