package com.triloo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.RelayEntityType

/**
 * DAO для журнала удалений, который нужен relay-модулю при слиянии офлайн-изменений.
 */
@Dao
interface DeletionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(log: DeletionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletions(logs: List<DeletionLog>)

    @Query("SELECT * FROM deletion_log WHERE tripId = :tripId")
    suspend fun getDeletionsForTrip(tripId: String): List<DeletionLog>

    @Query("""
        SELECT * FROM deletion_log 
        WHERE tripId = :tripId AND entityType = :entityType AND entityId = :entityId
        ORDER BY deletedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestDeletion(
        tripId: String,
        entityType: RelayEntityType,
        entityId: String
    ): DeletionLog?

    @Query("DELETE FROM deletion_log WHERE tripId = :tripId")
    suspend fun deleteDeletionsForTrip(tripId: String)
}
