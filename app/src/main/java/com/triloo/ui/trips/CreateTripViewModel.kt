package com.triloo.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.accommodation.AccommodationRecommendation
import com.triloo.data.accommodation.AccommodationRecommendationService
import com.triloo.data.accommodation.AccommodationRequest
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Trip
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.places.PlacesService
import com.triloo.data.repository.TripRepository
import com.triloo.data.user.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val userProfileRepository: UserProfileRepository,
    private val accommodationRecommendationService: AccommodationRecommendationService,
    private val placesService: PlacesService,
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

    private val _hotelSearchQuery = MutableStateFlow("")
    private val _hotelSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val hotelSuggestions: StateFlow<List<PlaceSuggestion>> = _hotelSuggestions.asStateFlow()

    private val _destinationQuery = MutableStateFlow("")
    private val _destinationSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val destinationSuggestions: StateFlow<List<PlaceSuggestion>> = _destinationSuggestions.asStateFlow()

    init {
        viewModelScope.launch {
            _hotelSearchQuery
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length < 2) {
                        _hotelSuggestions.value = emptyList()
                        _uiState.update { it.copy(isSearchingHotel = false) }
                    } else {
                        searchHotel(query)
                    }
                }
        }
        viewModelScope.launch {
            _destinationQuery
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length < 2) {
                        _destinationSuggestions.value = emptyList()
                        _uiState.update { it.copy(isSearchingDestination = false) }
                    } else {
                        searchDestination(query)
                    }
                }
        }
        if (editTripId != null) {
            viewModelScope.launch {
                val trip = tripRepository.getTripById(editTripId) ?: return@launch
                editingTrip = trip
                _uiState.update { state ->
                    state.copy(
                        name = trip.name,
                        destination = trip.destination,
                        destinationLatitude = trip.destinationLatitude,
                        destinationLongitude = trip.destinationLongitude,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        baseCurrency = trip.baseCurrency,
                        hotelName = trip.hotelName.orEmpty(),
                        hotelAddress = trip.hotelAddress.orEmpty(),
                        hotelPlaceId = trip.hotelPlaceId,
                        hotelLatitude = trip.hotelLatitude,
                        hotelLongitude = trip.hotelLongitude,
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
        _uiState.update { state ->
            val shouldClearSelectedHotel = state.hotelPlaceId != null
            state.copy(
                destination = destination,
                destinationLatitude = null,
                destinationLongitude = null,
                hotelName = if (shouldClearSelectedHotel) "" else state.hotelName,
                hotelAddress = if (shouldClearSelectedHotel) "" else state.hotelAddress,
                hotelPlaceId = if (shouldClearSelectedHotel) null else state.hotelPlaceId,
                hotelLatitude = if (shouldClearSelectedHotel) null else state.hotelLatitude,
                hotelLongitude = if (shouldClearSelectedHotel) null else state.hotelLongitude,
                hotelRecommendations = emptyList(),
                showHotelRecommendationsSheet = false
            )
        }
        _destinationQuery.value = destination
        if (destination.isBlank()) {
            _destinationSuggestions.value = emptyList()
        }
    }
    
    fun updateStartDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(
                startDate = date,
                // Если дата окончания оказалась раньше даты начала, сбрасываем её.
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
        _uiState.update { state ->
            val sameValue = name == state.hotelName
            state.copy(
                hotelName = name,
                hotelAddress = if (sameValue) state.hotelAddress else "",
                hotelPlaceId = if (sameValue) state.hotelPlaceId else null,
                hotelLatitude = if (sameValue) state.hotelLatitude else null,
                hotelLongitude = if (sameValue) state.hotelLongitude else null
            )
        }
        _hotelSearchQuery.value = name
        if (name.isBlank()) {
            _hotelSuggestions.value = emptyList()
        }
    }

    fun selectHotelSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update {
            it.copy(
                hotelName = suggestion.name,
                hotelAddress = suggestion.address,
                hotelPlaceId = suggestion.placeId,
                hotelLatitude = suggestion.latitude,
                hotelLongitude = suggestion.longitude,
                isSearchingHotel = false
            )
        }
        _hotelSuggestions.value = emptyList()
        _hotelSearchQuery.value = ""
    }

    fun clearHotelSuggestions() {
        _hotelSuggestions.value = emptyList()
    }

    fun selectDestinationSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update {
            it.copy(
                destination = suggestion.name,
                destinationLatitude = suggestion.latitude,
                destinationLongitude = suggestion.longitude,
                isSearchingDestination = false
            )
        }
        _destinationSuggestions.value = emptyList()
        _destinationQuery.value = ""
    }

    fun updateDestinationCoordinates(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                hotelLatitude = it.hotelLatitude ?: latitude,
                hotelLongitude = it.hotelLongitude ?: longitude,
                destinationLatitude = latitude,
                destinationLongitude = longitude
            )
        }
    }
    
    fun updateBudget(input: String) {
        _uiState.update { it.copy(budgetInput = sanitizeBudgetInput(input)) }
    }

    fun updateIsGroupTrip(isGroupTrip: Boolean) {
        _uiState.update { it.copy(isGroupTrip = isGroupTrip) }
    }

    fun requestHotelRecommendations() {
        val state = _uiState.value
        val budget = state.budget
        val startDate = state.startDate
        val endDate = state.endDate
        when {
            state.destination.isBlank() -> {
                _uiState.update { it.copy(error = "Сначала укажите город или страну") }
                return
            }
            budget == null || budget <= 0.0 -> {
                _uiState.update { it.copy(error = "Сначала укажите бюджет для подбора жилья") }
                return
            }
            startDate == null || endDate == null -> {
                _uiState.update { it.copy(error = "Сначала укажите даты поездки") }
                return
            }
            endDate.isBefore(startDate) -> {
                _uiState.update { it.copy(error = "Проверьте интервал поездки") }
                return
            }
        }

        _uiState.update {
            it.copy(
                isLoadingHotelRecommendations = true,
                hotelRecommendations = emptyList(),
                showHotelRecommendationsSheet = false
            )
        }

        viewModelScope.launch {
            runCatching {
                accommodationRecommendationService.recommend(
                    AccommodationRequest(
                        destination = state.destination.trim(),
                        budget = budget,
                        currency = state.baseCurrency,
                        startDate = startDate,
                        endDate = endDate
                    )
                )
            }.onSuccess { recommendations ->
                _uiState.update {
                    it.copy(
                        isLoadingHotelRecommendations = false,
                        hotelRecommendations = recommendations,
                        showHotelRecommendationsSheet = recommendations.isNotEmpty(),
                        error = if (recommendations.isEmpty()) {
                            "Не удалось найти реальные варианты жилья по текущим параметрам"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingHotelRecommendations = false,
                        error = throwable.message ?: "Ошибка подбора жилья"
                    )
                }
            }
        }
    }

    fun dismissHotelRecommendations() {
        _uiState.update { it.copy(showHotelRecommendationsSheet = false) }
    }

    fun applyHotelRecommendation(recommendation: AccommodationRecommendation) {
        _uiState.update {
            it.copy(
                hotelName = recommendation.name,
                hotelAddress = recommendation.address,
                hotelPlaceId = recommendation.placeId,
                hotelLatitude = recommendation.latitude,
                hotelLongitude = recommendation.longitude,
                showHotelRecommendationsSheet = false
            )
        }
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
                        destinationLatitude = state.destinationLatitude,
                        destinationLongitude = state.destinationLongitude,
                        startDate = state.startDate!!,
                        endDate = state.endDate!!,
                        baseCurrency = state.baseCurrency,
                        hotelName = state.hotelName.takeIf { it.isNotBlank() },
                        hotelAddress = state.hotelAddress.takeIf { it.isNotBlank() },
                        hotelPlaceId = state.hotelPlaceId,
                        hotelLatitude = state.hotelLatitude,
                        hotelLongitude = state.hotelLongitude,
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
                        destinationLatitude = state.destinationLatitude,
                        destinationLongitude = state.destinationLongitude,
                        startDate = state.startDate!!,
                        endDate = state.endDate!!,
                        baseCurrency = state.baseCurrency,
                        hotelName = state.hotelName.takeIf { it.isNotBlank() },
                        hotelAddress = state.hotelAddress.takeIf { it.isNotBlank() },
                        hotelPlaceId = state.hotelPlaceId,
                        hotelLatitude = state.hotelLatitude,
                        hotelLongitude = state.hotelLongitude,
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

    private suspend fun searchHotel(query: String) {
        _uiState.update { it.copy(isSearchingHotel = true) }
        try {
            val state = _uiState.value
            val results = placesService.searchPlaces(
                query = "${state.destination} $query".trim(),
                latitude = state.destinationLatitude,
                longitude = state.destinationLongitude
            )
            _hotelSuggestions.value = results
        } catch (_: Exception) {
            _hotelSuggestions.value = emptyList()
        } finally {
            _uiState.update { it.copy(isSearchingHotel = false) }
        }
    }

    private suspend fun searchDestination(query: String) {
        _uiState.update { it.copy(isSearchingDestination = true) }
        try {
            val results = placesService.searchPlaces(query = query)
            _destinationSuggestions.value = results
        } catch (_: Exception) {
            _destinationSuggestions.value = emptyList()
        } finally {
            _uiState.update { it.copy(isSearchingDestination = false) }
        }
    }

    private fun sanitizeBudgetInput(input: String): String {
        val normalized = input.replace(',', '.')
        val dotIndex = normalized.indexOf('.')
        return if (dotIndex < 0) {
            normalized.filter { it.isDigit() }
        } else {
            val wholePart = normalized.substring(0, dotIndex).filter { it.isDigit() }
            val decimalPart = normalized.substring(dotIndex + 1).filter { it.isDigit() }.take(2)
            "$wholePart.$decimalPart"
        }
    }

    private fun formatBudgetInput(budget: Double?): String {
        if (budget == null) return ""
        val long = budget.toLong()
        return if (budget == long.toDouble()) long.toString()
        else String.format("%.2f", budget).trimEnd('0').trimEnd('.')
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
    val hotelAddress: String = "",
    val hotelPlaceId: String? = null,
    val hotelLatitude: Double? = null,
    val hotelLongitude: Double? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val budgetInput: String = "",
    val isGroupTrip: Boolean = false,
    val isEditing: Boolean = false,
    val hotelRecommendations: List<AccommodationRecommendation> = emptyList(),
    val isLoadingHotelRecommendations: Boolean = false,
    val isSearchingHotel: Boolean = false,
    val isSearchingDestination: Boolean = false,
    val showHotelRecommendationsSheet: Boolean = false,
    
    val isCreating: Boolean = false,
    val createdTripId: String? = null,
    val error: String? = null
) {
    val budget: Double?
        get() = budgetInput.toDoubleOrNull()

    val isValid: Boolean
        get() = name.isNotBlank() && 
                destination.isNotBlank() && 
                startDate != null && 
                endDate != null &&
                !endDate.isBefore(startDate)
}
