package com.triloo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

enum class ThemeMode(val displayName: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Темная")
}

enum class AppLanguage(val displayName: String, val localeTag: String) {
    RU("Русский", "ru"),
    EN("English", "en")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "RUB",
    val language: AppLanguage = AppLanguage.RU,
    val notificationsEnabled: Boolean = true,
    val syncEnabled: Boolean = true
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val mode = runCatching { ThemeMode.valueOf(modeName) }
                .getOrDefault(ThemeMode.SYSTEM)
            val currency = prefs[Keys.DEFAULT_CURRENCY] ?: "RUB"
            val languageName = prefs[Keys.LANGUAGE] ?: AppLanguage.RU.name
            val language = runCatching { AppLanguage.valueOf(languageName) }
                .getOrDefault(AppLanguage.RU)
            AppSettings(
                themeMode = mode,
                defaultCurrency = currency,
                language = language,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                syncEnabled = prefs[Keys.SYNC_ENABLED] ?: true
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultCurrency(currency: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_CURRENCY] = currency
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_ENABLED] = enabled
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    }
}
