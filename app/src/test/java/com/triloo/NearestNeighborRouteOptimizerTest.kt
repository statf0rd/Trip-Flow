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
import com.triloo.data.route.LatLng
import com.triloo.data.route.MapRouteProvider
import com.triloo.data.route.NearestNeighborRouteOptimizer
import com.triloo.data.route.TravelPreferences
import com.triloo.data.route.YandexRouteResult
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
            nearbyPlacesProvider = StubNearbyPlacesProvider(),
            mapRouteProvider = NoopMapRouteProvider()
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
            nearbyPlacesProvider = StubNearbyPlacesProvider(),
            mapRouteProvider = NoopMapRouteProvider()
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

    @Test
    fun optimizeRouteReordersMultiplePlaces() = runBlocking {
        val result = optimizer().optimizeRoute(
            listOf(
                place("a", 55.7500, 37.6100),
                place("b", 55.7600, 37.6100),
                place("c", 55.7550, 37.6100)
            ),
            startLocation = null
        )

        assertEquals(3, result.optimizedPlaces.size)
        assertEquals(2, result.routeLegs.size)
        assertTrue(result.totalDistanceMeters > 0)
    }

    @Test
    fun optimizeRouteHandlesEmptyAndSingle() = runBlocking {
        assertTrue(optimizer().optimizeRoute(emptyList(), null).optimizedPlaces.isEmpty())

        val single = optimizer().optimizeRoute(listOf(place("a", 55.75, 37.61)), null)
        assertEquals(1, single.optimizedPlaces.size)
        assertTrue(single.routeLegs.isEmpty())
    }

    @Test
    fun calculateRouteEmptyPlacesReturnsEmpty() = runBlocking {
        val route = optimizer().calculateRoute(emptyList(), TravelMode.WALKING)
        assertTrue(route.legs.isEmpty())
        assertEquals(0, route.totalDistanceMeters)
    }

    @Test
    fun calculateRouteEstimatesDurationForEachTravelMode() = runBlocking {
        // ORS отдаёт пустой ответ -> детерминированно идём в haversine-оценку
        // (это покрывает estimateTravelTime для всех режимов независимо от ключа).
        val opt = NearestNeighborRouteOptimizer(
            openRouteServiceApi = EmptyOpenRouteServiceApi(),
            nearbyPlacesProvider = StubNearbyPlacesProvider(),
            mapRouteProvider = NoopMapRouteProvider()
        )
        val places = listOf(
            place("a", 55.7512, 37.6184),
            place("b", 55.7602, 37.6187)
        )

        for (mode in TravelMode.entries) {
            val route = opt.calculateRoute(places, mode)
            assertEquals(1, route.legs.size)
            assertEquals(mode, route.legs.first().travelMode)
            assertTrue(route.totalDurationMinutes > 0)
        }
    }

    @Test
    fun getRecommendationsAppliesPreferenceInterests() = runBlocking {
        val recommendations = optimizer().getRecommendations(
            currentPlaces = listOf(place("a", 55.7539, 37.6208)),
            center = LatLng(55.7540, 37.6209),
            radius = 2_000,
            preferences = TravelPreferences(
                interests = listOf("food", "museums", "nature", "nightlife", "shopping")
            )
        )

        assertFalse(recommendations.isEmpty())
    }

    private fun optimizer() = NearestNeighborRouteOptimizer(
        openRouteServiceApi = StubOpenRouteServiceApi(),
        nearbyPlacesProvider = StubNearbyPlacesProvider(),
        mapRouteProvider = NoopMapRouteProvider()
    )

    private fun place(
        id: String,
        lat: Double,
        lon: Double,
        category: PlaceCategory = PlaceCategory.ATTRACTION
    ) = Place(
        id = id,
        tripId = "trip",
        tripDayId = "day",
        name = id,
        latitude = lat,
        longitude = lon,
        category = category
    )

    private class EmptyOpenRouteServiceApi : OpenRouteServiceApi {
        override suspend fun getDirections(
            profile: String,
            apiKey: String,
            request: OpenRouteServiceDirectionsRequest
        ): OpenRouteServiceDirectionsResponse = OpenRouteServiceDirectionsResponse(routes = emptyList())
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

    private class NoopMapRouteProvider : MapRouteProvider {
        override suspend fun route(
            places: List<Place>,
            mode: TravelMode
        ): YandexRouteResult? = null
    }
}
