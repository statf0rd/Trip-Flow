package com.triloo.data.repository

import com.triloo.data.local.dao.DeletionLogDao
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Participant
import com.triloo.data.model.Place
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.model.TripStatus
import com.triloo.data.user.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val deletionLogDao: DeletionLogDao,
    private val userProfileRepository: UserProfileRepository
) {
    
    // Trip Operations
    
    fun observeAllTrips(): Flow<List<Trip>> = tripDao.observeAllTrips()

    suspend fun getAllTrips(): List<Trip> = tripDao.getAllTrips()
    
    fun observeTripById(tripId: String): Flow<Trip?> = tripDao.observeTripById(tripId)
    
    fun observeCurrentTrip(): Flow<Trip?> = tripDao.observeCurrentTrip()
    
    fun observeUpcomingTrips(): Flow<List<Trip>> = tripDao.observeUpcomingTrips()
    
    fun observePastTrips(): Flow<List<Trip>> = tripDao.observePastTrips()
    
    suspend fun getTripById(tripId: String): Trip? = tripDao.getTripById(tripId)
    
    suspend fun getTripByInviteCode(code: String): Trip? = tripDao.getTripByInviteCode(code)
    
    suspend fun createTrip(trip: Trip): String {
        tripDao.insertTrip(trip)
        // Auto-create trip days based on date range
        createTripDays(trip)
        return trip.id
    }
    
    suspend fun updateTrip(trip: Trip) {
        tripDao.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteTrip(tripId: String) {
        logDeletion(tripId, RelayEntityType.TRIP, tripId)
        tripDao.deleteTripById(tripId)
        tripDao.deleteParticipantsByTrip(tripId)
    }
    
    suspend fun updateTripStatus(tripId: String, status: TripStatus) {
        tripDao.getTripById(tripId)?.let { trip ->
            tripDao.updateTrip(trip.copy(status = status, updatedAt = System.currentTimeMillis()))
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
    
    // Participant Operations
    
    fun observeParticipants(tripId: String): Flow<List<Participant>> =
        tripDao.observeParticipants(tripId)
    
    suspend fun getParticipants(tripId: String): List<Participant> =
        tripDao.getParticipants(tripId)
    
    suspend fun addParticipant(participant: Participant) {
        tripDao.insertParticipant(participant)
        // Mark trip as group trip
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
    }
    
    suspend fun removeParticipant(tripId: String, userId: String) {
        logDeletion(tripId, RelayEntityType.PARTICIPANT, userId)
        tripDao.removeParticipant(tripId, userId)
    }
    
    suspend fun updateParticipantLocation(
        tripId: String,
        userId: String,
        latitude: Double,
        longitude: Double
    ) {
        tripDao.updateParticipantLocation(
            tripId = tripId,
            userId = userId,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
    }
    
    // TripDay Operations
    
    fun observeTripDays(tripId: String): Flow<List<TripDay>> =
        placeDao.observeTripDays(tripId)
    
    suspend fun getTripDays(tripId: String): List<TripDay> =
        placeDao.getTripDays(tripId)
    
    suspend fun getTripDayById(dayId: String): TripDay? =
        placeDao.getTripDayById(dayId)
    
    suspend fun updateTripDay(tripDay: TripDay) {
        placeDao.updateTripDay(tripDay.copy(updatedAt = System.currentTimeMillis()))
    }
    
    // Place Operations
    
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
        val maxOrder = placeDao.getMaxOrderIndex(place.tripDayId) ?: -1
        placeDao.insertPlace(place.copy(orderIndex = maxOrder + 1))
    }
    
    suspend fun updatePlace(place: Place) {
        placeDao.updatePlace(place.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deletePlace(placeId: String) {
        placeDao.getPlaceById(placeId)?.let { place ->
            logDeletion(place.tripId, RelayEntityType.PLACE, placeId)
        }
        placeDao.deletePlaceById(placeId)
    }
    
    suspend fun reorderPlaces(tripDayId: String, orderedPlaceIds: List<String>) {
        placeDao.reorderPlaces(tripDayId, orderedPlaceIds)
    }
    
    suspend fun markPlaceVisited(placeId: String, visited: Boolean) {
        placeDao.updatePlaceVisited(placeId, visited)
    }

    private suspend fun logDeletion(tripId: String, type: RelayEntityType, entityId: String) {
        val deviceId = userProfileRepository.getOrCreateUserId()
        deletionLogDao.insertDeletion(
            DeletionLog(
                tripId = tripId,
                entityType = type,
                entityId = entityId,
                deviceId = deviceId
            )
        )
    }
}
