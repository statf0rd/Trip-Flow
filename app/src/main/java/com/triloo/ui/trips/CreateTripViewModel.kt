package com.triloo.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Trip
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
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
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val initialIsGroupTrip: Boolean =
        savedStateHandle.get<Boolean>("isGroupTrip") ?: false
    private val editTripId: String? = savedStateHandle.get<String>("tripId")

    private var editingTrip: Trip? = null

    private val defaultStartDate = if (editTripId == null) LocalDate.now() else null
    private val defaultEndDate = if (editTripId == null) LocalDate.now().plusDays(1) else null

    private val _uiState = MutableStateFlow(
        CreateTripUiState(
            isGroupTrip = initialIsGroupTrip,
            tripId = editTripId,
            isEditing = editTripId != null,
            startDate = defaultStartDate,
            endDate = defaultEndDate
        )
    )
    val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

    init {
        if (editTripId != null) {
            viewModelScope.launch {
                val trip = tripRepository.getTripById(editTripId) ?: return@launch
                editingTrip = trip
                _uiState.update { state ->
                    state.copy(
                        name = trip.name,
                        destination = trip.destination,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        baseCurrency = trip.baseCurrency,
                        hotelName = trip.hotelName.orEmpty(),
                        budgetInput = formatBudgetInput(trip.budget),
                        isGroupTrip = trip.isGroupTrip
                    )
                }
            }
        }
    }
    
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
    
    fun updateBudget(input: String) {
        _uiState.update { it.copy(budgetInput = sanitizeBudgetInput(input)) }
    }

    fun updateIsGroupTrip(isGroupTrip: Boolean) {
        _uiState.update { it.copy(isGroupTrip = isGroupTrip) }
    }
    
    fun saveTrip() {
        val state = _uiState.value
        if (!state.isValid) return
        
        _uiState.update { it.copy(isCreating = true) }
        
        viewModelScope.launch {
            try {
                val tripId = if (state.isEditing) {
                    val baseTrip = editingTrip ?: tripRepository.getTripById(state.tripId ?: "")
                    if (baseTrip == null) {
                        _uiState.update {
                            it.copy(
                                isCreating = false,
                                error = "Поездка не найдена"
                            )
                        }
                        return@launch
                    }
                    val updatedTrip = baseTrip.copy(
                        name = state.name.trim(),
                        destination = state.destination.trim(),
                        startDate = state.startDate!!,
                        endDate = state.endDate!!,
                        baseCurrency = state.baseCurrency,
                        hotelName = state.hotelName.takeIf { it.isNotBlank() },
                        budget = state.budget
                    )
                    tripRepository.updateTrip(updatedTrip)
                    updatedTrip.id
                } else {
                    val profile = userProfileRepository.getProfile()
                    val ownerId = profile.userId
                    val trip = Trip(
                        name = state.name.trim(),
                        destination = state.destination.trim(),
                        startDate = state.startDate!!,
                        endDate = state.endDate!!,
                        baseCurrency = state.baseCurrency,
                        hotelName = state.hotelName.takeIf { it.isNotBlank() },
                        budget = state.budget,
                        isGroupTrip = state.isGroupTrip,
                        ownerId = if (state.isGroupTrip) ownerId else null
                    )

                    val newTripId = tripRepository.createTrip(trip)

                    if (state.isGroupTrip) {
                        val displayName = profile.displayName.ifBlank { "Участник" }
                        tripRepository.addParticipant(
                            Participant(
                                tripId = newTripId,
                                userId = ownerId,
                                displayName = displayName,
                                role = ParticipantRole.OWNER
                            )
                        )
                    }
                    newTripId
                }
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun sanitizeBudgetInput(input: String): String {
        val normalized = input.replace(',', '.')
        val wholePart = normalized.substringBefore('.')
        return wholePart.filter { it.isDigit() }
    }

    private fun formatBudgetInput(budget: Double?): String {
        if (budget == null) return ""
        return budget.toLong().toString()
    }
}

data class CreateTripUiState(
    val tripId: String? = null,
    val name: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String = "RUB",
    val hotelName: String = "",
    val budgetInput: String = "",
    val isGroupTrip: Boolean = false,
    val isEditing: Boolean = false,
    
    val isCreating: Boolean = false,
    val createdTripId: String? = null,
    val error: String? = null
) {
    val budget: Double?
        get() = budgetInput.toLongOrNull()?.toDouble()

    val isValid: Boolean
        get() = name.isNotBlank() && 
                destination.isNotBlank() && 
                startDate != null && 
                endDate != null &&
                !endDate.isBefore(startDate)
}
