package com.triloo.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Trip
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Готовит агрегированное состояние списка поездок и обрабатывает удаление карточек.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val baseFlow = combine(
        tripRepository.observeCurrentTrip(),
        tripRepository.observeUpcomingTrips(),
        tripRepository.observePastTrips()
    ) { current, upcoming, past -> Triple(current, upcoming, past) }

    val uiState: StateFlow<TripListUiState> = baseFlow
        .flatMapLatest { (current, upcoming, past) ->
            if (current == null) {
                flowOf(
                    TripListUiState(
                        currentTrip = null,
                        upcomingTrips = upcoming,
                        pastTrips = past
                    )
                )
            } else {
                combine(
                    tripRepository.observePlacesByTrip(current.id),
                    expenseRepository.observeTotalExpenses(current.id)
                ) { places, total ->
                    TripListUiState(
                        currentTrip = current,
                        currentTripPlaceCount = places.size,
                        currentTripTotalSpent = total ?: 0.0,
                        upcomingTrips = upcoming,
                        pastTrips = past
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripListUiState()
        )

    init {
        viewModelScope.launch {
            uiState.collect { _isLoading.value = false }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            runCatching {
                tripRepository.deleteTrip(tripId)
            }
        }
    }
}

/**
 * UI-состояние домашнего списка поездок, разбитое на текущие, будущие и завершённые.
 */
data class TripListUiState(
    val currentTrip: Trip? = null,
    val currentTripPlaceCount: Int = 0,
    val currentTripTotalSpent: Double = 0.0,
    val upcomingTrips: List<Trip> = emptyList(),
    val pastTrips: List<Trip> = emptyList()
) {
    val hasTrips: Boolean
        get() = currentTrip != null || upcomingTrips.isNotEmpty() || pastTrips.isNotEmpty()
}
