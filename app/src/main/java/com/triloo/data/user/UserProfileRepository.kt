package com.triloo.data.user

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore by preferencesDataStore(name = "user_profile")

data class UserProfile(
    val userId: String,
    val displayName: String,
    val email: String?,
    val isAuthenticated: Boolean
)

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userDataStore

    val profile: Flow<UserProfile> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserProfile(
                userId = prefs[Keys.USER_ID] ?: "",
                displayName = prefs[Keys.DISPLAY_NAME] ?: "",
                email = prefs[Keys.EMAIL],
                isAuthenticated = prefs[Keys.IS_AUTHENTICATED] ?: false
            )
        }

    suspend fun getOrCreateUserId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[Keys.USER_ID]
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.USER_ID] = newId }
        return newId
    }

    suspend fun getProfile(): UserProfile {
        val profile = profile.first()
        val userId = if (profile.userId.isBlank()) getOrCreateUserId() else profile.userId
        return profile.copy(userId = userId)
    }

    suspend fun updateDisplayName(name: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name
        }
    }

    suspend fun updateEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.EMAIL)
            } else {
                prefs[Keys.EMAIL] = email
            }
        }
    }

    suspend fun setAuthenticated(
        displayName: String,
        email: String?
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = true
            prefs[Keys.DISPLAY_NAME] = displayName
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.EMAIL)
            } else {
                prefs[Keys.EMAIL] = email
            }
        }
        getOrCreateUserId()
    }

    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = false
        }
    }

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
    }
}
