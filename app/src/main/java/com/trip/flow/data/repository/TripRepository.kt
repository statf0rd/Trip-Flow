package com.trip.flow.data.repository

import com.trip.flow.data.local.dao.PlaceDao
import com.trip.flow.data.local.dao.TripDao
import com.trip.flow.data.model.Participant
import com.trip.flow.data.model.Place
import com.trip.flow.data.model.Trip
import com.trip.flow.data.model.TripDay
import com.trip.flow.data.model.TripStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val placeDao: PlaceDao
) {
    
    // ══════════════════════════════════════════════════════════
    // Trip Operations
    // ══════════════════════════════════════════════════════════
    
    fun observeAllTrips(): Flow<List<Trip>> = tripDao.observeAllTrips()
    
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
        tripDao.deleteTripById(tripId)
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
    
    // ══════════════════════════════════════════════════════════
    // Participant Operations
    // ══════════════════════════════════════════════════════════
    
    fun observeParticipants(tripId: String): Flow<List<Participant>> =
        tripDao.observeParticipants(tripId)
    
    suspend fun getParticipants(tripId: String): List<Participant> =
        tripDao.getParticipants(tripId)
    
    suspend fun addParticipant(participant: Participant) {
        tripDao.insertParticipant(participant)
        // Mark trip as group trip
        tripDao.getTripById(participant.tripId)?.let { trip ->
            if (!trip.isGroupTrip) {
                tripDao.updateTrip(trip.copy(isGroupTrip = true))
            }
        }
    }
    
    suspend fun removeParticipant(tripId: String, userId: String) {
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
    
    // ══════════════════════════════════════════════════════════
    // TripDay Operations
    // ══════════════════════════════════════════════════════════
    
    fun observeTripDays(tripId: String): Flow<List<TripDay>> =
        placeDao.observeTripDays(tripId)
    
    suspend fun getTripDays(tripId: String): List<TripDay> =
        placeDao.getTripDays(tripId)
    
    suspend fun getTripDayById(dayId: String): TripDay? =
        placeDao.getTripDayById(dayId)
    
    suspend fun updateTripDay(tripDay: TripDay) {
        placeDao.updateTripDay(tripDay.copy(updatedAt = System.currentTimeMillis()))
    }
    
    // ══════════════════════════════════════════════════════════
    // Place Operations
    // ══════════════════════════════════════════════════════════
    
    fun observePlacesByDay(tripDayId: String): Flow<List<Place>> =
        placeDao.observePlacesByDay(tripDayId)
    
    fun observePlacesByTrip(tripId: String): Flow<List<Place>> =
        placeDao.observePlacesByTrip(tripId)
    
    suspend fun getPlacesByDay(tripDayId: String): List<Place> =
        placeDao.getPlacesByDay(tripDayId)
    
    suspend fun getPlacesByTrip(tripId: String): List<Place> =
        placeDao.getPlacesByTrip(tripId)
    
    suspend fun addPlace(place: Place) {
        val maxOrder = placeDao.getMaxOrderIndex(place.tripDayId) ?: -1
        placeDao.insertPlace(place.copy(orderIndex = maxOrder + 1))
    }
    
    suspend fun updatePlace(place: Place) {
        placeDao.updatePlace(place.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deletePlace(placeId: String) {
        placeDao.deletePlaceById(placeId)
    }
    
    suspend fun reorderPlaces(tripDayId: String, orderedPlaceIds: List<String>) {
        placeDao.reorderPlaces(tripDayId, orderedPlaceIds)
    }
    
    suspend fun markPlaceVisited(placeId: String, visited: Boolean) {
        placeDao.updatePlaceVisited(placeId, visited)
    }
}

