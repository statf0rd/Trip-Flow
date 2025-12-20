package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TripDay
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.places.PlacesService
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddPlaceViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val placesService: PlacesService,
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
    
    private val _searchQuery = MutableStateFlow("")
    
    private val _suggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val suggestions: StateFlow<List<PlaceSuggestion>> = _suggestions.asStateFlow()

    init {
        // Preload day info for header
        viewModelScope.launch {
            _day.value = tripRepository.getTripDayById(dayId)
        }
        
        // Setup search debounce
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    searchPlaces(query)
                }
        }
    }
    
    private suspend fun searchPlaces(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        try {
            val results = placesService.searchPlaces(query)
            _suggestions.value = results
        } catch (e: Exception) {
            _suggestions.value = emptyList()
        } finally {
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
        _searchQuery.value = value
        
        // If user clears the field, clear suggestions
        if (value.isBlank()) {
            _suggestions.value = emptyList()
        }
    }
    
    fun selectSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { state ->
            state.copy(
                name = suggestion.name,
                address = suggestion.address,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                category = suggestion.category,
                rating = suggestion.rating,
                selectedPlaceId = suggestion.placeId
            )
        }
        // Clear suggestions after selection
        _suggestions.value = emptyList()
        _searchQuery.value = ""
    }
    
    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
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
        
        // Validate coordinates
        if (state.latitude == 0.0 && state.longitude == 0.0) {
            _uiState.update { it.copy(error = "Выберите место из подсказок или укажите координаты") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val place = Place(
                    tripId = tripId,
                    tripDayId = dayId,
                    name = state.name.trim(),
                    address = state.address.trim().ifBlank { null },
                    placeId = state.selectedPlaceId,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    category = state.category,
                    iconEmoji = state.category.emoji,
                    scheduledTime = state.time.trim().ifBlank { null },
                    estimatedDuration = state.durationMinutes.toIntOrNull(),
                    rating = state.rating,
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
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val time: String = "",
    val durationMinutes: String = "",
    val notes: String = "",
    val rating: Float? = null,
    val selectedPlaceId: String? = null,
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
                (latitude != 0.0 || longitude != 0.0) &&
                !isSaving
    
    val hasCoordinates: Boolean
        get() = latitude != 0.0 || longitude != 0.0
}
