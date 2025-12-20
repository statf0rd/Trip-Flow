package com.triloo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RelayEntityType {
    TRIP,
    PARTICIPANT,
    TRIP_DAY,
    PLACE,
    EXPENSE
}

@Entity(tableName = "deletion_log")
data class DeletionLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val entityType: RelayEntityType,
    val entityId: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null
)

data class RelayPackage(
    val version: Int = 1,
    val packageId: String = UUID.randomUUID().toString(),
    val createdAt: Long,
    val deviceId: String,
    val trip: Trip,
    val participants: List<Participant>,
    val tripDays: List<TripDay>,
    val places: List<Place>,
    val expenses: List<Expense>,
    val expenseSplits: List<ExpenseSplit>,
    val deletions: List<DeletionLog>
)

data class InvitePackage(
    val version: Int = 1,
    val packageId: String = UUID.randomUUID().toString(),
    val createdAt: Long,
    val deviceId: String,
    val trip: Trip,
    val participants: List<Participant>,
    val tripDays: List<TripDay>,
    val places: List<Place>
)

data class RelayMergeResult(
    val inserted: Int,
    val updated: Int,
    val deleted: Int
)
