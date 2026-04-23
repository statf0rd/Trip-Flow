package com.triloo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Типы сущностей, которые можно синхронизировать через Triloo Relay.
 */
enum class RelayEntityType {
    TRIP,
    PARTICIPANT,
    TRIP_DAY,
    PLACE,
    EXPENSE
}

/**
 * Запись об удалении сущности, чтобы синхронизация не "воскрешала" удалённые данные.
 */
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

/**
 * Пакет поездки для обмена изменениями между устройствами через Triloo Relay.
 * Может содержать как полный снимок поездки, так и только изменения после известного курсора.
 */
data class RelayPackage(
    val version: Int = 2,
    val packageId: String = UUID.randomUUID().toString(),
    val createdAt: Long,
    val deviceId: String,
    val isDelta: Boolean = false,
    val baseCursor: Long? = null,
    val changeCursor: Long = createdAt,
    val trip: Trip,
    val participants: List<Participant>,
    val tripDays: List<TripDay>,
    val places: List<Place>,
    val expenses: List<Expense>,
    val expenseSplits: List<ExpenseSplit>,
    val deletions: List<DeletionLog>
)

/**
 * Облегчённый пакет приглашения для присоединения к групповой поездке.
 */
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

/**
 * Краткая статистика применения входящего relay-пакета.
 */
data class RelayMergeResult(
    val inserted: Int,
    val updated: Int,
    val deleted: Int
)
