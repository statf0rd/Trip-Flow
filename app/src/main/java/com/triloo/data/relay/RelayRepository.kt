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

/**
 * Собирает, сериализует и сливает relay-пакеты для офлайн-синхронизации поездок.
 */
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

    suspend fun buildPackage(
        tripId: String,
        sinceCursor: Long? = null
    ): RelayPackage? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val isDelta = sinceCursor != null
        val participants = tripDao.getParticipants(tripId)
            .filterByCursor(sinceCursor) { it.updatedAt }
        val tripDays = placeDao.getTripDays(tripId)
            .filterByCursor(sinceCursor) { it.updatedAt }
        val places = placeDao.getPlacesByTrip(tripId)
            .filterByCursor(sinceCursor) { it.updatedAt }
        val expenses = expenseDao.getExpensesByTrip(tripId)
            .filterByCursor(sinceCursor) { it.updatedAt }
        val expenseSplits = expenseDao.getSplitsForTrip(tripId)
            .filter { split -> expenses.any { it.id == split.expenseId } }
        val deletions = deletionLogDao.getDeletionsForTrip(tripId)
            .filterByCursor(sinceCursor) { it.deletedAt }
        val deviceId = userProfileRepository.getOrCreateDeviceId()
        val changeCursor = maxOf(
            trip.updatedAt,
            participants.maxOfOrNull { it.updatedAt } ?: 0L,
            tripDays.maxOfOrNull { it.updatedAt } ?: 0L,
            places.maxOfOrNull { it.updatedAt } ?: 0L,
            expenses.maxOfOrNull { it.updatedAt } ?: 0L,
            deletions.maxOfOrNull { it.deletedAt } ?: 0L,
            sinceCursor ?: 0L
        )

        // Диагностика расхождения участников между хостом и гостем после BT
        // sync (хост: 1, гость: 2). Логируем, кого реально кладём в пакет —
        // чтобы сравнить с финальным набором на принимающей стороне.
        android.util.Log.d(
            TAG,
            "buildPackage: tripId=$tripId delta=$isDelta participants=${participants.size}" +
                " names=${participants.joinToString { "${it.displayName}/${it.role}" }}" +
                " days=${tripDays.size} places=${places.size} expenses=${expenses.size}" +
                " deletions=${deletions.size}"
        )

        return RelayPackage(
            createdAt = System.currentTimeMillis(),
            deviceId = deviceId,
            isDelta = isDelta,
            baseCursor = sinceCursor,
            changeCursor = changeCursor,
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
        val deviceId = userProfileRepository.getOrCreateDeviceId()

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
        android.util.Log.d(
            TAG,
            "mergePackage: tripId=${relayPackage.trip.id} delta=${relayPackage.isDelta}" +
                " incomingParticipants=${relayPackage.participants.size}" +
                " names=${relayPackage.participants.joinToString { "${it.displayName}/${it.role}" }}" +
                " days=${relayPackage.tripDays.size} places=${relayPackage.places.size}" +
                " expenses=${relayPackage.expenses.size} deletions=${relayPackage.deletions.size}"
        )

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

        val totalLocalParticipants = tripDao.getParticipants(relayPackage.trip.id).size
        android.util.Log.d(
            TAG,
            "mergePackage.done: tripId=${relayPackage.trip.id} inserted=$inserted updated=$updated" +
                " deleted=$deleted localParticipantsAfter=$totalLocalParticipants"
        )

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

    private inline fun <T> List<T>.filterByCursor(
        sinceCursor: Long?,
        updatedAt: (T) -> Long
    ): List<T> {
        if (sinceCursor == null) return this
        return filter { updatedAt(it) > sinceCursor }
    }

    private suspend fun upsertTrip(trip: Trip): Pair<Int, Int> {
        val local = tripDao.getTripById(trip.id)
        return if (local == null) {
            tripDao.insertTrip(trip)
            1 to 0
        } else if (trip.updatedAt > local.updatedAt) {
            tripDao.updateTrip(trip)
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
            tripDao.updateParticipant(participant)
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
            placeDao.updateTripDay(day)
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
            placeDao.updatePlace(place)
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
            expenseDao.updateExpense(expense)
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

    companion object {
        private const val TAG = "RelayRepo"
    }
}
