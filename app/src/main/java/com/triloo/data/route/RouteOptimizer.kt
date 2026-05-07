package com.triloo.data.route

import com.triloo.BuildConfig
import com.triloo.data.model.Place
import com.triloo.data.model.TravelMode
import com.triloo.data.places.NearbyPlacesProvider
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.remote.OpenRouteServiceApi
import com.triloo.data.remote.OpenRouteServiceDirectionsRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Интерфейс оптимизатора маршрута.
 *
 * Отвечает за перестановку точек, расчёт маршрута и рекомендации мест.
 * Текущая реализация использует простые эвристики и openrouteservice,
 * если ключ настроен; иначе остаётся на локальных расчётах.
 *
 * TODO: Подключить более умные AI/ML-подходы для рекомендаций и оптимизации.
 */
interface RouteOptimizer {
    
    /**
     * Оптимизирует порядок мест, чтобы сократить путь и время в дороге.
     *
     * @param places список мест для посещения.
     * @param startLocation стартовая точка: отель или текущее местоположение.
     * @return результат с новым порядком мест и расчётными метриками.
     */
    suspend fun optimizeRoute(
        places: List<Place>,
        startLocation: LatLng? = null
    ): RouteOptimizationResult
    
    /**
     * Подбирает рекомендованные места рядом с текущим маршрутом.
     *
     * @param currentPlaces уже выбранные точки маршрута.
     * @param center центральная точка поиска.
     * @param radius радиус поиска в метрах.
     * @param preferences пользовательские предпочтения для фильтрации.
     * @return список рекомендованных мест.
     */
    suspend fun getRecommendations(
        currentPlaces: List<Place>,
        center: LatLng,
        radius: Int = 2000,
        preferences: TravelPreferences? = null
    ): List<PlaceRecommendation>
    
    /**
     * Считает подробности маршрута между уже упорядоченными точками.
     *
     * @param places упорядоченный список мест.
     * @param travelMode предпочтительный способ передвижения.
     * @return детали маршрута с расстояниями и длительностями.
     */
    suspend fun calculateRoute(
        places: List<Place>,
        travelMode: TravelMode = TravelMode.WALKING
    ): RouteDetails
}

/**
 * Простая координата на карте.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Результат оптимизации маршрута.
 */
data class RouteOptimizationResult(
    val optimizedPlaces: List<Place>,
    val totalDistanceMeters: Int,
    val estimatedTimeMinutes: Int,
    val savingsPercent: Float, // Насколько маршрут лучше исходного порядка.
    val routeLegs: List<RouteLeg>
)

/**
 * Один участок маршрута между двумя местами.
 */
data class RouteLeg(
    val from: Place,
    val to: Place,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val travelMode: TravelMode
)

/**
 * Полные детали построенного маршрута.
 */
data class RouteDetails(
    val places: List<Place>,
    val legs: List<RouteLeg>,
    val totalDistanceMeters: Int,
    val totalDurationMinutes: Int,
    val polylineEncoded: String? = null, // Encoded polyline для рендера на карте.
    val decodedPath: List<RoutePoint>? = null, // Готовые точки от Yandex Transport — без encode/decode.
    val isEstimated: Boolean = false,
    val sourceLabel: String = "openrouteservice"
)

/**
 * Рекомендация места от оптимизатора.
 */
data class PlaceRecommendation(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val rating: Float?,
    val distanceFromRoute: Int, // Расстояние от маршрута в метрах.
    val reason: String // Причина рекомендации: "По пути", "Рядом с ...", и т.д.
)

/**
 * Предпочтения пользователя для подбора рекомендаций.
 */
data class TravelPreferences(
    val budgetLevel: BudgetLevel = BudgetLevel.MEDIUM,
    val interests: List<String> = emptyList(), // Например: "food", "museums", "nature".
    val travelStyle: TravelStyle = TravelStyle.BALANCED,
    val avoidCrowds: Boolean = false,
    val familyFriendly: Boolean = false
)

enum class BudgetLevel { LOW, MEDIUM, HIGH, LUXURY }
enum class TravelStyle { RELAXED, BALANCED, ACTIVE }

/**
 * Реализация по умолчанию на эвристике ближайшего соседа.
 *
 * Это простой жадный алгоритм: на каждом шаге он выбирает ближайшую ещё не посещённую точку.
 * Решение не гарантирует глобальный оптимум, но работает быстро и даёт приемлемый результат
 * для типичных маршрутов поездки.
 *
 * TODO: Добавить более сильные алгоритмы:
 * - 2-opt улучшение
 * - имитацию отжига
 * - генетический алгоритм
 * - внешний AI-сервис для сложной оптимизации
 */
@Singleton
class NearestNeighborRouteOptimizer @Inject constructor(
    private val openRouteServiceApi: OpenRouteServiceApi,
    private val nearbyPlacesProvider: NearbyPlacesProvider,
    private val yandexRouter: YandexRouter
) : RouteOptimizer {
    
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
        
        // Считаем длину исходного маршрута.
        val originalDistance = calculateTotalDistance(places)
        
        // Применяем алгоритм ближайшего соседа.
        val optimized = nearestNeighbor(places, startLocation)
        val optimizedDistance = calculateTotalDistance(optimized)
        
        // Собираем участки маршрута.
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
        val routeAnchors = buildRecommendationAnchors(currentPlaces, center)
        val searchTypes = buildSearchTypes(currentPlaces, preferences)
        val existingPlaceIds = currentPlaces.mapNotNull { it.placeId }.toSet()
        val existingNames = currentPlaces.map { it.name.trim().lowercase() }.toSet()
        val existingCoordinates = currentPlaces.map { LatLng(it.latitude, it.longitude) }

        val candidates = mutableListOf<PlaceSuggestion>()
        routeAnchors.forEach { anchor ->
            searchTypes.forEach { type ->
                candidates += nearbyPlacesProvider.getNearbyPlaces(
                    latitude = anchor.latitude,
                    longitude = anchor.longitude,
                    radius = radius,
                    type = type
                )
            }
        }

        return candidates
            .distinctBy { it.placeId }
            .filterNot { suggestion ->
                suggestion.placeId in existingPlaceIds ||
                    suggestion.name.trim().lowercase() in existingNames
            }
            .mapNotNull { suggestion ->
                val suggestionPoint = LatLng(suggestion.latitude, suggestion.longitude)
                val distanceFromRoute = existingCoordinates
                    .minOfOrNull { point ->
                        haversineDistance(
                            point.latitude,
                            point.longitude,
                            suggestionPoint.latitude,
                            suggestionPoint.longitude
                        ).toInt()
                    }
                    ?: haversineDistance(
                        center.latitude,
                        center.longitude,
                        suggestion.latitude,
                        suggestion.longitude
                    ).toInt()
                if (distanceFromRoute > radius) return@mapNotNull null

                val closestPlace = currentPlaces.minByOrNull { place ->
                    haversineDistance(
                        place.latitude,
                        place.longitude,
                        suggestion.latitude,
                        suggestion.longitude
                    )
                }
                val reason = when {
                    closestPlace != null && distanceFromRoute <= 250 ->
                        "В ${distanceFromRoute} м от ${closestPlace.name}"
                    closestPlace != null ->
                        "Рядом с ${closestPlace.name}"
                    else ->
                        "Недалеко от текущего маршрута"
                }

                PlaceRecommendation(
                    placeId = suggestion.placeId,
                    name = suggestion.name,
                    address = suggestion.address,
                    latitude = suggestion.latitude,
                    longitude = suggestion.longitude,
                    category = suggestion.category.displayName,
                    rating = suggestion.rating,
                    distanceFromRoute = distanceFromRoute,
                    reason = reason
                )
            }
            .sortedWith(
                compareByDescending<PlaceRecommendation> { it.rating ?: 0f }
                    .thenBy { it.distanceFromRoute }
            )
            .take(6)
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
        // 1) Сначала пробуем Yandex Transport — он один умеет TRANSIT и
        //    отдаёт реальные полилинии для всех режимов кроме BICYCLING.
        val yandexResult = yandexRouter.route(places, travelMode)
        if (yandexResult != null) {
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
                totalDistanceMeters = yandexResult.distanceMeters,
                totalDurationMinutes = yandexResult.durationMinutes,
                decodedPath = yandexResult.points,
                isEstimated = false,
                sourceLabel = when (travelMode) {
                    TravelMode.TRANSIT -> "Yandex Transit"
                    TravelMode.DRIVING -> "Yandex Driving"
                    TravelMode.WALKING -> "Yandex Pedestrian"
                    TravelMode.BICYCLING -> "Yandex"
                }
            )
        }

        // 2) Fallback — OpenRouteService для non-TRANSIT режимов.
        val remoteDetails = fetchDirectionsRoute(places, travelMode)
        if (remoteDetails != null) {
            return remoteDetails
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
            totalDurationMinutes = legs.sumOf { it.durationMinutes },
            isEstimated = true,
            sourceLabel = if (travelMode == TravelMode.TRANSIT) {
                "эвристическая оценка"
            } else {
                "локальный расчёт"
            }
        )
    }
    
    // Приватные вспомогательные методы.
    
    /**
     * Реализация алгоритма ближайшего соседа.
     */
    private fun nearestNeighbor(
        places: List<Place>,
        startLocation: LatLng?
    ): List<Place> {
        val remaining = places.toMutableList()
        val result = mutableListOf<Place>()
        
        // Стартуем с ближайшей точки к стартовой позиции или с первого места из списка.
        var currentLat = startLocation?.latitude ?: places.first().latitude
        var currentLon = startLocation?.longitude ?: places.first().longitude
        
        while (remaining.isNotEmpty()) {
            // Ищем ближайшее следующее место.
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
     * Считает общую длину маршрута.
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
     * Формула гаверсинусов для расстояния между двумя координатами.
     * Возвращает расстояние в метрах.
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // Радиус Земли в метрах.
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Оценивает время пешком в минутах.
     * Использует среднюю скорость 5 км/ч.
     */
    private fun estimateWalkingTime(distanceMeters: Int): Int {
        val walkingSpeedMps = 5000.0 / 3600.0 // 5 км/ч в м/с.
        return ceil(distanceMeters / walkingSpeedMps / 60).toInt()
    }
    
    /**
     * Оценивает время в пути в зависимости от режима передвижения.
     */
    private fun estimateTravelTime(distanceMeters: Int, mode: TravelMode): Int {
        val speedMps = when (mode) {
            TravelMode.WALKING -> 5000.0 / 3600.0    // 5 км/ч.
            TravelMode.BICYCLING -> 15000.0 / 3600.0 // 15 км/ч.
            TravelMode.DRIVING -> 30000.0 / 3600.0   // 30 км/ч в городе.
            TravelMode.TRANSIT -> 20000.0 / 3600.0   // Средняя скорость 20 км/ч.
        }
        return ceil(distanceMeters / speedMps / 60).toInt()
    }

    private suspend fun fetchDirectionsRoute(
        places: List<Place>,
        travelMode: TravelMode
    ): RouteDetails? {
        if (travelMode == TravelMode.TRANSIT) return null
        if (!hasValidApiKey() || places.size < 2) return null

        val response = runCatching {
            openRouteServiceApi.getDirections(
                profile = travelMode.toOpenRouteServiceProfile(),
                apiKey = BuildConfig.APP_OPENROUTESERVICE_API_KEY,
                request = OpenRouteServiceDirectionsRequest(
                    coordinates = places.map { place ->
                        listOf(place.longitude, place.latitude)
                    }
                )
            )
        }.getOrNull() ?: return null

        val route = response.routes.firstOrNull() ?: return null
        val segments = route.segments
        if (segments.isEmpty()) return null

        val mappedLegs = places.zipWithNext().mapIndexedNotNull { index, (from, to) ->
            val segment = segments.getOrNull(index) ?: return@mapIndexedNotNull null
            val distance = segment.distance?.roundToInt() ?: return@mapIndexedNotNull null
            val durationSeconds = segment.duration ?: 0.0
            RouteLeg(
                from = from,
                to = to,
                distanceMeters = distance,
                durationMinutes = ceil(durationSeconds / 60.0).toInt(),
                travelMode = travelMode
            )
        }

        if (mappedLegs.isEmpty()) return null

        return RouteDetails(
            places = places,
            legs = mappedLegs,
            totalDistanceMeters = route.summary?.distance?.roundToInt()
                ?: mappedLegs.sumOf { it.distanceMeters },
            totalDurationMinutes = route.summary?.duration
                ?.let { ceil(it / 60.0).toInt() }
                ?: mappedLegs.sumOf { it.durationMinutes },
            polylineEncoded = route.geometry,
            isEstimated = false,
            sourceLabel = "openrouteservice"
        )
    }

    private fun hasValidApiKey(): Boolean {
        val apiKey = BuildConfig.APP_OPENROUTESERVICE_API_KEY
        return apiKey.isNotBlank()
    }

    private fun buildRecommendationAnchors(
        currentPlaces: List<Place>,
        center: LatLng
    ): List<LatLng> {
        if (currentPlaces.isEmpty()) return listOf(center)
        val anchors = buildList {
            add(LatLng(currentPlaces.first().latitude, currentPlaces.first().longitude))
            if (currentPlaces.size > 2) {
                val middle = currentPlaces[currentPlaces.size / 2]
                add(LatLng(middle.latitude, middle.longitude))
            }
            add(LatLng(currentPlaces.last().latitude, currentPlaces.last().longitude))
            add(center)
        }
        return anchors.distinctBy { "${"%.5f".format(it.latitude)}:${"%.5f".format(it.longitude)}" }
    }

    private fun buildSearchTypes(
        currentPlaces: List<Place>,
        preferences: TravelPreferences?
    ): List<String?> {
        val interestTypes = preferences?.interests.orEmpty().flatMap { interest ->
            when (interest.lowercase()) {
                "food" -> listOf("restaurant", "cafe")
                "museums" -> listOf("museum")
                "nature" -> listOf("park", "tourist_attraction")
                "nightlife" -> listOf("bar", "night_club")
                "shopping" -> listOf("shopping_mall")
                else -> emptyList()
            }
        }
        val routeCategories = currentPlaces.map { it.category }.toSet()
        val complementaryTypes = buildList {
            if (routeCategories.none { it == com.triloo.data.model.PlaceCategory.RESTAURANT || it == com.triloo.data.model.PlaceCategory.CAFE }) {
                add("cafe")
            }
            if (routeCategories.none { it == com.triloo.data.model.PlaceCategory.PARK || it == com.triloo.data.model.PlaceCategory.NATURE }) {
                add("park")
            }
            if (routeCategories.none { it == com.triloo.data.model.PlaceCategory.VIEWPOINT || it == com.triloo.data.model.PlaceCategory.ATTRACTION }) {
                add("tourist_attraction")
            }
        }
        return listOf<String?>(null) + (interestTypes + complementaryTypes).distinct().take(3)
    }

    private fun TravelMode.toOpenRouteServiceProfile(): String {
        return when (this) {
            TravelMode.WALKING -> "foot-walking"
            TravelMode.DRIVING -> "driving-car"
            TravelMode.TRANSIT -> "driving-car"
            TravelMode.BICYCLING -> "cycling-regular"
        }
    }
}
