package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.*
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    
    private val tripId: String = savedStateHandle.get<String>("tripId") 
        ?: throw IllegalArgumentException("tripId is required")
    
    private val _isDeleted = MutableStateFlow(false)

    private val tripPlanState: Flow<TripPlanState> = combine(
        tripRepository.observeTripById(tripId),
        tripRepository.observeTripDays(tripId),
        tripRepository.observePlacesByTrip(tripId),
        tripRepository.observeParticipants(tripId)
    ) { trip, days, places, participants ->
        TripPlanState(
            trip = trip,
            days = days,
            places = places,
            participants = participants
        )
    }

    private val expenseState: Flow<ExpenseState> = combine(
        expenseRepository.observeExpensesByTrip(tripId),
        expenseRepository.observeTotalExpenses(tripId)
    ) { expenses, total ->
        ExpenseState(
            expenses = expenses,
            totalExpenses = total ?: 0.0
        )
    }

    val uiState: StateFlow<TripDetailsUiState> = combine(
        tripPlanState,
        expenseState,
        _isDeleted
    ) { plan, expense, isDeleted ->
        TripDetailsUiState(
            trip = plan.trip,
            days = plan.days,
            places = plan.places,
            participants = plan.participants,
            expenses = expense.expenses,
            totalExpenses = expense.totalExpenses,
            isLoading = false,
            isDeleted = isDeleted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TripDetailsUiState(isLoading = true)
    )
    
    fun markPlaceVisited(placeId: String, visited: Boolean) {
        viewModelScope.launch {
            tripRepository.markPlaceVisited(placeId, visited)
        }
    }
    
    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            tripRepository.deletePlace(placeId)
        }
    }
    
    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expenseId)
        }
    }
    
    fun deleteTrip() {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            _isDeleted.value = true
        }
    }
    
    fun optimizeRoute() {
        // TODO: Implement route optimization
        // This will be implemented in RouteOptimizer service
        viewModelScope.launch {
            // For now, just reorder places by proximity (nearest neighbor heuristic)
            // Will be enhanced with AI/ML later
        }
    }
}

private data class TripPlanState(
    val trip: Trip?,
    val days: List<TripDay>,
    val places: List<Place>,
    val participants: List<Participant>
)

private data class ExpenseState(
    val expenses: List<Expense>,
    val totalExpenses: Double
)

data class TripDetailsUiState(
    val trip: Trip? = null,
    val days: List<TripDay> = emptyList(),
    val places: List<Place> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val totalExpenses: Double = 0.0,
    val balances: List<Balance> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)
