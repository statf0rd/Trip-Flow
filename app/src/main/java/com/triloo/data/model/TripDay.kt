package com.triloo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * TripDay — один день маршрута внутри поездки.
 */
@Entity(
    tableName = "trip_days",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TripDay(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val tripId: String,
    val date: LocalDate,
    val dayNumber: Int,                         // Номер дня: 1, 2 и далее.
    
    val title: String? = null,                  // Необязательное название, например "День музеев".
    val notes: String? = null,                  // Произвольные заметки на день.
    
    val weatherForecast: String? = null,        // Закешированная информация о погоде.
    val temperatureHigh: Int? = null,
    val temperatureLow: Int? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Place — точка маршрута или место интереса внутри конкретного дня поездки.
 */
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = TripDay::class,
            parentColumns = ["id"],
            childColumns = ["tripDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripDayId"), Index("tripId")]
)
data class Place(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val tripId: String,                         // Денормализован для упрощения запросов.
    val tripDayId: String,
    
    val name: String,
    val address: String? = null,
    val placeId: String? = null,                // Идентификатор места в Google Places.
    
    val latitude: Double,
    val longitude: Double,
    
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val iconEmoji: String? = null,              // Необязательная emoji-иконка.
    
    val orderIndex: Int = 0,                    // Порядок внутри дня.
    
    val scheduledTime: String? = null,          // Время в формате "09:00".
    val estimatedDuration: Int? = null,         // Оценка длительности в минутах.
    
    val openingHours: String? = null,           // Данные о часах работы из Places API.
    val rating: Float? = null,                  // Рейтинг Google.
    val priceLevel: Int? = null,                // Уровень цен от 0 до 4.
    val photoUrl: String? = null,               // Ссылка или reference на фото.
    
    val website: String? = null,
    val phoneNumber: String? = null,
    
    val notes: String? = null,                  // Пользовательские заметки.
    val isVisited: Boolean = false,             // Было ли место посещено.
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PlaceCategory(val emoji: String, val displayName: String) {
    ATTRACTION("🏛️", "Достопримечательность"),
    RESTAURANT("🍽️", "Ресторан"),
    CAFE("☕", "Кафе"),
    BAR("🍸", "Бар"),
    MUSEUM("🎨", "Музей"),
    PARK("🌳", "Парк"),
    BEACH("🏖️", "Пляж"),
    SHOPPING("🛍️", "Шоппинг"),
    ENTERTAINMENT("🎭", "Развлечения"),
    HOLIDAY("🎉", "Праздник"),
    TRANSPORT("🚇", "Транспорт"),
    VIEWPOINT("📸", "Смотровая"),
    NATURE("🌄", "Природа"),
    NIGHTLIFE("🌙", "Ночная жизнь"),
    OTHER("📍", "Другое");
    
    companion object {
        fun fromGoogleType(types: List<String>): PlaceCategory {
            return when {
                types.any { it.contains("restaurant") } -> RESTAURANT
                types.any { it.contains("cafe") } -> CAFE
                types.any { it.contains("bar") } -> BAR
                types.any { it.contains("museum") } -> MUSEUM
                types.any { it.contains("park") } -> PARK
                types.any { it.contains("beach") } -> BEACH
                types.any { it.contains("shopping") || it.contains("store") } -> SHOPPING
                types.any { it.contains("amusement") || it.contains("entertainment") } -> ENTERTAINMENT
                types.any { it.contains("festival") || it.contains("event") } -> HOLIDAY
                types.any { it.contains("transit") || it.contains("station") } -> TRANSPORT
                types.any { it.contains("natural") } -> NATURE
                types.any { it.contains("night_club") } -> NIGHTLIFE
                types.any { it.contains("tourist_attraction") } -> ATTRACTION
                else -> OTHER
            }
        }
    }
}

/**
 * Route — участок маршрута между двумя местами.
 */
data class Route(
    val originPlaceId: String,
    val destinationPlaceId: String,
    
    val distanceMeters: Int,
    val durationSeconds: Int,
    val durationInTraffic: Int? = null,
    
    val travelMode: TravelMode = TravelMode.DRIVING,
    
    val polylineEncoded: String? = null,        // Encoded polyline для отрисовки на карте.
    
    val fare: Double? = null,                   // Стоимость проезда, если доступна.
    val fareCurrency: String? = null
)

enum class TravelMode(val displayName: String, val icon: String) {
    WALKING("Пешком", "🚶"),
    DRIVING("На машине", "🚗"),
    TRANSIT("Общ. транспорт", "🚇"),
    BICYCLING("На велосипеде", "🚴")
}

