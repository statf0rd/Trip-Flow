package com.triloo.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import java.util.Locale
import com.triloo.data.location.LocationSharingServiceController
import com.triloo.data.model.*
import com.triloo.data.places.PlacesService
import com.triloo.data.route.LatLng
import com.triloo.data.route.PlaceRecommendation
import com.triloo.data.route.RouteDetails
import com.triloo.data.route.RoutePlanSource
import com.triloo.data.route.RoutePlanSuggestion
import com.triloo.data.route.RoutePlanningAssistant
import com.triloo.data.route.RoutePlanningMode
import com.triloo.data.route.RouteOptimizer
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.repository.TripRepository
import com.triloo.data.sync.OnlineSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Собирает состояние деталей поездки: план, карту, расходы, балансы и оптимизированный маршрут.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val routeOptimizer: RouteOptimizer,
    private val routePlanningAssistant: RoutePlanningAssistant,
    private val placesService: PlacesService,
    private val onlineSyncRepository: OnlineSyncRepository,
    private val locationSharingServiceController: LocationSharingServiceController,
    private val locationSharingManager: com.triloo.data.location.LocationSharingManager
) : ViewModel() {
    companion object {
        private const val TAG = "TripDetailsViewModel"
    }
    
    private val tripId: String = savedStateHandle.get<String>("tripId") 
        ?: throw IllegalArgumentException("tripId is required")
    
    private val _isDeleted = MutableStateFlow(false)
    private val _actionError = MutableStateFlow<String?>(null)
    private val _selectedPlanningMode = MutableStateFlow(RoutePlanningMode.CLASSIC)
    private val _selectedTravelMode = MutableStateFlow(TravelMode.WALKING)
    private var participantRefreshJob: Job? = null

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
                expenseRepository.calculateBalances(tripId)
            }
            .flowOn(Dispatchers.IO)
            .catch { emit(emptyList()) }

    private val planningSuggestionState: StateFlow<RoutePlanSuggestion?> = combine(
        tripPlanState,
        _selectedPlanningMode
    ) { plan, planningMode ->
        PlanningInputs(
            trip = plan.trip,
            days = plan.days,
            // Места без координат не должны попадать ни в эвристику, ни в AI-план,
            // иначе расстояние от (0,0) до реальной точки даёт сотни/тысячи км
            // и подсказка маршрута показывает мусор вроде «7066 км · пешком».
            places = plan.places.filter { it.latitude != 0.0 || it.longitude != 0.0 },
            planningMode = planningMode
        )
    }.mapLatest { input ->
        routePlanningAssistant.planRoute(
            trip = input.trip,
            days = input.days,
            places = input.places,
            preferAi = input.planningMode == RoutePlanningMode.AI_ASSISTED
        )
    }.flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val routeInputs: Flow<RouteInputs> = combine(
        tripPlanState,
        planningSuggestionState,
        _selectedTravelMode,
        _selectedPlanningMode
    ) { plan, planningSuggestion, travelMode, planningMode ->
        RouteInputs(
            days = plan.days,
            places = plan.places,
            travelMode = travelMode,
            planningMode = planningMode,
            planningSuggestion = planningSuggestion
        )
    }

    private val routeState: Flow<RouteDetails?> = routeInputs
        .mapLatest { input ->
            val ordered = resolveOrderedPlaces(
                days = input.days,
                places = input.places,
                planningMode = input.planningMode,
                planningSuggestion = input.planningSuggestion
            )
            if (ordered.size < 2) return@mapLatest null
            routeOptimizer.calculateRoute(ordered, input.travelMode)
        }
        .flowOn(Dispatchers.IO)
        .catch { emit(null) }

    private val recommendationState: Flow<List<PlaceRecommendation>> = combine(
        tripPlanState,
        planningSuggestionState,
        _selectedTravelMode,
        _selectedPlanningMode
    ) { plan, planningSuggestion, travelMode, planningMode ->
        RecommendationInputs(
            trip = plan.trip,
            orderedPlaces = resolveOrderedPlaces(
                days = plan.days,
                places = plan.places,
                planningMode = planningMode,
                planningSuggestion = planningSuggestion
            ),
            travelMode = travelMode
        )
    }.mapLatest { input ->
        val center = resolveRecommendationCenter(input.trip, input.orderedPlaces) ?: return@mapLatest emptyList()
        routeOptimizer.getRecommendations(
            currentPlaces = input.orderedPlaces,
            center = center,
            radius = recommendationRadius(input.travelMode)
        )
    }.flowOn(Dispatchers.Main)
    .catch { emit(emptyList()) }

    private val destinationMarkerState: StateFlow<DestinationMapMarker?> = combine(
        tripRepository.observeTripById(tripId),
        tripRepository.observePlacesByTrip(tripId)
    ) { trip, places ->
        trip to places
    }.mapLatest { (trip, places) ->
        val currentTrip = trip ?: return@mapLatest null
        val hasMappedPlaces = places.any { it.latitude != 0.0 && it.longitude != 0.0 }
        val hasHotelCoordinates = currentTrip.hotelLatitude != null && currentTrip.hotelLongitude != null
        val destination = currentTrip.destination.trim()

        if (destination.isBlank() || hasMappedPlaces || hasHotelCoordinates) {
            return@mapLatest null
        }

        // Если координаты destination уже сохранены в Trip, используем их.
        val destLat = currentTrip.destinationLatitude
        val destLon = currentTrip.destinationLongitude
        if (destLat != null && destLon != null) {
            return@mapLatest DestinationMapMarker(
                placeId = "destination:${currentTrip.id}",
                name = destination,
                address = destination,
                latitude = destLat,
                longitude = destLon
            )
        }

        placesService.searchPlaces(destination).firstOrNull()?.let { suggestion ->
            DestinationMapMarker(
                placeId = suggestion.placeId,
                name = suggestion.name.ifBlank { destination },
                address = suggestion.address.ifBlank { destination },
                latitude = suggestion.latitude,
                longitude = suggestion.longitude
            )
        }
    }.flowOn(Dispatchers.Main)
    .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val locationSharingState: Flow<LocationSharingUiState> =
        locationSharingServiceController.sessionState
            .map { session ->
                if (session.tripId == tripId) {
                    LocationSharingUiState(
                        isActive = session.isActive,
                        statusMessage = session.statusMessage,
                        error = session.error
                    )
                } else {
                    LocationSharingUiState()
                }
            }

    private val mapState: Flow<MapUiState> = combine(
        combine(
            routeState,
            recommendationState,
            planningSuggestionState,
            _selectedTravelMode,
            _selectedPlanningMode
        ) { routeDetails, recommendations, planningSuggestion, travelMode, planningMode ->
            MapUiState(
                routeDetails = routeDetails,
                recommendations = recommendations,
                selectedTravelMode = travelMode,
                selectedPlanningMode = planningMode,
                suggestedTravelMode = planningSuggestion?.suggestedTravelMode,
                routePlanningSummary = planningSuggestion?.summary,
                routePlanningSource = planningSuggestion?.source
            )
        },
        destinationMarkerState,
        locationSharingState
    ) { map, destinationMarker, locationSharing ->
        map.copy(
            destinationMarker = destinationMarker,
            isLocationSharingActive = locationSharing.isActive,
            locationSharingStatus = locationSharing.statusMessage,
            locationSharingError = locationSharing.error
        )
    }

    val uiState: StateFlow<TripDetailsUiState> = combine(
        combine(
            tripPlanState,
            expenseState,
            balancesState,
            mapState
        ) { plan, expense, balances, map ->
            PartialTripDetailsUiState(
                trip = plan.trip,
                days = plan.days,
                places = plan.places,
                participants = plan.participants,
                expenses = expense.expenses,
                totalExpenses = expense.totalExpenses,
                balances = balances,
                routeDetails = map.routeDetails,
                recommendations = map.recommendations,
                destinationMarker = map.destinationMarker,
                selectedTravelMode = map.selectedTravelMode,
                selectedPlanningMode = map.selectedPlanningMode,
                suggestedTravelMode = map.suggestedTravelMode,
                routePlanningSummary = map.routePlanningSummary,
                routePlanningSource = map.routePlanningSource,
                isLocationSharingActive = map.isLocationSharingActive,
                locationSharingStatus = map.locationSharingStatus,
                locationSharingError = map.locationSharingError
            )
        },
        _isDeleted,
        _actionError
    ) { partial, isDeleted, error ->
        TripDetailsUiState(
            trip = partial.trip,
            days = partial.days,
            places = partial.places,
            participants = partial.participants,
            expenses = partial.expenses,
            totalExpenses = partial.totalExpenses,
            balances = partial.balances,
            routeDetails = partial.routeDetails,
            recommendations = partial.recommendations,
            destinationMarker = partial.destinationMarker,
            selectedTravelMode = partial.selectedTravelMode,
            selectedPlanningMode = partial.selectedPlanningMode,
            suggestedTravelMode = partial.suggestedTravelMode,
            routePlanningSummary = partial.routePlanningSummary,
            routePlanningSource = partial.routePlanningSource,
            isLocationSharingActive = partial.isLocationSharingActive,
            locationSharingStatus = partial.locationSharingStatus,
            locationSharingError = partial.locationSharingError,
            isLoading = false,
            isDeleted = isDeleted,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TripDetailsUiState(isLoading = true)
    )

    fun markPlaceVisited(placeId: String, visited: Boolean) {
        viewModelScope.launch {
            runCatching {
                tripRepository.markPlaceVisited(placeId, visited)
            }.onFailure { error ->
                Log.w(TAG, "markPlaceVisited failed", error)
                _actionError.value = error.message ?: "Не удалось обновить место"
            }
        }
    }
    
    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            runCatching {
                tripRepository.deletePlace(placeId)
            }.onFailure { error ->
                Log.w(TAG, "deletePlace failed", error)
                _actionError.value = error.message ?: "Не удалось удалить место"
            }
        }
    }
    
    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            runCatching {
                expenseRepository.deleteExpense(expenseId)
            }.onFailure { error ->
                Log.w(TAG, "deleteExpense failed", error)
                _actionError.value = error.message ?: "Не удалось удалить расход"
            }
        }
    }

    fun toggleExpenseSettled(expenseId: String, isSettled: Boolean) {
        viewModelScope.launch {
            runCatching {
                expenseRepository.setExpenseSettled(expenseId, isSettled)
            }.onFailure { error ->
                Log.w(TAG, "toggleExpenseSettled failed", error)
                _actionError.value = error.message ?: "Не удалось изменить статус расхода"
            }
        }
    }
    
    fun deleteTrip() {
        viewModelScope.launch {
            runCatching {
                tripRepository.deleteTrip(tripId)
            }.onSuccess {
                _isDeleted.value = true
            }.onFailure { error ->
                Log.w(TAG, "deleteTrip failed", error)
                _actionError.value = error.message ?: "Не удалось удалить поездку"
            }
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
                val planningSuggestion = planningSuggestionState.value

                var totalDays = 0
                var changedDays = 0

                days.forEach { day ->
                    val dayPlaces = places.filter { it.tripDayId == day.id }
                    if (dayPlaces.size < 2) return@forEach
                    totalDays += 1

                    // Текущий порядок — как его видит пользователь в плане:
                    // сначала по scheduledTime (UI сортирует по нему), потом по orderIndex.
                    val currentOrder = dayPlaces.sortedWith(
                        compareBy<com.triloo.data.model.Place> {
                            parseScheduledMinutes(it.scheduledTime)
                        }.thenBy { it.orderIndex }
                    )

                    val optimizedOrder = if (
                        _selectedPlanningMode.value == RoutePlanningMode.AI_ASSISTED &&
                        planningSuggestion != null
                    ) {
                        val orderedIds = planningSuggestion.dayOrders[day.id].orEmpty()
                        if (orderedIds.isEmpty()) currentOrder
                        else orderedIds.mapNotNull { id -> dayPlaces.firstOrNull { it.id == id } }
                    } else {
                        routeOptimizer.optimizeRoute(dayPlaces, startLocation).optimizedPlaces
                    }

                    if (optimizedOrder.map { it.id } == currentOrder.map { it.id }) {
                        return@forEach
                    }

                    // Сохраняем существующие тайм-слоты дня и переселяем в них места
                    // в новом порядке. Иначе reorder только orderIndex'а никак не
                    // отображается в UI (план сортирует по scheduledTime).
                    val now = System.currentTimeMillis()
                    optimizedOrder.forEachIndexed { idx, place ->
                        val targetSlot = currentOrder[idx]
                        tripRepository.updatePlace(
                            place.copy(
                                scheduledTime = targetSlot.scheduledTime,
                                estimatedDuration = targetSlot.estimatedDuration,
                                orderIndex = idx,
                                updatedAt = now
                            )
                        )
                    }
                    changedDays += 1
                }

                // Reuse-им существующий канал _actionError для фидбэка — снэк-бар уже
                // подписан на него; иначе тап «Запустить» был молчаливым.
                _actionError.value = when {
                    totalDays == 0 -> "Добавьте 2+ места в день, чтобы оптимизировать"
                    changedDays == 0 -> "Маршрут уже идёт оптимально"
                    changedDays == 1 -> "Маршрут пересобран на 1 дне"
                    else -> "Маршрут пересобран · $changedDays дн."
                }
            }.onFailure { error ->
                Log.w(TAG, "optimizeRoute failed", error)
                _actionError.value = error.message ?: "Не удалось оптимизировать маршрут"
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun setPlanningMode(mode: RoutePlanningMode) {
        _selectedPlanningMode.value = mode
    }

    fun setTravelMode(mode: TravelMode) {
        _selectedTravelMode.value = mode
    }

    fun applySuggestedTravelMode() {
        planningSuggestionState.value?.suggestedTravelMode?.let { suggestion ->
            _selectedTravelMode.value = suggestion
        }
    }

    fun startLocationSharing() {
        locationSharingServiceController.startSharing(tripId)
    }

    fun stopLocationSharing() {
        locationSharingServiceController.stopSharing()
    }

    /**
     * Одноразово получает координаты пользователя через FusedLocationProviderClient
     * и пробрасывает их в callback. UI отвечает за permission-запрос — без
     * ACCESS_FINE/COARSE_LOCATION нечего и спрашивать у системы.
     */
    fun requestCurrentUserLocation(onResolved: (Double, Double) -> Unit) {
        viewModelScope.launch {
            val point = runCatching { locationSharingManager.currentLocation() }
                .getOrNull() ?: return@launch
            onResolved(point.latitude, point.longitude)
        }
    }

    fun setMapVisible(isVisible: Boolean) {
        if (!isVisible) {
            participantRefreshJob?.cancel()
            participantRefreshJob = null
            return
        }
        if (participantRefreshJob != null) return

        participantRefreshJob = viewModelScope.launch {
            val trip = tripRepository.getTripById(tripId)
            if (trip?.isGroupTrip != true) {
                participantRefreshJob = null
                return@launch
            }

            while (isActive) {
                runCatching { onlineSyncRepository.pullRemoteChanges() }
                    .onFailure { error ->
                        Log.w(TAG, "pullRemoteChanges failed", error)
                    }
                delay(20_000L)
            }
        }
    }

    /**
     * «18:30» / «6:30 PM» → минуты от полуночи. Пустые/невалидные строки
     * получают Int.MAX_VALUE, чтобы при сортировке пойти в самый конец —
     * совпадает с тем, как план показывает места без времени.
     */
    private fun parseScheduledMinutes(raw: String?): Int {
        if (raw.isNullOrBlank()) return Int.MAX_VALUE
        val trimmed = raw.trim().uppercase(Locale.US)
        val isPm = trimmed.endsWith("PM")
        val isAm = trimmed.endsWith("AM")
        val core = trimmed.removeSuffix("AM").removeSuffix("PM").trim()
        val parts = core.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        val hour24 = when {
            isPm && h < 12 -> h + 12
            isAm && h == 12 -> 0
            else -> h
        }
        return hour24 * 60 + m
    }

    private fun resolveOrderedPlaces(
        days: List<TripDay>,
        places: List<Place>,
        planningMode: RoutePlanningMode,
        planningSuggestion: RoutePlanSuggestion?
    ): List<Place> {
        // Zero-coord места ломают любые расстояния — отбрасываем их сразу,
        // чтобы и routeOptimizer, и flattenDayOrders видели только осмысленные точки.
        val withCoords = places.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        if (planningMode == RoutePlanningMode.AI_ASSISTED && planningSuggestion != null) {
            return routePlanningAssistant.flattenDayOrders(
                days = days,
                places = withCoords,
                dayOrders = planningSuggestion.dayOrders
            )
        }
        return orderPlacesForRoute(days, withCoords)
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

    private fun resolveRecommendationCenter(
        trip: Trip?,
        orderedPlaces: List<Place>
    ): LatLng? {
        if (orderedPlaces.isNotEmpty()) {
            val averageLatitude = orderedPlaces.map { it.latitude }.average()
            val averageLongitude = orderedPlaces.map { it.longitude }.average()
            return LatLng(averageLatitude, averageLongitude)
        }
        val hotelLatitude = trip?.hotelLatitude
        val hotelLongitude = trip?.hotelLongitude
        if (hotelLatitude != null && hotelLongitude != null) {
            return LatLng(hotelLatitude, hotelLongitude)
        }
        val destLat = trip?.destinationLatitude
        val destLon = trip?.destinationLongitude
        if (destLat != null && destLon != null) {
            return LatLng(destLat, destLon)
        }
        return null
    }

    private fun recommendationRadius(travelMode: TravelMode): Int {
        return when (travelMode) {
            TravelMode.WALKING -> 1_500
            TravelMode.BICYCLING -> 2_500
            TravelMode.TRANSIT -> 3_500
            TravelMode.DRIVING -> 5_000
        }
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

private data class PlanningInputs(
    val trip: Trip?,
    val days: List<TripDay>,
    val places: List<Place>,
    val planningMode: RoutePlanningMode
)

private data class RouteInputs(
    val days: List<TripDay>,
    val places: List<Place>,
    val travelMode: TravelMode,
    val planningMode: RoutePlanningMode,
    val planningSuggestion: RoutePlanSuggestion?
)

private data class RecommendationInputs(
    val trip: Trip?,
    val orderedPlaces: List<Place>,
    val travelMode: TravelMode
)

private data class MapUiState(
    val routeDetails: RouteDetails? = null,
    val recommendations: List<PlaceRecommendation> = emptyList(),
    val destinationMarker: DestinationMapMarker? = null,
    val selectedTravelMode: TravelMode = TravelMode.WALKING,
    val selectedPlanningMode: RoutePlanningMode = RoutePlanningMode.CLASSIC,
    val suggestedTravelMode: TravelMode? = null,
    val routePlanningSummary: String? = null,
    val routePlanningSource: RoutePlanSource? = null,
    val isLocationSharingActive: Boolean = false,
    val locationSharingStatus: String? = null,
    val locationSharingError: String? = null
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
    val recommendations: List<PlaceRecommendation> = emptyList(),
    val destinationMarker: DestinationMapMarker? = null,
    val selectedTravelMode: TravelMode = TravelMode.WALKING,
    val selectedPlanningMode: RoutePlanningMode = RoutePlanningMode.CLASSIC,
    val suggestedTravelMode: TravelMode? = null,
    val routePlanningSummary: String? = null,
    val routePlanningSource: RoutePlanSource? = null,
    val isLocationSharingActive: Boolean = false,
    val locationSharingStatus: String? = null,
    val locationSharingError: String? = null,
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)

private data class PartialTripDetailsUiState(
    val trip: Trip? = null,
    val days: List<TripDay> = emptyList(),
    val places: List<Place> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val totalExpenses: Double = 0.0,
    val balances: List<Balance> = emptyList(),
    val routeDetails: RouteDetails? = null,
    val recommendations: List<PlaceRecommendation> = emptyList(),
    val destinationMarker: DestinationMapMarker? = null,
    val selectedTravelMode: TravelMode = TravelMode.WALKING,
    val selectedPlanningMode: RoutePlanningMode = RoutePlanningMode.CLASSIC,
    val suggestedTravelMode: TravelMode? = null,
    val routePlanningSummary: String? = null,
    val routePlanningSource: RoutePlanSource? = null,
    val isLocationSharingActive: Boolean = false,
    val locationSharingStatus: String? = null,
    val locationSharingError: String? = null
)

private data class LocationSharingUiState(
    val isActive: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null
)

data class DestinationMapMarker(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
