package com.trip.flow.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.*
import com.trip.flow.data.repository.ExpenseRepository
import com.trip.flow.data.repository.TripRepository
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
    
    private val _isLoading = MutableStateFlow(true)
    
    val uiState: StateFlow<TripDetailsUiState> = combine(
        tripRepository.observeTripById(tripId),
        tripRepository.observeTripDays(tripId),
        tripRepository.observePlacesByTrip(tripId),
        tripRepository.observeParticipants(tripId),
        expenseRepository.observeExpensesByTrip(tripId),
        expenseRepository.observeTotalExpenses(tripId)
    ) { flows ->
        _isLoading.value = false
        TripDetailsUiState(
            trip = flows[0] as Trip?,
            days = (flows[1] as List<*>).filterIsInstance<TripDay>(),
            places = (flows[2] as List<*>).filterIsInstance<Place>(),
            participants = (flows[3] as List<*>).filterIsInstance<Participant>(),
            expenses = (flows[4] as List<*>).filterIsInstance<Expense>(),
            totalExpenses = (flows[5] as? Double) ?: 0.0,
            isLoading = false
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
    
    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()
    
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

