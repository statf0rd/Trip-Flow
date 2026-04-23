package com.triloo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Trip — основная сущность поездки со сроками, направлением и групповыми настройками.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val name: String,
    val destination: String,                    // Название города или страны.
    val destinationPlaceId: String? = null,     // Идентификатор места в Google Places.
    val destinationLatitude: Double? = null,     // Широта назначения.
    val destinationLongitude: Double? = null,    // Долгота назначения.
    val coverImageUrl: String? = null,          // Ссылка на фото-обложку.
    
    val startDate: LocalDate,
    val endDate: LocalDate,
    
    val baseCurrency: String = "RUB",           // Код валюты по ISO 4217.
    val budget: Double? = null,                 // Необязательный общий бюджет.
    
    val hotelName: String? = null,
    val hotelAddress: String? = null,
    val hotelPlaceId: String? = null,           // Идентификатор отеля в Google Places.
    val hotelLatitude: Double? = null,
    val hotelLongitude: Double? = null,
    
    val inviteCode: String = generateInviteCode(),  // Код приглашения для групповой поездки.
    val isGroupTrip: Boolean = false,
    val ownerId: String? = null,                // Идентификатор создателя поездки.
    
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
    PLANNING,       // Поездка ещё планируется.
    CONFIRMED,      // План подтверждён и готов к поездке.
    ONGOING,        // Поездка идёт прямо сейчас.
    COMPLETED,      // Поездка завершена.
    CANCELLED       // Поездка отменена.
}

/**
 * Participant — участник групповой поездки с его правами и состоянием геошаринга.
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
    OWNER,      // Создал поездку и имеет полный доступ.
    ADMIN,      // Может редактировать поездку и приглашать других.
    MEMBER      // Может просматривать поездку и добавлять расходы.
}
