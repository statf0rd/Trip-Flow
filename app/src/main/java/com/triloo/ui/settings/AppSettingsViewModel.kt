package com.triloo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.settings.AppSettingsRepository
import com.triloo.data.settings.AppLanguage
import com.triloo.data.settings.ThemeMode
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val database: TrilooDatabase,
    private val userProfileRepository: UserProfileRepository,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<AppSettingsUiState> = repository.settings
        .map {
            AppSettingsUiState(
                themeMode = it.themeMode,
                defaultCurrency = it.defaultCurrency,
                language = it.language,
                notificationsEnabled = it.notificationsEnabled,
                syncEnabled = it.syncEnabled
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettingsUiState()
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            repository.setDefaultCurrency(currency)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguage(language)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSyncEnabled(enabled)
        }
    }

    fun exportData(targetUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val trips = tripRepository.getAllTrips()
                    val exportTrips = trips.map { trip ->
                        ExportTrip(
                            trip = trip,
                            days = tripRepository.getTripDays(trip.id),
                            places = tripRepository.getPlacesByTrip(trip.id),
                            expenses = expenseRepository.getExpensesByTrip(trip.id),
                            participants = tripRepository.getParticipants(trip.id)
                        )
                    }
                    val payload = ExportPayload(
                        generatedAt = System.currentTimeMillis(),
                        trips = exportTrips
                    )
                    val json = gson.toJson(payload)
                    val stream = context.contentResolver.openOutputStream(targetUri)
                        ?: throw IllegalStateException("Не удалось открыть файл")
                    stream.use {
                        it.write(json.toByteArray())
                    }
                }
            }
            onResult(result.isSuccess)
        }
    }

    fun clearAllData(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }
                userProfileRepository.signOut()
            }
            onResult(result.isSuccess)
        }
    }
}

data class AppSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "RUB",
    val language: AppLanguage = AppLanguage.RU,
    val notificationsEnabled: Boolean = true,
    val syncEnabled: Boolean = true
)

data class ExportPayload(
    val generatedAt: Long,
    val trips: List<ExportTrip>
)

data class ExportTrip(
    val trip: com.triloo.data.model.Trip,
    val days: List<com.triloo.data.model.TripDay>,
    val places: List<com.triloo.data.model.Place>,
    val expenses: List<com.triloo.data.model.Expense>,
    val participants: List<com.triloo.data.model.Participant>
)
