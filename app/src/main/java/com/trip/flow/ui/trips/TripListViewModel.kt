package com.trip.flow.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.Trip
import com.trip.flow.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    val uiState: StateFlow<TripListUiState> = combine(
        tripRepository.observeCurrentTrip(),
        tripRepository.observeUpcomingTrips(),
        tripRepository.observePastTrips()
    ) { current, upcoming, past ->
        _isLoading.value = false
        TripListUiState(
            currentTrip = current,
            upcomingTrips = upcoming,
            pastTrips = past
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TripListUiState()
    )
    
    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }
}

data class TripListUiState(
    val currentTrip: Trip? = null,
    val upcomingTrips: List<Trip> = emptyList(),
    val pastTrips: List<Trip> = emptyList()
) {
    val hasTrips: Boolean
        get() = currentTrip != null || upcomingTrips.isNotEmpty() || pastTrips.isNotEmpty()
}

