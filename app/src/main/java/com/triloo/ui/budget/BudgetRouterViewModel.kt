package com.triloo.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Решает, куда отправить тап по табу «Бюджет»:
 *   1) текущая поездка (startDate ≤ today ≤ endDate);
 *   2) ближайшая предстоящая (минимальный startDate > today);
 *   3) ни одной — оставляем экран-заглушку.
 */
@HiltViewModel
class BudgetRouterViewModel @Inject constructor(
    tripRepository: TripRepository
) : ViewModel() {

    val state: StateFlow<BudgetRouteState> = combine(
        tripRepository.observeCurrentTrip(),
        tripRepository.observeUpcomingTrips()
    ) { current, upcoming ->
        when {
            current != null -> BudgetRouteState.OpenTrip(current.id)
            upcoming.isNotEmpty() -> BudgetRouteState.OpenTrip(upcoming.first().id)
            else -> BudgetRouteState.Empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetRouteState.Loading
    )
}

sealed interface BudgetRouteState {
    data object Loading : BudgetRouteState
    data class OpenTrip(val tripId: String) : BudgetRouteState
    data object Empty : BudgetRouteState
}
