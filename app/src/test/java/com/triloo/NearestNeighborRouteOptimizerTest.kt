package com.triloo

import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TravelMode
import com.triloo.data.places.NearbyPlacesProvider
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.remote.OpenRouteServiceApi
import com.triloo.data.remote.OpenRouteServiceDirectionsRequest
import com.triloo.data.remote.OpenRouteServiceDirectionsResponse
import com.triloo.data.remote.OpenRouteServiceRoute
import com.triloo.data.remote.OpenRouteServiceSegment
import com.triloo.data.remote.OpenRouteServiceSummary
import com.triloo.data.route.NearestNeighborRouteOptimizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет, что оптимизатор маршрута строит хотя бы один leg и считает базовые метрики.
 */
class NearestNeighborRouteOptimizerTest {

    @Test
    fun calculateRouteReturnsLegsForTwoPlaces() = runBlocking {
        val optimizer = NearestNeighborRouteOptimizer(
            openRouteServiceApi = StubOpenRouteServiceApi(),
            nearbyPlacesProvider = StubNearbyPlacesProvider()
        )
        val places = listOf(
            Place(
                tripId = "trip",
                tripDayId = "day",
                name = "A",
                latitude = 55.751244,
                longitude = 37.618423,
                category = PlaceCategory.ATTRACTION
            ),
            Place(
                tripId = "trip",
                tripDayId = "day",
                name = "B",
                latitude = 55.760186,
                longitude = 37.618711,
                category = PlaceCategory.ATTRACTION
            )
        )

        val route = optimizer.calculateRoute(places, TravelMode.WALKING)

        assertEquals(1, route.legs.size)
        assertTrue(route.totalDistanceMeters > 0)
        assertTrue(route.totalDurationMinutes > 0)
    }

    @Test
    fun getRecommendationsReturnsNearbyPlaces() = runBlocking {
        val optimizer = NearestNeighborRouteOptimizer(
            openRouteServiceApi = StubOpenRouteServiceApi(),
            nearbyPlacesProvider = StubNearbyPlacesProvider()
        )
        val places = listOf(
            Place(
                tripId = "trip",
                tripDayId = "day",
                name = "Красная площадь",
                latitude = 55.7539,
                longitude = 37.6208,
                category = PlaceCategory.ATTRACTION
            )
        )

        val recommendations = optimizer.getRecommendations(
            currentPlaces = places,
            center = com.triloo.data.route.LatLng(55.7540, 37.6209),
            radius = 2_000
        )

        assertFalse(recommendations.isEmpty())
        assertTrue(recommendations.all { it.distanceFromRoute <= 2_000 })
    }

    private class StubOpenRouteServiceApi : OpenRouteServiceApi {
        override suspend fun getDirections(
            profile: String,
            apiKey: String,
            request: OpenRouteServiceDirectionsRequest
        ): OpenRouteServiceDirectionsResponse {
            return OpenRouteServiceDirectionsResponse(
                routes = listOf(
                    OpenRouteServiceRoute(
                        summary = OpenRouteServiceSummary(
                            distance = 1_200.0,
                            duration = 900.0
                        ),
                        segments = listOf(
                            OpenRouteServiceSegment(
                                distance = 1_200.0,
                                duration = 900.0
                            )
                        ),
                        geometry = "encoded_polyline"
                    )
                )
            )
        }
    }

    private class StubNearbyPlacesProvider : NearbyPlacesProvider {
        override suspend fun getNearbyPlaces(
            latitude: Double,
            longitude: Double,
            radius: Int,
            type: String?
        ): List<PlaceSuggestion> {
            return listOf(
                PlaceSuggestion(
                    placeId = "cafe-1",
                    name = "Кофейня рядом",
                    address = "Тверская, 1",
                    category = PlaceCategory.CAFE,
                    latitude = latitude + 0.001,
                    longitude = longitude + 0.001,
                    rating = 4.6f
                )
            )
        }
    }
}
