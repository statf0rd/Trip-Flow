package com.triloo.ui.tripdetails

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TripDay
import com.triloo.data.places.PlaceDetails
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.places.PlacesService
import com.triloo.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddPlaceViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val placesService: PlacesService,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val placeId: String? = savedStateHandle.get<String>("placeId")
    private var tripId: String = savedStateHandle.get<String>("tripId") ?: ""
    private var dayId: String = savedStateHandle.get<String>("dayId") ?: ""
    private var editingPlace: Place? = null

    private val defaultTimeFormat = resolveDeviceTimeFormat(context)
    private val _uiState = MutableStateFlow(AddPlaceUiState(timeFormat = defaultTimeFormat))
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private val _day = MutableStateFlow<TripDay?>(null)
    val day: StateFlow<TripDay?> = _day.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    
    private val _suggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val suggestions: StateFlow<List<PlaceSuggestion>> = _suggestions.asStateFlow()

    init {
        viewModelScope.launch {
            // Если редактируем место, сначала подтягиваем его текущие данные.
            placeId?.let { id ->
                val place = tripRepository.getPlaceById(id)
                if (place != null) {
                    editingPlace = place
                    tripId = place.tripId
                    dayId = place.tripDayId
                    _uiState.update { state ->
                        state.copy(
                            name = place.name,
                            address = place.address.orEmpty(),
                            latitude = place.latitude,
                            longitude = place.longitude,
                            category = place.category,
                            time = place.scheduledTime.orEmpty(),
                            timeFormat = place.scheduledTime?.let { detectTimeFormat(it) }
                                ?: TimeFormat.HOURS_24,
                            durationValue = place.estimatedDuration?.toString().orEmpty(),
                            durationUnit = DurationUnit.MINUTES,
                            notes = place.notes.orEmpty(),
                            rating = place.rating,
                            selectedPlaceId = place.placeId,
                            openingHours = place.openingHours,
                            priceLevel = place.priceLevel,
                            photoUrl = place.photoUrl,
                            website = place.website,
                            phoneNumber = place.phoneNumber
                        )
                    }
                }
            }

            // Заранее загружаем информацию о дне для заголовка экрана.
            _day.value = tripRepository.getTripDayById(dayId)

            val existing = tripRepository.getPlacesByDay(dayId)
            val lockedFormat = existing.firstNotNullOfOrNull { place ->
                place.scheduledTime?.let { detectTimeFormat(it) }
            }
            if (placeId != null) {
                if (lockedFormat != null) {
                    _uiState.update { state ->
                        state.copy(
                            timeFormat = lockedFormat,
                            lockedTimeFormat = lockedFormat,
                            isEditing = true
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(isEditing = true)
                    }
                }
            }
        }
        
        // Настраиваем debounce для поискового запроса.
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length < 2) {
                        _suggestions.value = emptyList()
                        _uiState.update { it.copy(isSearching = false) }
                    } else {
                        searchPlaces(query)
                    }
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
        
        // Если пользователь очистил поле, убираем подсказки.
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
                selectedPlaceId = suggestion.placeId,
                openingHours = null,
                priceLevel = null,
                photoUrl = null,
                website = null,
                phoneNumber = null
            )
        }
        // После выбора очищаем подсказки.
        _suggestions.value = emptyList()
        _searchQuery.value = ""
        viewModelScope.launch {
            fetchAndApplyPlaceDetails(suggestion.placeId)
        }
    }
    
    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun updateCoordinates(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(latitude = latitude, longitude = longitude) }
    }

    fun updateTime(value: String) {
        val formatted = formatTimeInput(value, _uiState.value.timeFormat)
        _uiState.update { it.copy(time = formatted) }
    }

    fun updateDuration(value: String) {
        _uiState.update { it.copy(durationValue = value) }
    }

    fun toggleDurationUnit() {
        _uiState.update { state ->
            val currentValue = parseDurationValue(state.durationValue)
            val newUnit = if (state.durationUnit == DurationUnit.MINUTES) {
                DurationUnit.HOURS
            } else {
                DurationUnit.MINUTES
            }
            val convertedValue = currentValue?.let { convertDuration(it, state.durationUnit, newUnit) }
            state.copy(
                durationUnit = newUnit,
                durationValue = convertedValue ?: state.durationValue
            )
        }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateCategory(category: PlaceCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun savePlace() {
        val state = _uiState.value

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val resolvedState = resolvePlaceDetails(state)
                val durationMinutes = parseDurationValue(resolvedState.durationValue)?.let { value ->
                    if (resolvedState.durationUnit == DurationUnit.MINUTES) {
                        value
                    } else {
                        value * 60.0
                    }
                }
                val normalizedTime = normalizeTime(resolvedState.time, resolvedState.timeFormat)
                if (placeId == null) {
                    val place = Place(
                        tripId = tripId,
                        tripDayId = dayId,
                        name = resolvedState.name.trim(),
                        address = resolvedState.address.trim().ifBlank { null },
                        placeId = resolvedState.selectedPlaceId,
                        latitude = resolvedState.latitude,
                        longitude = resolvedState.longitude,
                        category = resolvedState.category,
                        iconEmoji = resolvedState.category.emoji,
                        scheduledTime = normalizedTime ?: resolvedState.time.trim().ifBlank { null },
                        estimatedDuration = durationMinutes?.roundToInt(),
                        openingHours = resolvedState.openingHours,
                        rating = resolvedState.rating,
                        priceLevel = resolvedState.priceLevel,
                        photoUrl = resolvedState.photoUrl,
                        website = resolvedState.website,
                        phoneNumber = resolvedState.phoneNumber,
                        notes = resolvedState.notes.trim().ifBlank { null }
                    )
                    tripRepository.addPlace(place)
                } else {
                    val base = editingPlace ?: tripRepository.getPlaceById(placeId)
                    val updated = (base ?: Place(
                        id = placeId,
                        tripId = tripId,
                        tripDayId = dayId,
                        name = resolvedState.name.trim(),
                        latitude = resolvedState.latitude,
                        longitude = resolvedState.longitude,
                        category = resolvedState.category
                    )).copy(
                        name = resolvedState.name.trim(),
                        address = resolvedState.address.trim().ifBlank { null },
                        placeId = resolvedState.selectedPlaceId,
                        latitude = resolvedState.latitude,
                        longitude = resolvedState.longitude,
                        category = resolvedState.category,
                        iconEmoji = resolvedState.category.emoji,
                        scheduledTime = normalizedTime ?: resolvedState.time.trim().ifBlank { null },
                        estimatedDuration = durationMinutes?.roundToInt(),
                        openingHours = resolvedState.openingHours,
                        rating = resolvedState.rating,
                        priceLevel = resolvedState.priceLevel,
                        photoUrl = resolvedState.photoUrl,
                        website = resolvedState.website,
                        phoneNumber = resolvedState.phoneNumber,
                        notes = resolvedState.notes.trim().ifBlank { null },
                        updatedAt = System.currentTimeMillis()
                    )
                    tripRepository.updatePlace(updated)
                }
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

    private suspend fun resolvePlaceDetails(state: AddPlaceUiState): AddPlaceUiState {
        if (state.selectedPlaceId.isNullOrBlank()) return state
        val hasDetails = state.openingHours != null ||
            state.priceLevel != null ||
            state.photoUrl != null ||
            state.website != null ||
            state.phoneNumber != null
        if (hasDetails) return state

        val details = placesService.getPlaceDetails(state.selectedPlaceId)
            ?: return state

        val resolved = state.applyDetails(details)
        _uiState.update { current ->
            if (current.selectedPlaceId == details.placeId) resolved else current
        }
        return resolved
    }

    private suspend fun fetchAndApplyPlaceDetails(placeId: String) {
        _uiState.update { it.copy(isLoadingDetails = true) }
        val details = runCatching {
            placesService.getPlaceDetails(placeId)
        }.getOrNull()

        _uiState.update { state ->
            if (state.selectedPlaceId != placeId) {
                state
            } else {
                state.applyDetails(details).copy(isLoadingDetails = false)
            }
        }
    }

    private fun AddPlaceUiState.applyDetails(details: PlaceDetails?): AddPlaceUiState {
        if (details == null) return copy(isLoadingDetails = false)
        return copy(
            name = if (name.isBlank()) details.name else name,
            address = if (address.isBlank()) details.address else address,
            latitude = details.latitude,
            longitude = details.longitude,
            category = details.category,
            rating = details.rating ?: rating,
            openingHours = details.openingHours,
            priceLevel = details.priceLevel,
            photoUrl = details.photoUrl,
            website = details.website,
            phoneNumber = details.phoneNumber,
            isLoadingDetails = false
        )
    }

    private fun parseDurationValue(value: String): Double? {
        return value.trim().replace(',', '.').toDoubleOrNull()
    }

    private fun normalizeTime(value: String, format: TimeFormat): String? {
        if (value.isBlank()) return null
        val upper = value.trim().uppercase(Locale.US)
        val parsed = parseTime(upper, TimeFormat.HOURS_24) ?: parseTime(upper, TimeFormat.HOURS_12)
        return parsed?.let { formatTime(it, format) }
    }

    private fun parseTime(value: String, format: TimeFormat): LocalTime? {
        val trimmed = value.trim().uppercase(Locale.US)
        val patterns = if (format == TimeFormat.HOURS_24) {
            listOf("H:mm", "HH:mm")
        } else {
            listOf("h:mm a", "hh:mm a", "h:mma", "hh:mma")
        }
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.US))
            }.getOrNull()
        }
    }

    private fun formatTime(time: LocalTime, format: TimeFormat): String {
        val pattern = if (format == TimeFormat.HOURS_24) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    private fun formatTimeInput(raw: String, format: TimeFormat): String {
        if (raw.isBlank()) return ""
        val trimmed = raw.uppercase(Locale.US)
        val withoutSpace = trimmed.replace(" ", "")
        val digitsPrefix = withoutSpace.takeWhile { it.isDigit() }
        var candidate = trimmed

        if (!trimmed.contains(":") && digitsPrefix.length >= 2) {
            val rest = withoutSpace.drop(2)
            candidate = digitsPrefix.take(2) + ":" + rest
        } else if (trimmed.length == 2 && trimmed.all { it.isDigit() }) {
            candidate = "$trimmed:"
        }

        // Добавляем пробел перед AM/PM, чтобы формат оставался читаемым.
        val hasAmPm = candidate.contains("AM") || candidate.contains("PM")
        if (hasAmPm) {
            candidate = candidate.replace("AM", " AM").replace("PM", " PM").replace("  ", " ")
        }

        return candidate.trim()
    }

    private fun convertDuration(
        value: Double,
        fromUnit: DurationUnit,
        toUnit: DurationUnit
    ): String {
        val minutes = if (fromUnit == DurationUnit.MINUTES) value else value * 60.0
        return if (toUnit == DurationUnit.MINUTES) {
            minutes.roundToInt().toString()
        } else {
            val formatted = String.format(java.util.Locale.US, "%.1f", minutes / 60.0)
            formatted.trimEnd('0').trimEnd('.')
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
    val timeFormat: TimeFormat = TimeFormat.HOURS_24,
    val lockedTimeFormat: TimeFormat? = null,
    val durationValue: String = "",
    val durationUnit: DurationUnit = DurationUnit.MINUTES,
    val notes: String = "",
    val rating: Float? = null,
    val selectedPlaceId: String? = null,
    val openingHours: String? = null,
    val priceLevel: Int? = null,
    val photoUrl: String? = null,
    val website: String? = null,
    val phoneNumber: String? = null,
    val isSearching: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false
) {
    val isValid: Boolean
        get() = name.isNotBlank() && !isSaving
    
    val hasCoordinates: Boolean
        get() = latitude != 0.0 || longitude != 0.0
}

enum class DurationUnit(val label: String) {
    MINUTES("мин"),
    HOURS("час")
}

enum class TimeFormat(val label: String) {
    HOURS_24("24"),
    HOURS_12("12")
}
