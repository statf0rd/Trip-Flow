package com.triloo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.settings.AppSettingsRepository
import com.triloo.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AppSettingsUiState> = repository.settings
        .map { AppSettingsUiState(themeMode = it.themeMode) }
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
}

data class AppSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
