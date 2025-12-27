package com.triloo.data.places

import com.triloo.BuildConfig
import com.triloo.data.model.PlaceCategory
import com.triloo.data.remote.PlaceDetailsResult
import com.triloo.data.remote.PlacesApi
import com.triloo.data.remote.PlacesResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for searching places (Google Places API abstraction)
 */
@Singleton
class PlacesService @Inject constructor(
    private val placesApi: PlacesApi
) {
    
    /**
     * Search for places by query text
     * Returns list of place suggestions
     */
    suspend fun searchPlaces(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
        radius: Int = 50000 // meters
    ): List<PlaceSuggestion> {
        if (query.isBlank() || query.length < 2) {
            return emptyList()
        }

        if (!hasValidApiKey()) {
            return getMockSuggestions(query)
        }

        // Small debounce to keep UI smooth on fast typing.
        delay(150)

        val location = if (latitude != null && longitude != null) {
            "${latitude},${longitude}"
        } else {
            null
        }
        val radiusValue = if (location == null) null else radius

        return runCatching {
            placesApi.textSearch(
                query = query,
                location = location,
                radius = radiusValue,
                apiKey = BuildConfig.MAPS_API_KEY
            )
        }.getOrNull()?.takeIf { it.status == "OK" }?.results?.mapNotNull { result ->
            result.toSuggestion()
        }.orEmpty()
    }
    
    /**
     * Get place details by place ID
     */
    suspend fun getPlaceDetails(placeId: String): PlaceDetails? {
        if (!hasValidApiKey()) {
            return mockPlaceDetails[placeId]
        }

        val fields = listOf(
            "place_id",
            "name",
            "formatted_address",
            "geometry",
            "types",
            "rating",
            "price_level",
            "formatted_phone_number",
            "website",
            "opening_hours",
            "photos"
        ).joinToString(",")

        return runCatching {
            placesApi.details(
                placeId = placeId,
                fields = fields,
                apiKey = BuildConfig.MAPS_API_KEY
            )
        }.getOrNull()?.takeIf { it.status == "OK" }?.result?.toPlaceDetails()
    }
    
    /**
     * Get nearby places
     */
    suspend fun getNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radius: Int = 1000,
        type: String? = null
    ): List<PlaceSuggestion> {
        if (!hasValidApiKey()) {
            return emptyList()
        }

        return runCatching {
            placesApi.nearbySearch(
                location = "${latitude},${longitude}",
                radius = radius,
                type = type,
                apiKey = BuildConfig.MAPS_API_KEY
            )
        }.getOrNull()?.takeIf { it.status == "OK" }?.results?.mapNotNull { result ->
            result.toSuggestion()
        }.orEmpty()
    }
    
    // Mock data for development
    
    private fun getMockSuggestions(query: String): List<PlaceSuggestion> {
        val lowerQuery = query.lowercase()
        return mockSuggestions.filter { suggestion ->
            suggestion.name.lowercase().contains(lowerQuery) ||
            suggestion.address.lowercase().contains(lowerQuery)
        }.take(5)
    }
    
    private val mockSuggestions = listOf(
        PlaceSuggestion(
            placeId = "mock_1",
            name = "Эрмитаж",
            address = "Дворцовая площадь, 2, Санкт-Петербург",
            category = PlaceCategory.MUSEUM,
            latitude = 59.9398,
            longitude = 30.3146,
            rating = 4.8f
        ),
        PlaceSuggestion(
            placeId = "mock_2",
            name = "Красная площадь",
            address = "Красная площадь, Москва",
            category = PlaceCategory.ATTRACTION,
            latitude = 55.7539,
            longitude = 37.6208,
            rating = 4.9f
        ),
        PlaceSuggestion(
            placeId = "mock_3",
            name = "Кафе Пушкинъ",
            address = "Тверской бульвар, 26А, Москва",
            category = PlaceCategory.RESTAURANT,
            latitude = 55.7644,
            longitude = 37.6048,
            rating = 4.5f
        ),
        PlaceSuggestion(
            placeId = "mock_4",
            name = "Парк Горького",
            address = "ул. Крымский Вал, 9, Москва",
            category = PlaceCategory.PARK,
            latitude = 55.7312,
            longitude = 37.6031,
            rating = 4.7f
        ),
        PlaceSuggestion(
            placeId = "mock_5",
            name = "ГУМ",
            address = "Красная площадь, 3, Москва",
            category = PlaceCategory.SHOPPING,
            latitude = 55.7546,
            longitude = 37.6215,
            rating = 4.6f
        ),
        PlaceSuggestion(
            placeId = "mock_6",
            name = "Третьяковская галерея",
            address = "Лаврушинский переулок, 10, Москва",
            category = PlaceCategory.MUSEUM,
            latitude = 55.7415,
            longitude = 37.6208,
            rating = 4.8f
        ),
        PlaceSuggestion(
            placeId = "mock_7",
            name = "Большой театр",
            address = "Театральная площадь, 1, Москва",
            category = PlaceCategory.ENTERTAINMENT,
            latitude = 55.7601,
            longitude = 37.6186,
            rating = 4.9f
        ),
        PlaceSuggestion(
            placeId = "mock_8",
            name = "Смотровая площадка PANORAMA360",
            address = "Пресненская набережная, 12, Москва",
            category = PlaceCategory.VIEWPOINT,
            latitude = 55.7496,
            longitude = 37.5377,
            rating = 4.5f
        ),
        PlaceSuggestion(
            placeId = "mock_9",
            name = "Музей космонавтики",
            address = "проспект Мира, 111, Москва",
            category = PlaceCategory.MUSEUM,
            latitude = 55.8227,
            longitude = 37.6395,
            rating = 4.7f
        ),
        PlaceSuggestion(
            placeId = "mock_10",
            name = "Сочи Парк",
            address = "Олимпийский проспект, 21, Сочи",
            category = PlaceCategory.ENTERTAINMENT,
            latitude = 43.4019,
            longitude = 39.9647,
            rating = 4.6f
        )
    )
    
    private val mockPlaceDetails = mockSuggestions.associate { suggestion ->
        suggestion.placeId to PlaceDetails(
            placeId = suggestion.placeId,
            name = suggestion.name,
            address = suggestion.address,
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            category = suggestion.category,
            rating = suggestion.rating,
            openingHours = "09:00 - 18:00",
            phoneNumber = "+7 (495) 123-45-67",
            website = "https://example.com"
        )
    }

    private fun hasValidApiKey(): Boolean {
        val apiKey = BuildConfig.MAPS_API_KEY
        return apiKey.isNotBlank() && !apiKey.contains("YOUR_GOOGLE_MAPS_API_KEY")
    }

    private fun PlacesResult.toSuggestion(): PlaceSuggestion? {
        val location = geometry?.location ?: return null
        return PlaceSuggestion(
            placeId = placeId,
            name = name,
            address = formattedAddress ?: vicinity.orEmpty(),
            category = PlaceCategory.fromGoogleType(types.orEmpty()),
            latitude = location.lat,
            longitude = location.lng,
            rating = rating
        )
    }

    private fun PlaceDetailsResult.toPlaceDetails(): PlaceDetails? {
        val location = geometry?.location ?: return null
        return PlaceDetails(
            placeId = placeId,
            name = name,
            address = formattedAddress.orEmpty(),
            latitude = location.lat,
            longitude = location.lng,
            category = PlaceCategory.fromGoogleType(types.orEmpty()),
            rating = rating,
            priceLevel = priceLevel,
            openingHours = openingHours?.weekdayText?.joinToString("\n"),
            phoneNumber = phoneNumber,
            website = website,
            photoUrl = buildPhotoUrl(photos?.firstOrNull()?.photoReference)
        )
    }

    private fun buildPhotoUrl(photoReference: String?): String? {
        if (photoReference.isNullOrBlank()) return null
        return "https://maps.googleapis.com/maps/api/place/photo" +
            "?maxwidth=1200&photo_reference=$photoReference&key=${BuildConfig.MAPS_API_KEY}"
    }
}

/**
 * Place suggestion from search results
 */
data class PlaceSuggestion(
    val placeId: String,
    val name: String,
    val address: String,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val latitude: Double,
    val longitude: Double,
    val rating: Float? = null
)

/**
 * Detailed place information
 */
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
    val rating: Float? = null,
    val priceLevel: Int? = null,
    val openingHours: String? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    val photoUrl: String? = null
)
