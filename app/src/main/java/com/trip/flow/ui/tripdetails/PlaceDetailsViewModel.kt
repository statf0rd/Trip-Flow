package com.trip.flow.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.Place
import com.trip.flow.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {
    
    private val placeId: String = savedStateHandle.get<String>("placeId")
        ?: throw IllegalArgumentException("placeId is required")
    
    private val _uiState = MutableStateFlow(PlaceDetailsUiState())
    val uiState: StateFlow<PlaceDetailsUiState> = _uiState.asStateFlow()
    
    init {
        loadPlace()
    }
    
    private fun loadPlace() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Note: In a real app, we'd observe this from Room
                // For now, we'll do a one-time load
                val place = tripRepository.getPlaceById(placeId)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        place = place
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message
                    ) 
                }
            }
        }
    }
    
    fun toggleVisited() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch {
            tripRepository.markPlaceVisited(place.id, !place.isVisited)
            // Update local state
            _uiState.update { 
                it.copy(place = place.copy(isVisited = !place.isVisited)) 
            }
        }
    }
    
    fun deletePlace() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch {
            try {
                tripRepository.deletePlace(place.id)
                _uiState.update { it.copy(isDeleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

data class PlaceDetailsUiState(
    val place: Place? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null
)

