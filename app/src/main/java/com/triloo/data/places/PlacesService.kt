package com.triloo.data.places

import com.triloo.BuildConfig
import com.triloo.data.model.PlaceCategory
import com.triloo.data.remote.GeosuggestApi
import com.yandex.mapkit.GeoObject
import com.yandex.mapkit.GeoObjectCollection
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.BusinessObjectMetadata
import com.yandex.mapkit.search.BusinessPhotoObjectMetadata
import com.yandex.mapkit.search.BusinessRating1xObjectMetadata
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.Snippet
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.search.SuggestType
import com.yandex.mapkit.search.ToponymObjectMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Контракт источника nearby-мест для рекомендаций на карте и в маршрутах.
 */
interface NearbyPlacesProvider {
    suspend fun getNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radius: Int = 1000,
        type: String? = null
    ): List<PlaceSuggestion>
}

/**
 * Сервис поиска мест на базе Yandex MapKit Search и Geosuggest.
 * При недоступности реального провайдера возвращает пустой результат, а не фейковые данные.
 */
@Singleton
class PlacesService @Inject constructor(
    private val geosuggestApi: GeosuggestApi
) : NearbyPlacesProvider {
    private val searchManager: SearchManager by lazy {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    }
    private val suggestionCache = ConcurrentHashMap<String, PlaceSuggestion>()
    private val detailsCache = ConcurrentHashMap<String, PlaceDetails>()

    /**
     * Ищет места по текстовому запросу и возвращает список подсказок.
     */
    suspend fun searchPlaces(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
        radius: Int = 50_000
    ): List<PlaceSuggestion> {
        if (query.isBlank() || query.length < 2) {
            return emptyList()
        }

        if (!hasAnyRemoteProvider()) return emptyList()

        delay(150)

        if (hasValidMapKitKey()) {
            val yandexSuggestions = runCatching {
                searchWithMapKitSuggest(query, latitude, longitude, radius)
            }.getOrDefault(emptyList())
            if (yandexSuggestions.isNotEmpty()) {
                return yandexSuggestions.cacheSuggestions()
            }
        }

        if (hasValidGeosuggestKey()) {
            val geosuggestSuggestions = runCatching {
                searchWithGeosuggest(query)
            }.getOrDefault(emptyList())
            if (geosuggestSuggestions.isNotEmpty()) {
                return geosuggestSuggestions.cacheSuggestions()
            }
        }

        return emptyList()
    }

    /**
     * Загружает подробную информацию о месте.
     */
    suspend fun getPlaceDetails(placeId: String): PlaceDetails? {
        detailsCache[placeId]?.let { return it }

        if (!hasValidMapKitKey()) {
            return suggestionCache[placeId]?.toPlaceDetails()
        }

        val resolved = when {
            placeId.startsWith("ymaps") -> resolveUri(placeId)
            else -> suggestionCache[placeId]?.toPlaceDetails()
        }

        resolved?.let { detailsCache[placeId] = it.copy(placeId = placeId) }
        return resolved?.copy(placeId = placeId)
    }

    /**
     * Возвращает nearby-места вокруг заданных координат.
     */
    override suspend fun getNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radius: Int,
        type: String?
    ): List<PlaceSuggestion> {
        if (!hasValidMapKitKey()) return emptyList()

        val nearby = runCatching {
            searchNearbyWithMapKit(latitude, longitude, radius, type)
        }.getOrDefault(emptyList())

        return if (nearby.isNotEmpty()) {
            nearby.cacheSuggestions()
        } else {
            emptyList()
        }
    }

    private suspend fun searchWithMapKitSuggest(
        query: String,
        latitude: Double?,
        longitude: Double?,
        radius: Int
    ): List<PlaceSuggestion> = suspendCancellableCoroutine { continuation ->
        val session = searchManager.createSuggestSession()
        val userPoint = latitude?.let { lat ->
            longitude?.let { lon -> Point(lat, lon) }
        }
        val options = SuggestOptions()
            .setSuggestTypes(SuggestType.GEO.value + SuggestType.BIZ.value)
            .setSuggestWords(false)
            .setStrictBounds(userPoint != null)
        userPoint?.let { options.setUserPosition(it) }

        session.suggest(
            query,
            buildSearchWindow(latitude, longitude, radius),
            options,
            object : SuggestSession.SuggestListener {
                override fun onResponse(response: com.yandex.mapkit.search.SuggestResponse) {
                    if (!continuation.isActive) return
                    continuation.resume(
                        response.items.mapNotNull { item ->
                            val point = item.center ?: return@mapNotNull null
                            if (item.isWordItem) return@mapNotNull null
                            val title = item.displayText.takeUnless { it.isNullOrBlank() }
                                ?: item.title?.text
                                ?: item.searchText
                                ?: return@mapNotNull null
                            val subtitle = item.subtitle?.text.orEmpty()
                            PlaceSuggestion(
                                placeId = item.uri.orEmpty().ifBlank {
                                    "yandex:${point.latitude},${point.longitude}:$title"
                                },
                                name = title,
                                address = subtitle,
                                category = categoryFromTokens(item.tags.orEmpty() + subtitle),
                                latitude = point.latitude,
                                longitude = point.longitude
                            )
                        }.take(6)
                    )
                }

                override fun onError(error: com.yandex.runtime.Error) {
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }
        )

        continuation.invokeOnCancellation { session.reset() }
    }

    private suspend fun searchWithGeosuggest(query: String): List<PlaceSuggestion> {
        val response = geosuggestApi.suggest(
            apiKey = BuildConfig.APP_GEOSUGGEST_API_KEY,
            text = query
        )
        return buildList {
            response.results.forEach { item ->
                val uri = item.uri ?: return@forEach
                val suggestion = resolveUri(uri)?.copy(placeId = uri)?.toSuggestion()
                if (suggestion != null) {
                    add(suggestion)
                }
                if (size >= 6) {
                    return@buildList
                }
            }
        }
    }

    private suspend fun searchNearbyWithMapKit(
        latitude: Double,
        longitude: Double,
        radius: Int,
        type: String?
    ): List<PlaceSuggestion> {
        val query = nearbyQueryForType(type)
        val response = searchManager.submitAwait(
            query = query,
            geometry = Geometry.fromCircle(
                Circle(
                    Point(latitude, longitude),
                    radius.toFloat()
                )
            ),
            options = buildSearchOptions(
                userPoint = Point(latitude, longitude),
                searchTypes = SearchType.BIZ.value + SearchType.GEO.value
            )
        ) ?: return emptyList()

        return response.extractGeoObjects()
            .mapNotNull { geoObject ->
                geoObject.toPlaceDetails()?.toSuggestion()
            }
            .sortedBy { suggestion ->
                distanceMeters(
                    latitude = latitude,
                    longitude = longitude,
                    otherLatitude = suggestion.latitude,
                    otherLongitude = suggestion.longitude
                )
            }
            .take(8)
    }

    private suspend fun resolveUri(uri: String): PlaceDetails? {
        if (!hasValidMapKitKey()) return null
        return searchManager.resolveUriAwait(
            uri = uri,
            options = buildSearchOptions()
        )?.extractGeoObjects()
            ?.firstNotNullOfOrNull { geoObject ->
                geoObject.toPlaceDetails(uri)
            }
    }

    private fun buildSearchOptions(
        userPoint: Point? = null,
        searchTypes: Int = SearchType.BIZ.value + SearchType.GEO.value
    ): SearchOptions {
        return SearchOptions()
            .setGeometry(true)
            .setResultPageSize(8)
            .setSearchTypes(searchTypes)
            .setSnippets(Snippet.BUSINESS_RATING1X.value + Snippet.PHOTOS.value)
            .also { options ->
                userPoint?.let { options.setUserPosition(it) }
            }
    }

    private fun buildSearchWindow(
        latitude: Double?,
        longitude: Double?,
        radius: Int
    ): BoundingBox {
        if (latitude == null || longitude == null) {
            return BoundingBox(
                Point(-85.0, -180.0),
                Point(85.0, 180.0)
            )
        }

        val latDelta = radius / 111_320.0
        val lonDelta = radius / (111_320.0 * cos(Math.toRadians(latitude))).coerceAtLeast(1e-6)

        return BoundingBox(
            Point((latitude - latDelta).coerceIn(-85.0, 85.0), longitude - lonDelta),
            Point((latitude + latDelta).coerceIn(-85.0, 85.0), longitude + lonDelta)
        )
    }

    private fun Response.extractGeoObjects(): List<GeoObject> {
        return collection.children.flatMap { it.flatten() }
    }

    private fun GeoObjectCollection.Item.flatten(): List<GeoObject> {
        obj?.let { return listOf(it) }
        return collection?.children.orEmpty().flatMap { child -> child.flatten() }
    }

    private fun GeoObject.toPlaceDetails(placeIdOverride: String? = null): PlaceDetails? {
        val point = resolvePoint() ?: return null
        val metadata = metadataContainer
        val business = metadata.getItem(BusinessObjectMetadata::class.java)
        val toponym = metadata.getItem(ToponymObjectMetadata::class.java)
        val rating = metadata.getItem(BusinessRating1xObjectMetadata::class.java)?.score
        val photos = metadata.getItem(BusinessPhotoObjectMetadata::class.java)
        val categoryTokens = buildList<String> {
            add(name.orEmpty())
            add(descriptionText.orEmpty())
            business?.categories?.forEach { category ->
                category.name?.let(::add)
                category.categoryClass?.let(::add)
                addAll(category.tags.orEmpty().mapNotNull { it })
            }
        }

        return PlaceDetails(
            placeId = placeIdOverride
                ?: business?.oid
                ?: toponym?.id
                ?: "yandex:${point.latitude},${point.longitude}:${name.orEmpty()}",
            name = business?.name?.takeIf { it.isNotBlank() } ?: name.orEmpty(),
            address = business?.address?.formattedAddress
                ?: toponym?.address?.formattedAddress
                ?: descriptionText.orEmpty(),
            latitude = point.latitude,
            longitude = point.longitude,
            category = categoryFromTokens(categoryTokens),
            rating = rating,
            openingHours = business?.workingHours?.text,
            phoneNumber = business?.phones?.firstOrNull()?.formattedNumber,
            website = business?.links
                ?.mapNotNull { it.link?.href }
                ?.firstOrNull(),
            photoUrl = photos?.photos
                ?.firstOrNull()
                ?.links
                ?.firstOrNull()
                ?.uri
        )
    }

    private fun GeoObject.resolvePoint(): Point? {
        geometry.firstNotNullOfOrNull { it.point }?.let { return it }
        metadataContainer.getItem(ToponymObjectMetadata::class.java)?.balloonPoint?.let { return it }
        return boundingBox?.center()
    }

    private fun categoryFromTokens(tokens: Collection<String>): PlaceCategory {
        val normalized = tokens.joinToString(" ").lowercase()
        return when {
            normalized.contains("restaurant") || normalized.contains("ресторан") -> PlaceCategory.RESTAURANT
            normalized.contains("cafe") || normalized.contains("коф") || normalized.contains("кафе") -> PlaceCategory.CAFE
            normalized.contains("bar") || normalized.contains("бар") || normalized.contains("pub") -> PlaceCategory.BAR
            normalized.contains("museum") || normalized.contains("музей") || normalized.contains("gallery") -> PlaceCategory.MUSEUM
            normalized.contains("park") || normalized.contains("парк") || normalized.contains("garden") -> PlaceCategory.PARK
            normalized.contains("beach") || normalized.contains("пляж") -> PlaceCategory.BEACH
            normalized.contains("mall") || normalized.contains("shopping") || normalized.contains("магаз") -> PlaceCategory.SHOPPING
            normalized.contains("hotel") || normalized.contains("hostel") || normalized.contains("отель") -> PlaceCategory.OTHER
            normalized.contains("station") || normalized.contains("metro") || normalized.contains("вокзал") -> PlaceCategory.TRANSPORT
            normalized.contains("view") || normalized.contains("смотров") -> PlaceCategory.VIEWPOINT
            normalized.contains("night") || normalized.contains("club") || normalized.contains("ноч") -> PlaceCategory.NIGHTLIFE
            normalized.contains("nature") || normalized.contains("природ") -> PlaceCategory.NATURE
            normalized.contains("theatre") || normalized.contains("театр") || normalized.contains("cinema") -> PlaceCategory.ENTERTAINMENT
            normalized.contains("landmark") || normalized.contains("attraction") || normalized.contains("достопримеч") -> PlaceCategory.ATTRACTION
            else -> PlaceCategory.OTHER
        }
    }

    private fun nearbyQueryForType(type: String?): String {
        return when (type?.lowercase()) {
            "cafe" -> "кафе"
            "restaurant" -> "ресторан"
            "bar" -> "бар"
            "museum" -> "музей"
            "park" -> "парк"
            "tourist_attraction" -> "достопримечательности"
            "shopping_mall" -> "торговый центр"
            "night_club" -> "ночной клуб"
            else -> "интересные места"
        }
    }

    private fun hasAnyRemoteProvider(): Boolean {
        return hasValidMapKitKey() || hasValidGeosuggestKey()
    }

    private fun hasValidMapKitKey(): Boolean {
        return BuildConfig.APP_MAPKIT_VIEW_ENABLED && BuildConfig.APP_MAPKIT_API_KEY.isNotBlank()
    }

    private fun hasValidGeosuggestKey(): Boolean {
        return BuildConfig.APP_GEOSUGGEST_API_KEY.isNotBlank()
    }

    private fun List<PlaceSuggestion>.cacheSuggestions(): List<PlaceSuggestion> {
        forEach { suggestion ->
            suggestionCache[suggestion.placeId] = suggestion
            detailsCache.putIfAbsent(suggestion.placeId, suggestion.toPlaceDetails())
        }
        return this
    }

    // Ниже оставлены вспомогательные данные для локальных preview/debug-сценариев,
    // но runtime-поток больше не использует их как fallback для пользователя.

    private fun getMockSuggestions(query: String): List<PlaceSuggestion> {
        val lowerQuery = query.lowercase()
        return mockSuggestions.filter { suggestion ->
            suggestion.name.lowercase().contains(lowerQuery) ||
                suggestion.address.lowercase().contains(lowerQuery)
        }.take(5)
    }

    private fun getMockNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radius: Int,
        type: String?
    ): List<PlaceSuggestion> {
        val normalizedType = type?.lowercase()
        return mockSuggestions
            .filter { suggestion ->
                normalizedType == null || suggestion.matchesType(normalizedType)
            }
            .map { suggestion ->
                suggestion to distanceMeters(
                    latitude = latitude,
                    longitude = longitude,
                    otherLatitude = suggestion.latitude,
                    otherLongitude = suggestion.longitude
                )
            }
            .filter { (_, distanceMeters) -> distanceMeters <= radius }
            .sortedBy { (_, distanceMeters) -> distanceMeters }
            .map { (suggestion, _) -> suggestion }
            .take(8)
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
        suggestion.placeId to suggestion.toPlaceDetails().copy(
            openingHours = "09:00 - 18:00",
            phoneNumber = "+7 (495) 123-45-67",
            website = "https://example.com"
        )
    }

    private fun distanceMeters(
        latitude: Double,
        longitude: Double,
        otherLatitude: Double,
        otherLongitude: Double
    ): Int {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(otherLatitude - latitude)
        val dLon = Math.toRadians(otherLongitude - longitude)
        val originLatitude = Math.toRadians(latitude)
        val destinationLatitude = Math.toRadians(otherLatitude)
        val a = sin(dLat / 2).let { it * it } +
            cos(originLatitude) * cos(destinationLatitude) *
            sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toInt()
    }

    private fun BoundingBox.center(): Point {
        return Point(
            (southWest.latitude + northEast.latitude) / 2.0,
            (southWest.longitude + northEast.longitude) / 2.0
        )
    }

    private fun PlaceSuggestion.matchesType(type: String): Boolean {
        return when (type) {
            "cafe" -> category == PlaceCategory.CAFE
            "restaurant" -> category == PlaceCategory.RESTAURANT
            "bar" -> category == PlaceCategory.BAR
            "museum" -> category == PlaceCategory.MUSEUM
            "park" -> category == PlaceCategory.PARK || category == PlaceCategory.NATURE
            "tourist_attraction" -> category == PlaceCategory.ATTRACTION || category == PlaceCategory.VIEWPOINT
            "shopping_mall" -> category == PlaceCategory.SHOPPING
            "night_club" -> category == PlaceCategory.NIGHTLIFE
            else -> true
        }
    }

    private fun PlaceSuggestion.toPlaceDetails(): PlaceDetails {
        return PlaceDetails(
            placeId = placeId,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            category = category,
            rating = rating
        )
    }

    private fun PlaceDetails.toSuggestion(): PlaceSuggestion {
        return PlaceSuggestion(
            placeId = placeId,
            name = name,
            address = address,
            category = category,
            latitude = latitude,
            longitude = longitude,
            rating = rating
        )
    }

    private suspend fun SearchManager.submitAwait(
        query: String,
        geometry: Geometry,
        options: SearchOptions
    ): Response? = suspendCancellableCoroutine { continuation ->
        val session = submit(
            query,
            geometry,
            options,
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    }
                }

                override fun onSearchError(error: com.yandex.runtime.Error) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        )

        continuation.invokeOnCancellation { session.cancel() }
    }

    private suspend fun SearchManager.resolveUriAwait(
        uri: String,
        options: SearchOptions
    ): Response? = suspendCancellableCoroutine { continuation ->
        val session = resolveURI(
            uri,
            options,
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    }
                }

                override fun onSearchError(error: com.yandex.runtime.Error) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        )

        continuation.invokeOnCancellation { session.cancel() }
    }
}

/**
 * Подсказка места из поисковой выдачи.
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
 * Подробная информация о месте.
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
