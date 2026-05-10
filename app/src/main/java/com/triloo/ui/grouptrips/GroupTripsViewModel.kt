package com.triloo.ui.grouptrips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Participant
import com.triloo.data.repository.TripRepository
import com.triloo.data.sync.RemoteTripInviteRepository
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

/**
 * Управляет входом в групповые поездки по текстовому коду приглашения.
 */
@HiltViewModel
class GroupTripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    private val remoteTripInviteRepository: RemoteTripInviteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupTripsUiState())
    val uiState: StateFlow<GroupTripsUiState> = _uiState.asStateFlow()

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
                val profile = userProfileRepository.getProfile()
                val localUserId = profile.userId
                val trip = tripRepository.getTripByInviteCode(code)
                val joinedTripId = if (trip != null) {
                    tripRepository.addParticipant(
                        Participant(
                            tripId = trip.id,
                            userId = localUserId,
                            displayName = name
                        )
                    )
                    trip.id
                } else {
                    remoteTripInviteRepository.joinByInviteCode(code, name)
                        .getOrElse { error ->
                            throw error
                        }
                }

                userProfileRepository.updateDisplayName(name)

                _uiState.update {
                    it.copy(
                        isJoining = false,
                        joinedTripId = joinedTripId
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

/**
 * Состояние экрана присоединения к групповой поездке.
 */
data class GroupTripsUiState(
    val inviteCode: String = "",
    val displayName: String = "",
    val lastAutofillName: String = "",
    val isJoining: Boolean = false,
    val joinedTripId: String? = null,
    val error: String? = null
) {
    val isJoinEnabled: Boolean
        get() = inviteCode.isNotBlank() && displayName.isNotBlank() && !isJoining
}
