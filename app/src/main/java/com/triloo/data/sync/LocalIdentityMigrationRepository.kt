package com.triloo.data.sync

import androidx.room.withTransaction
import com.triloo.data.auth.User
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.local.dao.ExpenseDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseSplit
import com.triloo.data.model.Participant
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.SplitType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * После входа переносит локальные ссылки со стабильного device id на серверный user id.
 * Это нужно, чтобы ранее созданные групповые поездки корректно уходили в online sync.
 */
@Singleton
class LocalIdentityMigrationRepository @Inject constructor(
    private val database: TrilooDatabase,
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val onlineSyncRepository: OnlineSyncRepository
) {

    suspend fun migrateDeviceIdentity(deviceId: String, user: User): Set<String> {
        if (deviceId.isBlank() || deviceId == user.id) return emptySet()

        val changedTripIds = linkedSetOf<String>()
        database.withTransaction {
            val timestamp = System.currentTimeMillis()
            tripDao.getAllTrips().forEach { trip ->
                var tripChanged = false

                if (trip.ownerId == deviceId) {
                    tripDao.updateTrip(
                        trip.copy(
                            ownerId = user.id,
                            updatedAt = timestamp
                        )
                    )
                    tripChanged = true
                }

                val participants = tripDao.getParticipants(trip.id)
                val legacyParticipant = participants.firstOrNull { it.userId == deviceId }
                val authenticatedParticipant = participants.firstOrNull { it.userId == user.id }

                if (legacyParticipant != null) {
                    val mergedParticipant = mergeParticipant(
                        legacy = legacyParticipant,
                        current = authenticatedParticipant,
                        user = user,
                        timestamp = timestamp
                    )
                    tripDao.removeParticipant(trip.id, deviceId)
                    tripDao.insertParticipant(mergedParticipant)
                    tripChanged = true
                } else if (authenticatedParticipant != null) {
                    val normalizedDisplayName = resolveDisplayName(authenticatedParticipant.displayName, user)
                    val normalizedEmail = user.email.ifBlank { authenticatedParticipant.email }
                    if (
                        authenticatedParticipant.displayName != normalizedDisplayName ||
                        authenticatedParticipant.email != normalizedEmail
                    ) {
                        tripDao.updateParticipant(
                            authenticatedParticipant.copy(
                                displayName = normalizedDisplayName,
                                email = normalizedEmail,
                                updatedAt = timestamp
                            )
                        )
                        tripChanged = true
                    }
                }

                val expensesChanged = migrateExpensesForTrip(
                    tripId = trip.id,
                    deviceId = deviceId,
                    user = user,
                    timestamp = timestamp
                )

                if (tripChanged || expensesChanged) {
                    rebuildExpenseSplits(trip.id)
                    changedTripIds += trip.id
                }
            }
        }

        changedTripIds.forEach { tripId ->
            onlineSyncRepository.syncTripAsync(tripId)
        }
        return changedTripIds
    }

    private suspend fun migrateExpensesForTrip(
        tripId: String,
        deviceId: String,
        user: User,
        timestamp: Long
    ): Boolean {
        var changed = false
        expenseDao.getExpensesByTrip(tripId).forEach { expense ->
            val updatedExpense = expense.withMigratedIdentity(
                legacyUserId = deviceId,
                authenticatedUser = user,
                timestamp = timestamp
            )
            if (updatedExpense != expense) {
                expenseDao.updateExpense(updatedExpense)
                changed = true
            }
        }
        return changed
    }

    private suspend fun rebuildExpenseSplits(tripId: String) {
        val participants = tripDao.getParticipants(tripId)
        val participantNames = participants.associate { it.userId to it.displayName }
        val participantPairs = participants.map { it.userId to it.displayName }

        expenseDao.getExpensesByTrip(tripId).forEach { expense ->
            expenseDao.deleteSplitsForExpense(expense.id)
            val splits = when (expense.splitType) {
                SplitType.EQUAL -> createEqualSplits(expense, participantPairs)
                SplitType.EXACT -> createExactSplits(expense, participantNames)
                else -> emptyList()
            }
            if (splits.isNotEmpty()) {
                expenseDao.insertExpenseSplits(splits)
            }
        }
    }

    private fun createEqualSplits(
        expense: Expense,
        participants: List<Pair<String, String>>
    ): List<ExpenseSplit> {
        if (participants.isEmpty()) return emptyList()

        val shareAmount = expense.amount / participants.size
        val shareInBase = expense.amountInBaseCurrency / participants.size
        return participants.map { (userId, userName) ->
            ExpenseSplit(
                expenseId = expense.id,
                userId = userId,
                userName = userName,
                shareAmount = shareAmount,
                shareAmountInBaseCurrency = shareInBase,
                isPaid = userId == expense.paidByUserId
            )
        }
    }

    private fun createExactSplits(
        expense: Expense,
        participantNames: Map<String, String>
    ): List<ExpenseSplit> {
        return expense.splitAmounts.orEmpty().map { (userId, amount) ->
            ExpenseSplit(
                expenseId = expense.id,
                userId = userId,
                userName = participantNames[userId] ?: userId,
                shareAmount = amount,
                shareAmountInBaseCurrency = amount * expense.exchangeRate,
                isPaid = false
            )
        }
    }

    private fun mergeParticipant(
        legacy: Participant,
        current: Participant?,
        user: User,
        timestamp: Long
    ): Participant {
        val resolvedDisplayName = resolveDisplayName(
            currentName = current?.displayName ?: legacy.displayName,
            user = user
        )
        val resolvedEmail = user.email.ifBlank { current?.email ?: legacy.email }

        return if (current == null) {
            legacy.copy(
                userId = user.id,
                displayName = resolvedDisplayName,
                email = resolvedEmail,
                updatedAt = timestamp
            )
        } else {
            current.copy(
                displayName = resolvedDisplayName,
                email = resolvedEmail,
                role = strongestRole(current.role, legacy.role),
                shareLocation = current.shareLocation || legacy.shareLocation,
                lastLatitude = current.lastLatitude ?: legacy.lastLatitude,
                lastLongitude = current.lastLongitude ?: legacy.lastLongitude,
                lastLocationUpdate = maxOf(current.lastLocationUpdate ?: 0L, legacy.lastLocationUpdate ?: 0L)
                    .takeIf { it > 0L },
                joinedAt = minOf(current.joinedAt, legacy.joinedAt),
                isOnline = current.isOnline || legacy.isOnline,
                createdAt = minOf(current.createdAt, legacy.createdAt),
                updatedAt = timestamp
            )
        }
    }

    private fun resolveDisplayName(currentName: String, user: User): String {
        return user.displayName.ifBlank { currentName.ifBlank { user.email.substringBefore("@") } }
    }

    private fun strongestRole(first: ParticipantRole, second: ParticipantRole): ParticipantRole {
        return when {
            first == ParticipantRole.OWNER || second == ParticipantRole.OWNER -> ParticipantRole.OWNER
            first == ParticipantRole.ADMIN || second == ParticipantRole.ADMIN -> ParticipantRole.ADMIN
            else -> ParticipantRole.MEMBER
        }
    }

    private fun Expense.withMigratedIdentity(
        legacyUserId: String,
        authenticatedUser: User,
        timestamp: Long
    ): Expense {
        val updatedSplitAmounts = splitAmounts?.let { splits ->
            if (legacyUserId !in splits.keys) {
                splits
            } else {
                buildMap {
                    splits.forEach { (userId, amount) ->
                        put(
                            if (userId == legacyUserId) authenticatedUser.id else userId,
                            amount
                        )
                    }
                }
            }
        }

        val updatedPayerId = if (paidByUserId == legacyUserId) authenticatedUser.id else paidByUserId
        val updatedPayerName = if (paidByUserId == legacyUserId) {
            resolveDisplayName(paidByName, authenticatedUser)
        } else {
            paidByName
        }

        if (updatedPayerId == paidByUserId && updatedPayerName == paidByName && updatedSplitAmounts == splitAmounts) {
            return this
        }

        return copy(
            paidByUserId = updatedPayerId,
            paidByName = updatedPayerName,
            splitAmounts = updatedSplitAmounts,
            updatedAt = timestamp
        )
    }
}
