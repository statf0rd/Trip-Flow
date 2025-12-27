package com.triloo

import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TravelMode
import com.triloo.data.remote.DirectionsApi
import com.triloo.data.remote.DirectionsResponse
import com.triloo.data.route.NearestNeighborRouteOptimizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearestNeighborRouteOptimizerTest {

    @Test
    fun calculateRouteReturnsLegsForTwoPlaces() = runBlocking {
        val optimizer = NearestNeighborRouteOptimizer(StubDirectionsApi())
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

    private class StubDirectionsApi : DirectionsApi {
        override suspend fun getDirections(
            origin: String,
            destination: String,
            waypoints: String?,
            mode: String,
            apiKey: String
        ): DirectionsResponse {
            return DirectionsResponse(status = "ZERO_RESULTS")
        }
    }
}
