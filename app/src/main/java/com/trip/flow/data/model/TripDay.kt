package com.trip.flow.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * TripDay — Represents a single day within a trip's itinerary
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
    val dayNumber: Int,                         // Day 1, Day 2, etc.
    
    val title: String? = null,                  // Optional: "Museum Day", "Beach Day"
    val notes: String? = null,                  // Free-form notes for the day
    
    val weatherForecast: String? = null,        // Cached weather info
    val temperatureHigh: Int? = null,
    val temperatureLow: Int? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Place — A point of interest within a trip day
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
    
    val tripId: String,                         // Denormalized for easier queries
    val tripDayId: String,
    
    val name: String,
    val address: String? = null,
    val placeId: String? = null,                // Google Place ID
    
    val latitude: Double,
    val longitude: Double,
    
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val iconEmoji: String? = null,              // Optional emoji icon
    
    val orderIndex: Int = 0,                    // Order within the day
    
    val scheduledTime: String? = null,          // "09:00" format
    val estimatedDuration: Int? = null,         // Minutes
    
    val openingHours: String? = null,           // Cached from Places API
    val rating: Float? = null,                  // Google rating
    val priceLevel: Int? = null,                // 0-4 price level
    val photoUrl: String? = null,               // Photo reference
    
    val website: String? = null,
    val phoneNumber: String? = null,
    
    val notes: String? = null,                  // User notes
    val isVisited: Boolean = false,             // Mark as visited
    
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
 * Route between two places
 */
data class Route(
    val originPlaceId: String,
    val destinationPlaceId: String,
    
    val distanceMeters: Int,
    val durationSeconds: Int,
    val durationInTraffic: Int? = null,
    
    val travelMode: TravelMode = TravelMode.DRIVING,
    
    val polylineEncoded: String? = null,        // Encoded polyline for map
    
    val fare: Double? = null,                   // Transit fare if available
    val fareCurrency: String? = null
)

enum class TravelMode(val displayName: String, val icon: String) {
    WALKING("Пешком", "🚶"),
    DRIVING("На машине", "🚗"),
    TRANSIT("Общ. транспорт", "🚇"),
    BICYCLING("На велосипеде", "🚴")
}

