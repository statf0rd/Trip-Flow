package com.trip.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.trip.flow.data.model.Participant
import com.trip.flow.data.model.Trip
import com.trip.flow.data.model.TripStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    
    // ══════════════════════════════════════════════════════════
    // Trip CRUD
    // ══════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip)
    
    @Update
    suspend fun updateTrip(trip: Trip)
    
    @Delete
    suspend fun deleteTrip(trip: Trip)
    
    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)
    
    // ══════════════════════════════════════════════════════════
    // Trip Queries
    // ══════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): Trip?
    
    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observeTripById(tripId: String): Flow<Trip?>
    
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun observeAllTrips(): Flow<List<Trip>>
    
    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startDate DESC")
    fun observeTripsByStatus(status: TripStatus): Flow<List<Trip>>
    
    @Query("""
        SELECT * FROM trips 
        WHERE startDate <= date('now') AND endDate >= date('now')
        ORDER BY startDate ASC
        LIMIT 1
    """)
    fun observeCurrentTrip(): Flow<Trip?>
    
    @Query("""
        SELECT * FROM trips 
        WHERE startDate > date('now')
        ORDER BY startDate ASC
    """)
    fun observeUpcomingTrips(): Flow<List<Trip>>
    
    @Query("""
        SELECT * FROM trips 
        WHERE endDate < date('now')
        ORDER BY endDate DESC
    """)
    fun observePastTrips(): Flow<List<Trip>>
    
    @Query("SELECT * FROM trips WHERE inviteCode = :code LIMIT 1")
    suspend fun getTripByInviteCode(code: String): Trip?
    
    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getTripCount(): Int
    
    // ══════════════════════════════════════════════════════════
    // Participant CRUD
    // ══════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: Participant)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<Participant>)
    
    @Update
    suspend fun updateParticipant(participant: Participant)
    
    @Delete
    suspend fun deleteParticipant(participant: Participant)
    
    @Query("DELETE FROM participants WHERE tripId = :tripId AND userId = :userId")
    suspend fun removeParticipant(tripId: String, userId: String)
    
    // ══════════════════════════════════════════════════════════
    // Participant Queries
    // ══════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM participants WHERE tripId = :tripId")
    fun observeParticipants(tripId: String): Flow<List<Participant>>
    
    @Query("SELECT * FROM participants WHERE tripId = :tripId")
    suspend fun getParticipants(tripId: String): List<Participant>
    
    @Query("SELECT * FROM participants WHERE tripId = :tripId AND userId = :userId")
    suspend fun getParticipant(tripId: String, userId: String): Participant?
    
    @Query("SELECT COUNT(*) FROM participants WHERE tripId = :tripId")
    suspend fun getParticipantCount(tripId: String): Int
    
    @Query("""
        UPDATE participants 
        SET lastLatitude = :latitude, lastLongitude = :longitude, lastLocationUpdate = :timestamp
        WHERE tripId = :tripId AND userId = :userId
    """)
    suspend fun updateParticipantLocation(
        tripId: String,
        userId: String,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    )
    
    @Query("""
        UPDATE participants 
        SET isOnline = :isOnline
        WHERE tripId = :tripId AND userId = :userId
    """)
    suspend fun updateParticipantOnlineStatus(tripId: String, userId: String, isOnline: Boolean)
}

