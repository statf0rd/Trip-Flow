package com.trip.flow.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.Trip
import com.trip.flow.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CreateTripUiState())
    val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()
    
    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }
    
    fun updateDestination(destination: String) {
        _uiState.update { it.copy(destination = destination) }
    }
    
    fun updateStartDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(
                startDate = date,
                // If end date is before start date, reset it
                endDate = if (state.endDate != null && state.endDate.isBefore(date)) {
                    date.plusDays(1)
                } else state.endDate
            )
        }
    }
    
    fun updateEndDate(date: LocalDate) {
        _uiState.update { it.copy(endDate = date) }
    }
    
    fun updateCurrency(currency: String) {
        _uiState.update { it.copy(baseCurrency = currency) }
    }
    
    fun updateHotelName(name: String) {
        _uiState.update { it.copy(hotelName = name) }
    }
    
    fun updateBudget(budget: Double?) {
        _uiState.update { it.copy(budget = budget) }
    }
    
    fun createTrip() {
        val state = _uiState.value
        if (!state.isValid) return
        
        _uiState.update { it.copy(isCreating = true) }
        
        viewModelScope.launch {
            try {
                val trip = Trip(
                    name = state.name.trim(),
                    destination = state.destination.trim(),
                    startDate = state.startDate!!,
                    endDate = state.endDate!!,
                    baseCurrency = state.baseCurrency,
                    hotelName = state.hotelName.takeIf { it.isNotBlank() },
                    budget = state.budget
                )
                
                val tripId = tripRepository.createTrip(trip)
                _uiState.update { it.copy(isCreating = false, createdTripId = tripId) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isCreating = false, 
                        error = e.message ?: "Ошибка создания поездки"
                    ) 
                }
            }
        }
    }
}

data class CreateTripUiState(
    val name: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String = "RUB",
    val hotelName: String = "",
    val budget: Double? = null,
    
    val isCreating: Boolean = false,
    val createdTripId: String? = null,
    val error: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() && 
                destination.isNotBlank() && 
                startDate != null && 
                endDate != null &&
                !endDate.isBefore(startDate)
}

