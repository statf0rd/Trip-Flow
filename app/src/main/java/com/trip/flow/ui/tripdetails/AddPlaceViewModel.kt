package com.trip.flow.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trip.flow.data.model.Place
import com.trip.flow.data.model.PlaceCategory
import com.trip.flow.data.model.TripDay
import com.trip.flow.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddPlaceViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId")
        ?: throw IllegalArgumentException("tripId is required")
    private val dayId: String = savedStateHandle.get<String>("dayId")
        ?: throw IllegalArgumentException("dayId is required")

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private val _day = MutableStateFlow<TripDay?>(null)
    val day: StateFlow<TripDay?> = _day.asStateFlow()

    init {
        // Preload day info for header
        viewModelScope.launch {
            _day.value = tripRepository.getTripDayById(dayId)
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun updateLatitude(value: String) {
        _uiState.update { it.copy(latitude = value) }
    }

    fun updateLongitude(value: String) {
        _uiState.update { it.copy(longitude = value) }
    }

    fun updateTime(value: String) {
        _uiState.update { it.copy(time = value) }
    }

    fun updateDuration(value: String) {
        _uiState.update { it.copy(durationMinutes = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateCategory(category: PlaceCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun savePlace() {
        val state = _uiState.value
        val lat = state.latitude.toDoubleOrNull() ?: return
        val lon = state.longitude.toDoubleOrNull() ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val place = Place(
                    tripId = tripId,
                    tripDayId = dayId,
                    name = state.name.trim(),
                    address = state.address.trim().ifBlank { null },
                    latitude = lat,
                    longitude = lon,
                    category = state.category,
                    iconEmoji = state.category.emoji,
                    scheduledTime = state.time.trim().ifBlank { null },
                    estimatedDuration = state.durationMinutes.toIntOrNull(),
                    notes = state.notes.trim().ifBlank { null }
                )
                tripRepository.addPlace(place)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Не удалось добавить место"
                    )
                }
            }
        }
    }
}

data class AddPlaceUiState(
    val name: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val time: String = "",
    val durationMinutes: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
                latitude.toDoubleOrNull() != null &&
                longitude.toDoubleOrNull() != null &&
                !isSaving
}
