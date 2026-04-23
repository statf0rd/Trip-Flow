package com.triloo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.triloo.data.model.Place
import com.triloo.data.model.TripDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PlaceDao {
    
    // CRUD-операции для дней поездки.
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripDay(tripDay: TripDay)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripDays(tripDays: List<TripDay>)
    
    @Update
    suspend fun updateTripDay(tripDay: TripDay)
    
    @Delete
    suspend fun deleteTripDay(tripDay: TripDay)
    
    @Query("DELETE FROM trip_days WHERE tripId = :tripId")
    suspend fun deleteAllTripDays(tripId: String)
    
    // Запросы по дням поездки.
    
    @Query("SELECT * FROM trip_days WHERE id = :dayId")
    suspend fun getTripDayById(dayId: String): TripDay?
    
    @Query("SELECT * FROM trip_days WHERE tripId = :tripId ORDER BY dayNumber ASC")
    fun observeTripDays(tripId: String): Flow<List<TripDay>>
    
    @Query("SELECT * FROM trip_days WHERE tripId = :tripId ORDER BY dayNumber ASC")
    suspend fun getTripDays(tripId: String): List<TripDay>
    
    @Query("SELECT * FROM trip_days WHERE tripId = :tripId AND date = :date")
    suspend fun getTripDayByDate(tripId: String, date: LocalDate): TripDay?
    
    @Query("SELECT COUNT(*) FROM trip_days WHERE tripId = :tripId")
    suspend fun getTripDayCount(tripId: String): Int
    
    // CRUD-операции для мест.
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: Place)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<Place>)
    
    @Update
    suspend fun updatePlace(place: Place)
    
    @Delete
    suspend fun deletePlace(place: Place)
    
    @Query("DELETE FROM places WHERE id = :placeId")
    suspend fun deletePlaceById(placeId: String)
    
    @Query("DELETE FROM places WHERE tripDayId = :tripDayId")
    suspend fun deletePlacesByDayId(tripDayId: String)
    
    // Запросы по местам.
    
    @Query("SELECT * FROM places WHERE id = :placeId")
    suspend fun getPlaceById(placeId: String): Place?
    
    @Query("SELECT * FROM places WHERE tripDayId = :tripDayId ORDER BY orderIndex ASC")
    fun observePlacesByDay(tripDayId: String): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE tripDayId = :tripDayId ORDER BY orderIndex ASC")
    suspend fun getPlacesByDay(tripDayId: String): List<Place>
    
    @Query("SELECT * FROM places WHERE tripId = :tripId ORDER BY tripDayId, orderIndex ASC")
    fun observePlacesByTrip(tripId: String): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE tripId = :tripId ORDER BY tripDayId, orderIndex ASC")
    suspend fun getPlacesByTrip(tripId: String): List<Place>
    
    @Query("SELECT COUNT(*) FROM places WHERE tripId = :tripId")
    suspend fun getPlaceCountForTrip(tripId: String): Int
    
    @Query("SELECT COUNT(*) FROM places WHERE tripDayId = :tripDayId")
    suspend fun getPlaceCountForDay(tripDayId: String): Int
    
    @Query("SELECT MAX(orderIndex) FROM places WHERE tripDayId = :tripDayId")
    suspend fun getMaxOrderIndex(tripDayId: String): Int?
    
    @Query("UPDATE places SET orderIndex = :newIndex WHERE id = :placeId")
    suspend fun updatePlaceOrder(placeId: String, newIndex: Int)
    
    @Query("UPDATE places SET isVisited = :isVisited WHERE id = :placeId")
    suspend fun updatePlaceVisited(placeId: String, isVisited: Boolean)
    
    @Transaction
    suspend fun reorderPlaces(tripDayId: String, orderedPlaceIds: List<String>) {
        orderedPlaceIds.forEachIndexed { index, placeId ->
            updatePlaceOrder(placeId, index)
        }
    }
}

