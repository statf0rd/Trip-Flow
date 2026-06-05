package com.triloo.ui.grouptrips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Trip
import com.triloo.data.repository.TripRepository
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
 * Управляет состоянием экрана групповых поездок: приветствие, фильтр
 * и сводка по каждому групповому маршруту с участниками и ролью
 * текущего пользователя. Вход в поездку — только по Bluetooth (Relay):
 * UI «по коду или QR» удалён, такого способа присоединения нет.
 */
@HiltViewModel
class GroupTripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository
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
                _uiState.update { it.copy(userDisplayName = profile.displayName.trim()) }
            }
        }
    }

    fun setFilter(filter: TripFilter) {
        _uiState.update { it.copy(filter = filter) }
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
 * Состояние экрана групповых поездок: имя для приветствия и активный фильтр.
 */
data class GroupTripsUiState(
    val userDisplayName: String = "",
    val filter: TripFilter = TripFilter.ACTIVE
)
