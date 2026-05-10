package com.triloo.data.repository

import com.triloo.data.local.dao.DeletionLogDao
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.Place
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.model.TripStatus
import com.triloo.data.notifications.TripNotificationScheduler
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val deletionLogDao: DeletionLogDao,
    private val userProfileRepository: UserProfileRepository,
    private val tripNotificationScheduler: TripNotificationScheduler,
    private val onlineSyncRepository: OnlineSyncRepository
) {
    
    // Операции с поездками.
    
    fun observeAllTrips(): Flow<List<Trip>> = tripDao.observeAllTrips()

    /** Глобальные счётчики для шапки настроек: «4 поездки · 21 день · 37 мест». */
    fun observeTotalDayCount(): Flow<Int> = placeDao.observeTotalDayCount()
    fun observeTotalPlaceCount(): Flow<Int> = placeDao.observeTotalPlaceCount()

    suspend fun getAllTrips(): List<Trip> = tripDao.getAllTrips()
    
    fun observeTripById(tripId: String): Flow<Trip?> = tripDao.observeTripById(tripId)
    
    fun observeCurrentTrip(): Flow<Trip?> = tripDao.observeCurrentTrip()
    
    fun observeUpcomingTrips(): Flow<List<Trip>> = tripDao.observeUpcomingTrips()
    
    fun observePastTrips(): Flow<List<Trip>> = tripDao.observePastTrips()
    
    suspend fun getTripById(tripId: String): Trip? = tripDao.getTripById(tripId)
    
    suspend fun getTripByInviteCode(code: String): Trip? = tripDao.getTripByInviteCode(code)
    
    suspend fun createTrip(trip: Trip): String {
        tripDao.insertTrip(trip)
        // Автоматически создаём дни поездки по диапазону дат.
        createTripDays(trip)
        tripNotificationScheduler.syncTrip(trip.id)
        onlineSyncRepository.syncTripAsync(trip.id)
        return trip.id
    }
    
    suspend fun updateTrip(trip: Trip) {
        requireTripManagementPermission(trip.id, "изменять поездку")
        tripDao.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
        tripNotificationScheduler.syncTrip(trip.id)
        onlineSyncRepository.syncTripAsync(trip.id)
    }
    
    suspend fun deleteTrip(tripId: String) {
        requireTripManagementPermission(tripId, "удалять поездку")
        logDeletion(tripId, RelayEntityType.TRIP, tripId)
        tripDao.deleteTripById(tripId)
        tripDao.deleteParticipantsByTrip(tripId)
        tripNotificationScheduler.cancelTrip(tripId)
    }
    
    suspend fun updateTripStatus(tripId: String, status: TripStatus) {
        requireTripManagementPermission(tripId, "менять статус поездки")
        tripDao.getTripById(tripId)?.let { trip ->
            tripDao.updateTrip(trip.copy(status = status, updatedAt = System.currentTimeMillis()))
            tripNotificationScheduler.syncTrip(tripId)
            onlineSyncRepository.syncTripAsync(tripId)
        }
    }
    
    private suspend fun createTripDays(trip: Trip) {
        val days = mutableListOf<TripDay>()
        var currentDate = trip.startDate
        var dayNumber = 1
        
        while (!currentDate.isAfter(trip.endDate)) {
            days.add(
                TripDay(
                    tripId = trip.id,
                    date = currentDate,
                    dayNumber = dayNumber
                )
            )
            currentDate = currentDate.plusDays(1)
            dayNumber++
        }
        
        placeDao.insertTripDays(days)
    }
    
    // Операции с участниками.
    
    fun observeParticipants(tripId: String): Flow<List<Participant>> =
        tripDao.observeParticipants(tripId)
    
    suspend fun getParticipants(tripId: String): List<Participant> =
        tripDao.getParticipants(tripId)
    
    suspend fun addParticipant(participant: Participant) {
        val trip = tripDao.getTripById(participant.tripId)
            ?: throw IllegalStateException("Поездка не найдена")
        val currentUserId = userProfileRepository.getProfile().userId
        val canBootstrapOwner = participant.userId == currentUserId &&
            trip.ownerId == currentUserId &&
            tripDao.getParticipantCount(participant.tripId) == 0
        val canSelfJoinByInvite = participant.userId == currentUserId &&
            tripDao.getParticipant(participant.tripId, currentUserId) == null
        if (!canBootstrapOwner && !canSelfJoinByInvite) {
            requireParticipantManagementPermission(participant.tripId, "добавлять участников")
        }
        tripDao.insertParticipant(participant)
        // Помечаем поездку как групповую.
        tripDao.getTripById(participant.tripId)?.let { trip ->
            if (!trip.isGroupTrip) {
                tripDao.updateTrip(
                    trip.copy(
                        isGroupTrip = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        onlineSyncRepository.syncTripAsync(participant.tripId)
    }
    
    suspend fun removeParticipant(tripId: String, userId: String) {
        requireParticipantManagementPermission(tripId, "удалять участников")
        logDeletion(tripId, RelayEntityType.PARTICIPANT, userId)
        tripDao.removeParticipant(tripId, userId)
        onlineSyncRepository.syncTripAsync(tripId)
    }
    
    suspend fun updateParticipantLocation(
        tripId: String,
        userId: String,
        latitude: Double,
        longitude: Double
    ) {
        val currentUserId = userProfileRepository.getProfile().userId
        if (currentUserId != userId) {
            requireParticipantManagementPermission(tripId, "изменять местоположение участника")
        }
        tripDao.updateParticipantLocation(
            tripId = tripId,
            userId = userId,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun updateParticipantOnlineStatus(
        tripId: String,
        userId: String,
        isOnline: Boolean
    ) {
        val currentUserId = userProfileRepository.getProfile().userId
        if (currentUserId != userId) {
            requireParticipantManagementPermission(tripId, "менять online-статус участника")
        }
        tripDao.updateParticipantOnlineStatus(
            tripId = tripId,
            userId = userId,
            isOnline = isOnline,
            timestamp = System.currentTimeMillis()
        )
    }
    
    // Операции с днями поездки.
    
    fun observeTripDays(tripId: String): Flow<List<TripDay>> =
        placeDao.observeTripDays(tripId)
    
    suspend fun getTripDays(tripId: String): List<TripDay> =
        placeDao.getTripDays(tripId)
    
    suspend fun getTripDayById(dayId: String): TripDay? =
        placeDao.getTripDayById(dayId)
    
    suspend fun updateTripDay(tripDay: TripDay) {
        requireItineraryPermission(tripDay.tripId, "изменять день поездки")
        placeDao.updateTripDay(tripDay.copy(updatedAt = System.currentTimeMillis()))
        onlineSyncRepository.syncTripAsync(tripDay.tripId)
    }
    
    // Операции с местами.
    
    fun observePlacesByDay(tripDayId: String): Flow<List<Place>> =
        placeDao.observePlacesByDay(tripDayId)
    
    fun observePlacesByTrip(tripId: String): Flow<List<Place>> =
        placeDao.observePlacesByTrip(tripId)
    
    suspend fun getPlacesByDay(tripDayId: String): List<Place> =
        placeDao.getPlacesByDay(tripDayId)
    
    suspend fun getPlacesByTrip(tripId: String): List<Place> =
        placeDao.getPlacesByTrip(tripId)
    
    suspend fun getPlaceById(placeId: String): Place? =
        placeDao.getPlaceById(placeId)
    
    suspend fun addPlace(place: Place) {
        requireItineraryPermission(place.tripId, "добавлять места")
        val maxOrder = placeDao.getMaxOrderIndex(place.tripDayId) ?: -1
        placeDao.insertPlace(place.copy(orderIndex = maxOrder + 1))
        tripNotificationScheduler.syncTrip(place.tripId)
        onlineSyncRepository.syncTripAsync(place.tripId)
    }
    
    suspend fun updatePlace(place: Place) {
        requireItineraryPermission(place.tripId, "редактировать места")
        placeDao.updatePlace(place.copy(updatedAt = System.currentTimeMillis()))
        tripNotificationScheduler.syncTrip(place.tripId)
        onlineSyncRepository.syncTripAsync(place.tripId)
    }
    
    suspend fun deletePlace(placeId: String) {
        var tripIdForRefresh: String? = null
        placeDao.getPlaceById(placeId)?.let { place ->
            requireItineraryPermission(place.tripId, "удалять места")
            logDeletion(place.tripId, RelayEntityType.PLACE, placeId)
            tripIdForRefresh = place.tripId
        }
        placeDao.deletePlaceById(placeId)
        tripIdForRefresh?.let {
            tripNotificationScheduler.syncTrip(it)
            onlineSyncRepository.syncTripAsync(it)
        }
    }
    
    suspend fun reorderPlaces(tripDayId: String, orderedPlaceIds: List<String>) {
        placeDao.getTripDayById(tripDayId)?.tripId?.let {
            requireItineraryPermission(it, "менять порядок мест")
            placeDao.reorderPlaces(tripDayId, orderedPlaceIds)
            tripNotificationScheduler.syncTrip(it)
            onlineSyncRepository.syncTripAsync(it)
        }
    }
    
    suspend fun markPlaceVisited(placeId: String, visited: Boolean) {
        placeDao.getPlaceById(placeId)?.tripId?.let {
            requireItineraryPermission(it, "отмечать места как посещённые")
            placeDao.updatePlaceVisited(placeId, visited)
            tripNotificationScheduler.syncTrip(it)
            onlineSyncRepository.syncTripAsync(it)
        }
    }

    private suspend fun logDeletion(tripId: String, type: RelayEntityType, entityId: String) {
        val deviceId = userProfileRepository.getOrCreateDeviceId()
        deletionLogDao.insertDeletion(
            DeletionLog(
                tripId = tripId,
                entityType = type,
                entityId = entityId,
                deviceId = deviceId
            )
        )
    }

    private suspend fun requireTripManagementPermission(tripId: String, action: String) {
        requireRole(tripId, setOf(ParticipantRole.OWNER, ParticipantRole.ADMIN), action)
    }

    private suspend fun requireParticipantManagementPermission(tripId: String, action: String) {
        requireRole(tripId, setOf(ParticipantRole.OWNER, ParticipantRole.ADMIN), action)
    }

    private suspend fun requireItineraryPermission(tripId: String, action: String) {
        requireRole(tripId, setOf(ParticipantRole.OWNER, ParticipantRole.ADMIN), action)
    }

    private suspend fun requireRole(
        tripId: String,
        allowedRoles: Set<ParticipantRole>,
        action: String
    ) {
        val trip = tripDao.getTripById(tripId) ?: return
        if (!trip.isGroupTrip) return

        val currentUserId = userProfileRepository.getProfile().userId
        val role = tripDao.getParticipant(tripId, currentUserId)?.role
            ?: throw IllegalStateException("Недостаточно прав: вы не участник поездки")
        if (role !in allowedRoles) {
            throw IllegalStateException("Недостаточно прав, чтобы $action")
        }
    }
}
