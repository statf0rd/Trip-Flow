package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Place
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
                val place = tripRepository.getPlaceById(placeId)
                if (place != null) {
                    val trip = tripRepository.getTripById(place.tripId)
                    val day = tripRepository.getTripDayById(place.tripDayId)
                    val distance = computeDistanceFromHotelMeters(trip, place)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            place = place,
                            trip = trip,
                            day = day,
                            distanceFromHotelMeters = distance
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, place = null) }
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
            runCatching {
                tripRepository.markPlaceVisited(place.id, !place.isVisited)
            }.onSuccess {
                _uiState.update {
                    it.copy(place = place.copy(isVisited = !place.isVisited))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
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

    /**
     * Расстояние «от жилья» — гаверсинус от координат отеля поездки до места.
     * Возвращает null, если у поездки нет отеля или у места нет координат.
     */
    private fun computeDistanceFromHotelMeters(trip: Trip?, place: Place): Int? {
        val hotelLat = trip?.hotelLatitude ?: return null
        val hotelLon = trip.hotelLongitude ?: return null
        if (place.latitude == 0.0 && place.longitude == 0.0) return null
        val r = 6371000.0
        val dLat = Math.toRadians(place.latitude - hotelLat)
        val dLon = Math.toRadians(place.longitude - hotelLon)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(hotelLat)) *
            cos(Math.toRadians(place.latitude)) *
            sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toInt()
    }
}

data class PlaceDetailsUiState(
    val place: Place? = null,
    val trip: Trip? = null,
    val day: TripDay? = null,
    val distanceFromHotelMeters: Int? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null
)
