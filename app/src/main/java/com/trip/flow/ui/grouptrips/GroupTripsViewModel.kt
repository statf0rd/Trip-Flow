package com.trip.flow.ui.grouptrips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.Participant
import com.trip.flow.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GroupTripsViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val localUserId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(GroupTripsUiState())
    val uiState: StateFlow<GroupTripsUiState> = _uiState.asStateFlow()

    val groupTrips = tripRepository.observeAllTrips()
        .map { trips -> trips.filter { it.isGroupTrip } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
}

data class GroupTripsUiState(
    val inviteCode: String = "",
    val displayName: String = "",
    val isJoining: Boolean = false,
    val joinedTripId: String? = null,
    val error: String? = null
) {
    val isJoinEnabled: Boolean
        get() = inviteCode.isNotBlank() && displayName.isNotBlank() && !isJoining
}
