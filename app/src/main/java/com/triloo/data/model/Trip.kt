package com.triloo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Trip — The core entity representing a travel journey
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val name: String,
    val destination: String,                    // City/Country name
    val destinationPlaceId: String? = null,     // Google Place ID
    val coverImageUrl: String? = null,          // Cover photo URL
    
    val startDate: LocalDate,
    val endDate: LocalDate,
    
    val baseCurrency: String = "RUB",           // ISO 4217 currency code
    val budget: Double? = null,                 // Optional total budget
    
    val hotelName: String? = null,
    val hotelAddress: String? = null,
    val hotelPlaceId: String? = null,           // Google Place ID for hotel
    val hotelLatitude: Double? = null,
    val hotelLongitude: Double? = null,
    
    val inviteCode: String = generateInviteCode(),  // For group trips
    val isGroupTrip: Boolean = false,
    val ownerId: String? = null,                // User ID of creator
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    val status: TripStatus = TripStatus.PLANNING
) {
    val durationDays: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    
    val isUpcoming: Boolean
        get() = startDate > LocalDate.now()
    
    val isOngoing: Boolean
        get() = LocalDate.now() in startDate..endDate
    
    val isPast: Boolean
        get() = endDate < LocalDate.now()
    
    companion object {
        private fun generateInviteCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }
}

enum class TripStatus {
    PLANNING,       // Still being planned
    CONFIRMED,      // All set, ready to go
    ONGOING,        // Currently traveling
    COMPLETED,      // Trip finished
    CANCELLED       // Trip cancelled
}

/**
 * Participant — A person participating in a group trip
 */
@Entity(
    tableName = "participants",
    primaryKeys = ["tripId", "userId"]
)
data class Participant(
    val tripId: String,
    val userId: String,
    
    val displayName: String,
    val avatarUrl: String? = null,
    val email: String? = null,
    
    val role: ParticipantRole = ParticipantRole.MEMBER,
    val shareLocation: Boolean = true,
    
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationUpdate: Long? = null,
    
    val joinedAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ParticipantRole {
    OWNER,      // Created the trip, full permissions
    ADMIN,      // Can edit trip, invite others
    MEMBER      // Can view and add expenses
}

