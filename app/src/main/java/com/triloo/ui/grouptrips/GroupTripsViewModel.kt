package com.triloo.ui.grouptrips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Trip
import com.triloo.data.repository.TripRepository
import com.triloo.data.sync.RemoteTripInviteRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Управляет состоянием экрана групповых поездок: приветствие, фильтр,
 * вход по коду и сводка по каждому групповому маршруту с участниками
 * и ролью текущего пользователя.
 */
@HiltViewModel
class GroupTripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    private val remoteTripInviteRepository: RemoteTripInviteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupTripsUiState())
    val uiState: StateFlow<GroupTripsUiState> = _uiState.asStateFlow()

    // Поток сырых групповых поездок + участников + профиля собираем в один
    // снимок: иначе пришлось бы подписываться на observeParticipants(tripId)
    // на каждую поездку отдельно, что для UI-сводки избыточно.
    val groupTrips: StateFlow<List<GroupTripSummary>> = combine(
        tripRepository.observeAllTrips(),
        tripRepository.observeAllParticipants(),
        userProfileRepository.profile
    ) { trips, allParticipants, profile ->
        val participantsByTrip = allParticipants.groupBy { it.tripId }
        trips.filter { it.isGroupTrip }.map { trip ->
            val participants = participantsByTrip[trip.id].orEmpty()
            val role = participants.firstOrNull { it.userId == profile.userId }?.role
            val isOwner = trip.ownerId != null && trip.ownerId == profile.userId
            GroupTripSummary(
                trip = trip,
                participants = participants,
                currentUserRole = role,
                isOwner = isOwner
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            userProfileRepository.profile.collect { profile ->
                val name = profile.displayName.trim()
                _uiState.update { state ->
                    val shouldAutofill = name.isNotBlank() && (
                        state.displayName.isBlank() ||
                            state.displayName == state.lastAutofillName
                    )
                    state.copy(
                        userDisplayName = name,
                        displayName = if (shouldAutofill) name else state.displayName,
                        lastAutofillName = if (shouldAutofill) name else state.lastAutofillName
                    )
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

    fun setFilter(filter: TripFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun openJoinByCodeSheet() {
        _uiState.update { it.copy(showJoinByCodeSheet = true, error = null) }
    }

    fun dismissJoinByCodeSheet() {
        _uiState.update { it.copy(showJoinByCodeSheet = false, error = null) }
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
                        joinedTripId = joinedTripId,
                        showJoinByCodeSheet = false
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
 * Сводка по одной групповой поездке с предрассчитанной ролью текущего
 * пользователя — чтобы UI не дёргал репозиторий повторно ради бейджа.
 */
data class GroupTripSummary(
    val trip: Trip,
    val participants: List<Participant>,
    val currentUserRole: ParticipantRole?,
    val isOwner: Boolean
)

/**
 * Фильтр по статусу поездок в списке: только активные (предстоящие или
 * идущие) или архив (завершённые).
 */
enum class TripFilter {
    ACTIVE,
    ARCHIVE
}

/**
 * Состояние экрана присоединения к групповой поездке.
 */
data class GroupTripsUiState(
    val userDisplayName: String = "",
    val inviteCode: String = "",
    val displayName: String = "",
    val lastAutofillName: String = "",
    val isJoining: Boolean = false,
    val joinedTripId: String? = null,
    val error: String? = null,
    val filter: TripFilter = TripFilter.ACTIVE,
    val showJoinByCodeSheet: Boolean = false
) {
    val isJoinEnabled: Boolean
        get() = inviteCode.isNotBlank() && displayName.isNotBlank() && !isJoining
}
