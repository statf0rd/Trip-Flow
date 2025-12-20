package com.triloo.data.places

import com.triloo.data.model.PlaceCategory
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for searching places (Google Places API abstraction)
 * 
 * TODO: Implement real Google Places API integration
 * For now, uses mock data for demonstration
 */
@Singleton
class PlacesService @Inject constructor() {
    
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
        
        // Simulate network delay
        delay(300)
        
        // TODO: Replace with real Google Places API call
        // val request = FindAutocompletePredictionsRequest.builder()
        //     .setQuery(query)
        //     .setLocationBias(...)
        //     .build()
        
        return getMockSuggestions(query)
    }
    
    /**
     * Get place details by place ID
     */
    suspend fun getPlaceDetails(placeId: String): PlaceDetails? {
        // Simulate network delay
        delay(200)
        
        // TODO: Replace with real Google Places API call
        // val request = FetchPlaceRequest.newInstance(placeId, placeFields)
        
        return mockPlaceDetails[placeId]
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
        delay(300)
        
        // TODO: Replace with real Google Places API call
        return emptyList()
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



