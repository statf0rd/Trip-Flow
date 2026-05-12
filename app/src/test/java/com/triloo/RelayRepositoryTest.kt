package com.triloo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Expense
import com.triloo.data.model.Participant
import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.RelayPackage
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.relay.RelayRepository
import com.triloo.data.user.UserProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayRepositoryTest {

    private lateinit var database: TrilooDatabase
    private lateinit var repository: RelayRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrilooDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RelayRepository(
            database = database,
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            expenseDao = database.expenseDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = UserProfileRepository(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun mergePackageInsertsTripDataAndAppliesDeletion() = runBlocking {
        val trip = sampleTrip()
        val day = sampleDay(trip.id)
        val obsoletePlace = samplePlace(
            id = "place-obsolete",
            tripId = trip.id,
            tripDayId = day.id,
            name = "Old place",
            updatedAt = 1_000
        )
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(day)
        database.placeDao().insertPlace(obsoletePlace)

        val remotePlace = samplePlace(
            id = "place-remote",
            tripId = trip.id,
            tripDayId = day.id,
            name = "New place",
            updatedAt = 4_000
        )
        val remoteExpense = Expense(
            id = "expense-remote",
            tripId = trip.id,
            description = "Tickets",
            amount = 40.0,
            currency = "EUR",
            amountInBaseCurrency = 40.0,
            exchangeRate = 1.0,
            exchangeRateDate = trip.startDate,
            paidByUserId = "owner",
            paidByName = "Owner",
            splitType = SplitType.PAYER_ONLY,
            date = trip.startDate
        )
        val relayPackage = RelayPackage(
            createdAt = 4_000,
            deviceId = "remote-device",
            trip = trip.copy(updatedAt = 4_000),
            participants = listOf(
                Participant(
                    tripId = trip.id,
                    userId = "owner",
                    displayName = "Owner",
                    isOnline = true,
                    updatedAt = 4_000
                )
            ),
            tripDays = listOf(day.copy(updatedAt = 4_000)),
            places = listOf(remotePlace),
            expenses = listOf(remoteExpense),
            expenseSplits = emptyList(),
            deletions = listOf(
                DeletionLog(
                    tripId = trip.id,
                    entityType = RelayEntityType.PLACE,
                    entityId = obsoletePlace.id,
                    deletedAt = 5_000,
                    deviceId = "remote-device"
                )
            )
        )

        val result = repository.mergePackage(relayPackage)

        assertEquals(1, result.deleted)
        assertNull(database.placeDao().getPlaceById(obsoletePlace.id))
        assertNotNull(database.placeDao().getPlaceById(remotePlace.id))
        assertNotNull(database.expenseDao().getExpenseById(remoteExpense.id))
    }

    @Test
    fun mergePackageKeepsNewerLocalEntity() = runBlocking {
        val trip = sampleTrip()
        val day = sampleDay(trip.id)
        val localPlace = samplePlace(
            id = "place-shared",
            tripId = trip.id,
            tripDayId = day.id,
            name = "Local newest",
            updatedAt = 10_000
        )
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(day)
        database.placeDao().insertPlace(localPlace)

        val remoteOlderPlace = localPlace.copy(
            name = "Remote stale",
            updatedAt = 4_000
        )
        val relayPackage = RelayPackage(
            createdAt = 4_000,
            deviceId = "remote-device",
            trip = trip.copy(updatedAt = 4_000),
            participants = emptyList(),
            tripDays = listOf(day.copy(updatedAt = 4_000)),
            places = listOf(remoteOlderPlace),
            expenses = emptyList(),
            expenseSplits = emptyList(),
            deletions = emptyList()
        )

        repository.mergePackage(relayPackage)

        val mergedPlace = database.placeDao().getPlaceById(localPlace.id)
        assertEquals("Local newest", mergedPlace?.name)
        assertEquals(10_000L, mergedPlace?.updatedAt)
    }

    @Test
    fun buildPackageSinceCursorIncludesOnlyRecentChanges() = runBlocking {
        val trip = sampleTrip().copy(updatedAt = 3_000)
        val dayOld = sampleDay(trip.id).copy(id = "day-old", updatedAt = 2_000)
        val dayNew = sampleDay(trip.id).copy(id = "day-new", dayNumber = 2, updatedAt = 8_000)
        val placeOld = samplePlace(
            id = "place-old",
            tripId = trip.id,
            tripDayId = dayOld.id,
            name = "Old place",
            updatedAt = 3_000
        )
        val placeNew = samplePlace(
            id = "place-new",
            tripId = trip.id,
            tripDayId = dayNew.id,
            name = "Fresh place",
            updatedAt = 9_000
        )
        val expenseOld = Expense(
            id = "expense-old",
            tripId = trip.id,
            description = "Old expense",
            amount = 10.0,
            currency = "EUR",
            amountInBaseCurrency = 10.0,
            exchangeRate = 1.0,
            exchangeRateDate = trip.startDate,
            paidByUserId = "owner",
            paidByName = "Owner",
            splitType = SplitType.EQUAL,
            date = trip.startDate,
            updatedAt = 4_000
        )
        val expenseNew = expenseOld.copy(
            id = "expense-new",
            description = "Fresh expense",
            updatedAt = 10_000
        )

        database.tripDao().insertTrip(trip)
        database.tripDao().insertParticipant(
            Participant(
                tripId = trip.id,
                userId = "owner",
                displayName = "Owner",
                updatedAt = 11_000
            )
        )
        database.placeDao().insertTripDays(listOf(dayOld, dayNew))
        database.placeDao().insertPlaces(listOf(placeOld, placeNew))
        database.expenseDao().insertExpenses(listOf(expenseOld, expenseNew))
        database.expenseDao().insertExpenseSplits(
            listOf(
                com.triloo.data.model.ExpenseSplit(
                    expenseId = expenseOld.id,
                    userId = "owner",
                    userName = "Owner",
                    shareAmount = 10.0,
                    shareAmountInBaseCurrency = 10.0
                ),
                com.triloo.data.model.ExpenseSplit(
                    expenseId = expenseNew.id,
                    userId = "owner",
                    userName = "Owner",
                    shareAmount = 10.0,
                    shareAmountInBaseCurrency = 10.0
                )
            )
        )
        database.deletionLogDao().insertDeletion(
            DeletionLog(
                tripId = trip.id,
                entityType = RelayEntityType.PLACE,
                entityId = "place-deleted",
                deletedAt = 12_000,
                deviceId = "device-a"
            )
        )

        val relayPackage = repository.buildPackage(trip.id, sinceCursor = 5_000)

        assertNotNull(relayPackage)
        assertTrue(relayPackage!!.isDelta)
        assertEquals(5_000L, relayPackage.baseCursor)
        assertEquals(listOf("owner"), relayPackage.participants.map { it.userId })
        assertEquals(listOf(dayNew.id), relayPackage.tripDays.map { it.id })
        assertEquals(listOf(placeNew.id), relayPackage.places.map { it.id })
        assertEquals(listOf(expenseNew.id), relayPackage.expenses.map { it.id })
        assertEquals(listOf(expenseNew.id), relayPackage.expenseSplits.map { it.expenseId })
        assertEquals(listOf("place-deleted"), relayPackage.deletions.map { it.entityId })
        assertEquals(12_000L, relayPackage.changeCursor)
        assertFalse(relayPackage.places.any { it.id == placeOld.id })
    }

    private fun sampleTrip(): Trip {
        return Trip(
            id = "trip-relay",
            name = "Rome",
            destination = "Italy",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(2),
            baseCurrency = "EUR",
            isGroupTrip = true,
            updatedAt = 2_000
        )
    }

    private fun sampleDay(tripId: String): TripDay {
        return TripDay(
            id = "day-relay",
            tripId = tripId,
            date = LocalDate.now(),
            dayNumber = 1,
            updatedAt = 2_000
        )
    }

    private fun samplePlace(
        id: String,
        tripId: String,
        tripDayId: String,
        name: String,
        updatedAt: Long
    ): Place {
        return Place(
            id = id,
            tripId = tripId,
            tripDayId = tripDayId,
            name = name,
            latitude = 41.9028,
            longitude = 12.4964,
            category = PlaceCategory.ATTRACTION,
            updatedAt = updatedAt
        )
    }
}
