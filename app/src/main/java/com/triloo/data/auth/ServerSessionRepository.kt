package com.triloo.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverSessionDataStore by preferencesDataStore(name = "server_session")

data class ServerSession(
    val authToken: String? = null,
    val user: User? = null,
    val lastSyncAt: Long = 0L
)

@Singleton
class ServerSessionRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.serverSessionDataStore

    val session: Flow<ServerSession> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val token = prefs[Keys.AUTH_TOKEN]
            val userId = prefs[Keys.USER_ID]
            val user = if (token != null && userId != null) {
                User(
                    id = userId,
                    email = prefs[Keys.EMAIL] ?: "",
                    displayName = prefs[Keys.DISPLAY_NAME] ?: "",
                    avatarUrl = prefs[Keys.AVATAR_URL],
                    phoneNumber = prefs[Keys.PHONE_NUMBER],
                    preferredCurrency = prefs[Keys.PREFERRED_CURRENCY] ?: "RUB",
                    createdAt = prefs[Keys.CREATED_AT] ?: 0L,
                    lastLoginAt = prefs[Keys.LAST_LOGIN_AT] ?: 0L
                )
            } else {
                null
            }
            ServerSession(
                authToken = token,
                user = user,
                lastSyncAt = prefs[Keys.LAST_SYNC_AT] ?: 0L
            )
        }

    suspend fun getSession(): ServerSession = session.first()

    suspend fun saveSession(token: String, user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = token
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.DISPLAY_NAME] = user.displayName
            user.avatarUrl?.let { prefs[Keys.AVATAR_URL] = it } ?: prefs.remove(Keys.AVATAR_URL)
            user.phoneNumber?.let { prefs[Keys.PHONE_NUMBER] = it } ?: prefs.remove(Keys.PHONE_NUMBER)
            prefs[Keys.PREFERRED_CURRENCY] = user.preferredCurrency
            prefs[Keys.CREATED_AT] = user.createdAt
            prefs[Keys.LAST_LOGIN_AT] = user.lastLoginAt
        }
    }

    suspend fun updateUser(user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.DISPLAY_NAME] = user.displayName
            user.avatarUrl?.let { prefs[Keys.AVATAR_URL] = it } ?: prefs.remove(Keys.AVATAR_URL)
            user.phoneNumber?.let { prefs[Keys.PHONE_NUMBER] = it } ?: prefs.remove(Keys.PHONE_NUMBER)
            prefs[Keys.PREFERRED_CURRENCY] = user.preferredCurrency
            prefs[Keys.CREATED_AT] = user.createdAt
            prefs[Keys.LAST_LOGIN_AT] = user.lastLoginAt
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.AVATAR_URL)
            prefs.remove(Keys.PHONE_NUMBER)
            prefs.remove(Keys.PREFERRED_CURRENCY)
            prefs.remove(Keys.CREATED_AT)
            prefs.remove(Keys.LAST_LOGIN_AT)
            prefs.remove(Keys.LAST_SYNC_AT)
        }
    }

    suspend fun updateLastSyncAt(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_AT] = timestamp
        }
    }

    private object Keys {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
        val PREFERRED_CURRENCY = stringPreferencesKey("preferred_currency")
        val CREATED_AT = longPreferencesKey("created_at")
        val LAST_LOGIN_AT = longPreferencesKey("last_login_at")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }
}
