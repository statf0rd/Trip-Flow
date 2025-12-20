package com.triloo.data.route

import com.triloo.data.model.Place
import com.triloo.data.model.TravelMode
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Route Optimizer Interface
 * 
 * Provides route optimization and place recommendations.
 * Current implementation uses simple heuristics (Nearest Neighbor algorithm).
 * 
 * TODO: Integrate with AI/ML service for smarter recommendations
 * TODO: Use Google Directions API for real travel time estimation
 */
interface RouteOptimizer {
    
    /**
     * Optimize the order of places to minimize travel distance/time
     * 
     * @param places List of places to visit
     * @param startLocation Starting point (hotel or current location)
     * @return Optimized order of places
     */
    suspend fun optimizeRoute(
        places: List<Place>,
        startLocation: LatLng? = null
    ): RouteOptimizationResult
    
    /**
     * Get nearby place recommendations
     * 
     * @param currentPlaces Existing places in the itinerary
     * @param center Center point for search
     * @param radius Search radius in meters
     * @param preferences User preferences for filtering
     * @return List of recommended places
     */
    suspend fun getRecommendations(
        currentPlaces: List<Place>,
        center: LatLng,
        radius: Int = 2000,
        preferences: TravelPreferences? = null
    ): List<PlaceRecommendation>
    
    /**
     * Calculate route details between places
     * 
     * @param places Ordered list of places
     * @param travelMode Preferred travel mode
     * @return Route details including distances and times
     */
    suspend fun calculateRoute(
        places: List<Place>,
        travelMode: TravelMode = TravelMode.WALKING
    ): RouteDetails
}

/**
 * Simple coordinate class
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Result of route optimization
 */
data class RouteOptimizationResult(
    val optimizedPlaces: List<Place>,
    val totalDistanceMeters: Int,
    val estimatedTimeMinutes: Int,
    val savingsPercent: Float, // How much better than original order
    val routeLegs: List<RouteLeg>
)

/**
 * A leg of the route between two places
 */
data class RouteLeg(
    val from: Place,
    val to: Place,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val travelMode: TravelMode
)

/**
 * Full route details
 */
data class RouteDetails(
    val places: List<Place>,
    val legs: List<RouteLeg>,
    val totalDistanceMeters: Int,
    val totalDurationMinutes: Int,
    val polylineEncoded: String? = null // For map rendering
)

/**
 * Place recommendation from optimizer
 */
data class PlaceRecommendation(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val rating: Float?,
    val distanceFromRoute: Int, // meters
    val reason: String // "По пути", "Рядом с ...", etc.
)

/**
 * User travel preferences for recommendations
 */
data class TravelPreferences(
    val budgetLevel: BudgetLevel = BudgetLevel.MEDIUM,
    val interests: List<String> = emptyList(), // "food", "museums", "nature", etc.
    val travelStyle: TravelStyle = TravelStyle.BALANCED,
    val avoidCrowds: Boolean = false,
    val familyFriendly: Boolean = false
)

enum class BudgetLevel { LOW, MEDIUM, HIGH, LUXURY }
enum class TravelStyle { RELAXED, BALANCED, ACTIVE }

/**
 * Default implementation using Nearest Neighbor heuristic
 * 
 * This is a simple greedy algorithm that always picks the nearest unvisited place.
 * Not optimal, but fast and gives reasonable results for typical trip sizes.
 * 
 * TODO: Implement more sophisticated algorithms:
 * - 2-opt improvement
 * - Simulated annealing
 * - Genetic algorithm
 * - Call external AI service for complex optimization
 */
@Singleton
class NearestNeighborRouteOptimizer @Inject constructor() : RouteOptimizer {
    
    override suspend fun optimizeRoute(
        places: List<Place>,
        startLocation: LatLng?
    ): RouteOptimizationResult {
        if (places.isEmpty()) {
            return RouteOptimizationResult(
                optimizedPlaces = emptyList(),
                totalDistanceMeters = 0,
                estimatedTimeMinutes = 0,
                savingsPercent = 0f,
                routeLegs = emptyList()
            )
        }
        
        if (places.size == 1) {
            return RouteOptimizationResult(
                optimizedPlaces = places,
                totalDistanceMeters = 0,
                estimatedTimeMinutes = 0,
                savingsPercent = 0f,
                routeLegs = emptyList()
            )
        }
        
        // Simulate processing time
        delay(500)
        
        // Calculate original route distance
        val originalDistance = calculateTotalDistance(places)
        
        // Apply Nearest Neighbor algorithm
        val optimized = nearestNeighbor(places, startLocation)
        val optimizedDistance = calculateTotalDistance(optimized)
        
        // Build route legs
        val legs = optimized.zipWithNext().map { (from, to) ->
            val distance = haversineDistance(
                from.latitude, from.longitude,
                to.latitude, to.longitude
            ).toInt()
            
            RouteLeg(
                from = from,
                to = to,
                distanceMeters = distance,
                durationMinutes = estimateWalkingTime(distance),
                travelMode = TravelMode.WALKING
            )
        }
        
        val savings = if (originalDistance > 0) {
            ((originalDistance - optimizedDistance) / originalDistance.toFloat()) * 100
        } else 0f
        
        return RouteOptimizationResult(
            optimizedPlaces = optimized,
            totalDistanceMeters = optimizedDistance,
            estimatedTimeMinutes = legs.sumOf { it.durationMinutes },
            savingsPercent = maxOf(0f, savings),
            routeLegs = legs
        )
    }
    
    override suspend fun getRecommendations(
        currentPlaces: List<Place>,
        center: LatLng,
        radius: Int,
        preferences: TravelPreferences?
    ): List<PlaceRecommendation> {
        // Simulate processing time
        delay(300)
        
        // TODO: Call Google Places API or AI service for real recommendations
        // For now, return mock recommendations
        
        return listOf(
            PlaceRecommendation(
                placeId = "rec_1",
                name = "Кофейня у парка",
                address = "ул. Примерная, 10",
                latitude = center.latitude + 0.002,
                longitude = center.longitude + 0.001,
                category = "cafe",
                rating = 4.5f,
                distanceFromRoute = 150,
                reason = "Отличное место для перерыва"
            ),
            PlaceRecommendation(
                placeId = "rec_2",
                name = "Смотровая площадка",
                address = "Набережная, 5",
                latitude = center.latitude - 0.001,
                longitude = center.longitude + 0.003,
                category = "viewpoint",
                rating = 4.8f,
                distanceFromRoute = 300,
                reason = "По пути к следующей точке"
            ),
            PlaceRecommendation(
                placeId = "rec_3",
                name = "Исторический музей",
                address = "пр. Культуры, 15",
                latitude = center.latitude + 0.003,
                longitude = center.longitude - 0.002,
                category = "museum",
                rating = 4.6f,
                distanceFromRoute = 500,
                reason = "Рекомендуется посетителями"
            )
        )
    }
    
    override suspend fun calculateRoute(
        places: List<Place>,
        travelMode: TravelMode
    ): RouteDetails {
        if (places.isEmpty()) {
            return RouteDetails(
                places = emptyList(),
                legs = emptyList(),
                totalDistanceMeters = 0,
                totalDurationMinutes = 0
            )
        }
        
        val legs = places.zipWithNext().map { (from, to) ->
            val distance = haversineDistance(
                from.latitude, from.longitude,
                to.latitude, to.longitude
            ).toInt()
            
            RouteLeg(
                from = from,
                to = to,
                distanceMeters = distance,
                durationMinutes = estimateTravelTime(distance, travelMode),
                travelMode = travelMode
            )
        }
        
        return RouteDetails(
            places = places,
            legs = legs,
            totalDistanceMeters = legs.sumOf { it.distanceMeters },
            totalDurationMinutes = legs.sumOf { it.durationMinutes }
        )
    }
    
    // Private helper methods
    
    /**
     * Nearest Neighbor algorithm implementation
     */
    private fun nearestNeighbor(
        places: List<Place>,
        startLocation: LatLng?
    ): List<Place> {
        val remaining = places.toMutableList()
        val result = mutableListOf<Place>()
        
        // Start from the nearest place to start location, or first place
        var currentLat = startLocation?.latitude ?: places.first().latitude
        var currentLon = startLocation?.longitude ?: places.first().longitude
        
        while (remaining.isNotEmpty()) {
            // Find nearest place
            val nearest = remaining.minByOrNull { place ->
                haversineDistance(currentLat, currentLon, place.latitude, place.longitude)
            }!!
            
            result.add(nearest)
            remaining.remove(nearest)
            
            currentLat = nearest.latitude
            currentLon = nearest.longitude
        }
        
        return result
    }
    
    /**
     * Calculate total distance of a route
     */
    private fun calculateTotalDistance(places: List<Place>): Int {
        return places.zipWithNext().sumOf { (from, to) ->
            haversineDistance(
                from.latitude, from.longitude,
                to.latitude, to.longitude
            ).toInt()
        }
    }
    
    /**
     * Haversine formula to calculate distance between two coordinates
     * Returns distance in meters
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Estimate walking time in minutes
     * Assumes average walking speed of 5 km/h
     */
    private fun estimateWalkingTime(distanceMeters: Int): Int {
        val walkingSpeedMps = 5000.0 / 3600.0 // 5 km/h in m/s
        return ceil(distanceMeters / walkingSpeedMps / 60).toInt()
    }
    
    /**
     * Estimate travel time based on mode
     */
    private fun estimateTravelTime(distanceMeters: Int, mode: TravelMode): Int {
        val speedMps = when (mode) {
            TravelMode.WALKING -> 5000.0 / 3600.0   // 5 km/h
            TravelMode.BICYCLING -> 15000.0 / 3600.0 // 15 km/h
            TravelMode.DRIVING -> 30000.0 / 3600.0   // 30 km/h (urban)
            TravelMode.TRANSIT -> 20000.0 / 3600.0   // 20 km/h average
        }
        return ceil(distanceMeters / speedMps / 60).toInt()
    }
}



