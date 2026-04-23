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

/**
 * Локально сохранённый профиль пользователя и устройства.
 */
data class UserProfile(
    val userId: String,
    val deviceId: String,
    val displayName: String,
    val email: String?,
    val isAuthenticated: Boolean
)

/**
 * Хранит имя пользователя, e-mail и стабильный идентификатор устройства для авторизации и синхронизации.
 */
@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userDataStore

    val profile: Flow<UserProfile> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val deviceId = prefs[Keys.DEVICE_ID] ?: ""
            val authUserId = prefs[Keys.AUTH_USER_ID]
            UserProfile(
                userId = authUserId ?: deviceId,
                deviceId = deviceId,
                displayName = prefs[Keys.DISPLAY_NAME] ?: "",
                email = prefs[Keys.EMAIL],
                isAuthenticated = prefs[Keys.IS_AUTHENTICATED] ?: false
            )
        }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.DEVICE_ID] = newId }
        return newId
    }

    suspend fun getOrCreateUserId(): String = getOrCreateDeviceId()

    suspend fun getProfile(): UserProfile {
        val profile = profile.first()
        val deviceId = if (profile.deviceId.isBlank()) getOrCreateDeviceId() else profile.deviceId
        val resolvedUserId = if (profile.userId.isBlank()) deviceId else profile.userId
        return profile.copy(userId = resolvedUserId, deviceId = deviceId)
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
        userId: String,
        displayName: String,
        email: String?
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = true
            prefs[Keys.AUTH_USER_ID] = userId
            prefs[Keys.DISPLAY_NAME] = displayName
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.EMAIL)
            } else {
                prefs[Keys.EMAIL] = email
            }
        }
        getOrCreateDeviceId()
    }

    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = false
            prefs.remove(Keys.AUTH_USER_ID)
        }
    }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
    }
}
