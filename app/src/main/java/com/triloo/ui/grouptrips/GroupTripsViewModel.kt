package com.triloo.ui.grouptrips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.relay.RelayQrCodec
import com.triloo.data.relay.RelayQrCollector
import com.triloo.data.relay.RelayPayloadType
import com.triloo.data.relay.RelayRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupTripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    private val relayRepository: RelayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupTripsUiState())
    val uiState: StateFlow<GroupTripsUiState> = _uiState.asStateFlow()
    private val inviteCollector = RelayQrCollector()

    val groupTrips = tripRepository.observeAllTrips()
        .map { trips -> trips.filter { it.isGroupTrip } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            userProfileRepository.profile.collect { profile ->
                val name = profile.displayName.trim()
                if (name.isBlank()) return@collect

                _uiState.update { state ->
                    val shouldAutofill = state.displayName.isBlank() ||
                        state.displayName == state.lastAutofillName
                    if (shouldAutofill) {
                        state.copy(displayName = name, lastAutofillName = name)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun updateInviteCode(value: String) {
        val normalized = value.uppercase().replace(" ", "").take(12)
        _uiState.update { it.copy(inviteCode = normalized, error = null) }
    }

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value, error = null) }
    }

    fun joinByInviteCode() {
        val state = _uiState.value
        if (state.isJoining) return

        val code = state.inviteCode.trim().uppercase()
        val name = state.displayName.trim()

        if (code.isBlank()) {
            _uiState.update { it.copy(error = "Введите код приглашения") }
            return
        }
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Введите ваше имя") }
            return
        }

        _uiState.update { it.copy(isJoining = true, error = null, joinedTripId = null) }

        viewModelScope.launch {
            try {
                val localUserId = userProfileRepository.getOrCreateUserId()
                val trip = tripRepository.getTripByInviteCode(code)
                if (trip == null) {
                    _uiState.update { it.copy(isJoining = false, error = "Поездка по коду не найдена") }
                    return@launch
                }

                tripRepository.addParticipant(
                    Participant(
                        tripId = trip.id,
                        userId = localUserId,
                        displayName = name
                    )
                )

                userProfileRepository.updateDisplayName(name)

                _uiState.update {
                    it.copy(
                        isJoining = false,
                        joinedTripId = trip.id
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        error = e.message ?: "Не удалось присоединиться"
                    )
                }
            }
        }
    }

    fun consumeJoinedTripNavigation() {
        _uiState.update { it.copy(joinedTripId = null) }
    }

    fun handleInviteQrPayload(payload: String) {
        val chunk = RelayQrCodec.parse(payload)
        if (chunk == null || chunk.type != RelayPayloadType.INVITE) {
            inviteCollector.reset()
            _uiState.update {
                it.copy(
                    inviteScanError = "Это не QR-приглашение Triloo",
                    inviteScanProgress = 0,
                    inviteScanTotal = 0
                )
            }
            return
        }

        if (!inviteCollector.addChunk(chunk)) {
            inviteCollector.reset()
            _uiState.update {
                it.copy(
                    inviteScanError = "QR-код относится к другой сессии",
                    inviteScanProgress = 0,
                    inviteScanTotal = 0
                )
            }
            return
        }

        val (received, total) = inviteCollector.progress()
        _uiState.update { it.copy(inviteScanProgress = received, inviteScanTotal = total, inviteScanError = null) }

        if (!inviteCollector.isComplete()) return
        val jsonPayload = inviteCollector.assemblePayload() ?: run {
            _uiState.update { it.copy(inviteScanError = "Не удалось собрать пакет") }
            inviteCollector.reset()
            return
        }

        inviteCollector.reset()
        _uiState.update { it.copy(isProcessingInvite = true, inviteScanError = null) }

        viewModelScope.launch {
            try {
                val invitePackage = relayRepository.decodeInvite(jsonPayload)
                relayRepository.mergeInvitePackage(invitePackage)

                val profile = userProfileRepository.getProfile()
                val displayName = profile.displayName.ifBlank { "Участник" }
                tripRepository.addParticipant(
                    Participant(
                        tripId = invitePackage.trip.id,
                        userId = profile.userId,
                        displayName = displayName,
                        role = ParticipantRole.MEMBER
                    )
                )

                _uiState.update {
                    it.copy(
                        isProcessingInvite = false,
                        joinedTripId = invitePackage.trip.id,
                        inviteScanProgress = 0,
                        inviteScanTotal = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingInvite = false,
                        inviteScanError = e.message ?: "Не удалось импортировать приглашение"
                    )
                }
            }
        }
    }
}

data class GroupTripsUiState(
    val inviteCode: String = "",
    val displayName: String = "",
    val lastAutofillName: String = "",
    val isJoining: Boolean = false,
    val isProcessingInvite: Boolean = false,
    val joinedTripId: String? = null,
    val error: String? = null,
    val inviteScanProgress: Int = 0,
    val inviteScanTotal: Int = 0,
    val inviteScanError: String? = null
) {
    val isJoinEnabled: Boolean
        get() = inviteCode.isNotBlank() && displayName.isNotBlank() && !isJoining
}
