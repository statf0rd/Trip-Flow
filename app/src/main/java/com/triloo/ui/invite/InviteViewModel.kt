package com.triloo.ui.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Загружает приглашение в поездку: имя поездки и текстовый код для приглашения участников.
 */
@HiltViewModel
class InviteViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId")
        ?: throw IllegalArgumentException("tripId is required")

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    init {
        refreshInvite()
    }

    fun refreshInvite() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val trip = tripRepository.getTripById(tripId)
                if (trip == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Поездка не найдена") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tripName = trip.name,
                        inviteCode = trip.inviteCode
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки приглашения")
                }
            }
        }
    }
}

/**
 * Состояние экрана приглашения: текстовый код и имя поездки.
 */
data class InviteUiState(
    val tripName: String = "",
    val inviteCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
