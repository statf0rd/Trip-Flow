package com.triloo.ui.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.relay.RelayPayloadType
import com.triloo.data.relay.RelayQrCodec
import com.triloo.data.relay.RelayRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Генерирует приглашение в поездку и упаковывает его в последовательность QR-чанков.
 */
@HiltViewModel
class InviteViewModel @Inject constructor(
    private val relayRepository: RelayRepository,
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
                val invitePackage = relayRepository.buildInvitePackage(tripId)
                if (trip == null || invitePackage == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Поездка не найдена") }
                    return@launch
                }
                val json = relayRepository.encodeInvite(invitePackage)
                val chunks = RelayQrCodec.encode(RelayPayloadType.INVITE, json)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tripName = trip.name,
                        inviteCode = trip.inviteCode,
                        chunks = chunks
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка генерации приглашения")
                }
            }
        }
    }
}

/**
 * Состояние экрана приглашения: код, имя поездки и готовые QR-страницы.
 */
data class InviteUiState(
    val tripName: String = "",
    val inviteCode: String = "",
    val chunks: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
