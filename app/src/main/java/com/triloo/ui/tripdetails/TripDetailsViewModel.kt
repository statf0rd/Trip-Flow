package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.*
import com.triloo.data.route.RouteDetails
import com.triloo.data.route.RouteOptimizer
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val routeOptimizer: RouteOptimizer
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

    private val balancesState: Flow<List<Balance>> =
        expenseRepository.observeExpensesByTrip(tripId)
            .mapLatest {
                withContext(Dispatchers.IO) {
                    expenseRepository.calculateBalances(tripId)
                }
            }
            .catch { emit(emptyList()) }

    private val routeState: Flow<RouteDetails?> = tripPlanState
        .mapLatest { plan ->
            val ordered = orderPlacesForRoute(plan.days, plan.places)
            if (ordered.size < 2) return@mapLatest null
            withContext(Dispatchers.IO) {
                routeOptimizer.calculateRoute(ordered)
            }
        }
        .catch { emit(null) }

    val uiState: StateFlow<TripDetailsUiState> = combine(
        tripPlanState,
        expenseState,
        balancesState,
        routeState,
        _isDeleted
    ) { plan, expense, balances, routeDetails, isDeleted ->
        TripDetailsUiState(
            trip = plan.trip,
            days = plan.days,
            places = plan.places,
            participants = plan.participants,
            expenses = expense.expenses,
            totalExpenses = expense.totalExpenses,
            balances = balances,
            routeDetails = routeDetails,
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
        viewModelScope.launch {
            runCatching {
                val trip = tripRepository.getTripById(tripId) ?: return@runCatching
                val days = tripRepository.getTripDays(tripId)
                val places = tripRepository.getPlacesByTrip(tripId)
                val startLocation = trip.hotelLatitude?.let { lat ->
                    trip.hotelLongitude?.let { lon -> com.triloo.data.route.LatLng(lat, lon) }
                }

                days.forEach { day ->
                    val dayPlaces = places.filter { it.tripDayId == day.id }
                    if (dayPlaces.size < 2) return@forEach
                    val result = routeOptimizer.optimizeRoute(dayPlaces, startLocation)
                    val orderedIds = result.optimizedPlaces.map { it.id }
                    tripRepository.reorderPlaces(day.id, orderedIds)
                }
            }
        }
    }

    private fun orderPlacesForRoute(
        days: List<TripDay>,
        places: List<Place>
    ): List<Place> {
        if (places.isEmpty()) return emptyList()
        val dayOrder = days.sortedBy { it.dayNumber }.mapIndexed { index, day ->
            day.id to index
        }.toMap()
        return places.sortedWith(
            compareBy<Place> { dayOrder[it.tripDayId] ?: Int.MAX_VALUE }
                .thenBy { it.orderIndex }
        )
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
    val routeDetails: RouteDetails? = null,
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)
