package com.triloo.data.relay

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.local.dao.DeletionLogDao
import com.triloo.data.local.dao.ExpenseDao
import com.triloo.data.local.dao.PlaceDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Expense
import com.triloo.data.model.InvitePackage
import com.triloo.data.model.Participant
import com.triloo.data.model.Place
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.RelayMergeResult
import com.triloo.data.model.RelayPackage
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.user.UserProfileRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayRepository @Inject constructor(
    private val database: TrilooDatabase,
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val expenseDao: ExpenseDao,
    private val deletionLogDao: DeletionLogDao,
    private val userProfileRepository: UserProfileRepository
) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonSerializer<LocalDate> { value, _, _ ->
                com.google.gson.JsonPrimitive(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
        )
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonDeserializer { json, _, _ ->
                json?.asString?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
            }
        )
        .create()

    suspend fun buildPackage(tripId: String): RelayPackage? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val participants = tripDao.getParticipants(tripId)
        val tripDays = placeDao.getTripDays(tripId)
        val places = placeDao.getPlacesByTrip(tripId)
        val expenses = expenseDao.getExpensesByTrip(tripId)
        val expenseSplits = expenseDao.getSplitsForTrip(tripId)
        val deletions = deletionLogDao.getDeletionsForTrip(tripId)
        val deviceId = userProfileRepository.getOrCreateUserId()

        return RelayPackage(
            createdAt = System.currentTimeMillis(),
            deviceId = deviceId,
            trip = trip,
            participants = participants,
            tripDays = tripDays,
            places = places,
            expenses = expenses,
            expenseSplits = expenseSplits,
            deletions = deletions
        )
    }

    suspend fun buildInvitePackage(tripId: String): InvitePackage? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val participants = tripDao.getParticipants(tripId)
        val tripDays = placeDao.getTripDays(tripId)
        val places = placeDao.getPlacesByTrip(tripId)
        val deviceId = userProfileRepository.getOrCreateUserId()

        return InvitePackage(
            createdAt = System.currentTimeMillis(),
            deviceId = deviceId,
            trip = trip,
            participants = participants,
            tripDays = tripDays,
            places = places
        )
    }

    suspend fun mergeInvitePackage(invitePackage: InvitePackage): RelayMergeResult {
        var inserted = 0
        var updated = 0

        database.withTransaction {
            val tripResult = upsertTrip(invitePackage.trip)
            inserted += tripResult.first
            updated += tripResult.second

            invitePackage.participants.forEach { participant ->
                val result = upsertParticipant(participant)
                inserted += result.first
                updated += result.second
            }

            invitePackage.tripDays.forEach { day ->
                val result = upsertTripDay(day)
                inserted += result.first
                updated += result.second
            }

            invitePackage.places.forEach { place ->
                val result = upsertPlace(place)
                inserted += result.first
                updated += result.second
            }
        }

        return RelayMergeResult(
            inserted = inserted,
            updated = updated,
            deleted = 0
        )
    }

    suspend fun mergePackage(relayPackage: RelayPackage): RelayMergeResult {
        var inserted = 0
        var updated = 0
        var deleted = 0

        database.withTransaction {
            val tripResult = upsertTrip(relayPackage.trip)
            inserted += tripResult.first
            updated += tripResult.second

            relayPackage.participants.forEach { participant ->
                val result = upsertParticipant(participant)
                inserted += result.first
                updated += result.second
            }

            relayPackage.tripDays.forEach { day ->
                val result = upsertTripDay(day)
                inserted += result.first
                updated += result.second
            }

            relayPackage.places.forEach { place ->
                val result = upsertPlace(place)
                inserted += result.first
                updated += result.second
            }

            val splitsByExpense = relayPackage.expenseSplits.groupBy { it.expenseId }
            relayPackage.expenses.forEach { expense ->
                val result = upsertExpense(expense, splitsByExpense[expense.id].orEmpty())
                inserted += result.first
                updated += result.second
            }

            if (relayPackage.deletions.isNotEmpty()) {
                deletionLogDao.insertDeletions(relayPackage.deletions)
            }

            relayPackage.deletions.forEach { deletion ->
                if (applyDeletion(deletion)) {
                    deleted += 1
                }
            }
        }

        return RelayMergeResult(
            inserted = inserted,
            updated = updated,
            deleted = deleted
        )
    }

    fun encodePackage(relayPackage: RelayPackage): String = gson.toJson(relayPackage)

    fun decodePackage(payload: String): RelayPackage =
        gson.fromJson(payload, RelayPackage::class.java)

    fun encodeInvite(invitePackage: InvitePackage): String = gson.toJson(invitePackage)

    fun decodeInvite(payload: String): InvitePackage =
        gson.fromJson(payload, InvitePackage::class.java)

    private suspend fun upsertTrip(trip: Trip): Pair<Int, Int> {
        val local = tripDao.getTripById(trip.id)
        return if (local == null) {
            tripDao.insertTrip(trip)
            1 to 0
        } else if (trip.updatedAt > local.updatedAt) {
            tripDao.insertTrip(trip)
            0 to 1
        } else {
            0 to 0
        }
    }

    private suspend fun upsertParticipant(participant: Participant): Pair<Int, Int> {
        val local = tripDao.getParticipant(participant.tripId, participant.userId)
        return if (local == null) {
            tripDao.insertParticipant(participant)
            1 to 0
        } else if (participant.updatedAt > local.updatedAt) {
            tripDao.insertParticipant(participant)
            0 to 1
        } else {
            0 to 0
        }
    }

    private suspend fun upsertTripDay(day: TripDay): Pair<Int, Int> {
        val local = placeDao.getTripDayById(day.id)
        return if (local == null) {
            placeDao.insertTripDay(day)
            1 to 0
        } else if (day.updatedAt > local.updatedAt) {
            placeDao.insertTripDay(day)
            0 to 1
        } else {
            0 to 0
        }
    }

    private suspend fun upsertPlace(place: Place): Pair<Int, Int> {
        val local = placeDao.getPlaceById(place.id)
        return if (local == null) {
            placeDao.insertPlace(place)
            1 to 0
        } else if (place.updatedAt > local.updatedAt) {
            placeDao.insertPlace(place)
            0 to 1
        } else {
            0 to 0
        }
    }

    private suspend fun upsertExpense(
        expense: Expense,
        splits: List<com.triloo.data.model.ExpenseSplit>
    ): Pair<Int, Int> {
        val local = expenseDao.getExpenseById(expense.id)
        return if (local == null) {
            expenseDao.insertExpense(expense)
            expenseDao.deleteSplitsForExpense(expense.id)
            if (splits.isNotEmpty()) {
                expenseDao.insertExpenseSplits(splits)
            }
            1 to 0
        } else if (expense.updatedAt > local.updatedAt) {
            expenseDao.insertExpense(expense)
            expenseDao.deleteSplitsForExpense(expense.id)
            if (splits.isNotEmpty()) {
                expenseDao.insertExpenseSplits(splits)
            }
            0 to 1
        } else {
            0 to 0
        }
    }

    private suspend fun applyDeletion(deletion: DeletionLog): Boolean {
        return when (deletion.entityType) {
            RelayEntityType.TRIP -> {
                val trip = tripDao.getTripById(deletion.entityId) ?: return false
                if (trip.updatedAt <= deletion.deletedAt) {
                    tripDao.deleteTripById(trip.id)
                    tripDao.deleteParticipantsByTrip(trip.id)
                    true
                } else {
                    false
                }
            }
            RelayEntityType.PARTICIPANT -> {
                val participant = tripDao.getParticipant(deletion.tripId, deletion.entityId) ?: return false
                if (participant.updatedAt <= deletion.deletedAt) {
                    tripDao.removeParticipant(deletion.tripId, deletion.entityId)
                    true
                } else {
                    false
                }
            }
            RelayEntityType.TRIP_DAY -> {
                val day = placeDao.getTripDayById(deletion.entityId) ?: return false
                if (day.updatedAt <= deletion.deletedAt) {
                    placeDao.deleteTripDay(day)
                    true
                } else {
                    false
                }
            }
            RelayEntityType.PLACE -> {
                val place = placeDao.getPlaceById(deletion.entityId) ?: return false
                if (place.updatedAt <= deletion.deletedAt) {
                    placeDao.deletePlaceById(place.id)
                    true
                } else {
                    false
                }
            }
            RelayEntityType.EXPENSE -> {
                val expense = expenseDao.getExpenseById(deletion.entityId) ?: return false
                if (expense.updatedAt <= deletion.deletedAt) {
                    expenseDao.deleteSplitsForExpense(expense.id)
                    expenseDao.deleteExpenseById(expense.id)
                    true
                } else {
                    false
                }
            }
        }
    }
}
