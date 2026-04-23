package com.triloo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.Place
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.remote.CurrencyApi
import com.triloo.data.remote.CurrencyRatesResponse
import com.triloo.data.remote.OnlineSyncApi
import com.triloo.data.remote.SyncPullResponse
import com.triloo.data.remote.SyncPushResponse
import com.triloo.data.relay.RelayRepository
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.settings.AppSettingsRepository
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private lateinit var database: TrilooDatabase
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val userProfileRepository = UserProfileRepository(context)
        runBlocking {
            userProfileRepository.setAuthenticated(
                userId = "alice",
                displayName = "Alice",
                email = "alice@triloo.app"
            )
        }
        database = Room.inMemoryDatabaseBuilder(context, TrilooDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val relayRepository = RelayRepository(
            database = database,
            tripDao = database.tripDao(),
            placeDao = database.placeDao(),
            expenseDao = database.expenseDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = userProfileRepository
        )
        val onlineSyncRepository = OnlineSyncRepository(
            onlineSyncApi = StubOnlineSyncApi(),
            relayRepository = relayRepository,
            serverSessionRepository = ServerSessionRepository(context),
            appSettingsRepository = AppSettingsRepository(context),
            tripDao = database.tripDao()
        )
        repository = ExpenseRepository(
            expenseDao = database.expenseDao(),
            tripDao = database.tripDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = userProfileRepository,
            currencyApi = StubCurrencyApi(),
            onlineSyncRepository = onlineSyncRepository
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settledExpenseIsExcludedFromBalances() = runBlocking {
        val trip = sampleTrip()
        val day = sampleDay(trip.id, trip.startDate)
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(day)
        database.tripDao().insertParticipants(
            listOf(
                Participant(tripId = trip.id, userId = "alice", displayName = "Alice"),
                Participant(tripId = trip.id, userId = "bob", displayName = "Bob")
            )
        )

        val expense = Expense(
            tripId = trip.id,
            description = "Dinner",
            amount = 100.0,
            currency = trip.baseCurrency,
            amountInBaseCurrency = 100.0,
            exchangeRate = 1.0,
            exchangeRateDate = trip.startDate,
            category = ExpenseCategory.FOOD,
            paidByUserId = "alice",
            paidByName = "Alice",
            splitType = SplitType.EQUAL,
            date = trip.startDate,
            placeId = "place-1",
            placeName = "Cafe"
        )

        repository.addExpense(expense)

        val balancesBefore = repository.calculateBalances(trip.id)
        assertEquals(1, balancesBefore.size)
        assertEquals("bob", balancesBefore.first().fromUserId)
        assertEquals("alice", balancesBefore.first().toUserId)
        assertEquals(50.0, balancesBefore.first().amount, 0.01)

        repository.setExpenseSettled(expense.id, true)

        val balancesAfter = repository.calculateBalances(trip.id)
        assertTrue(balancesAfter.isEmpty())
    }

    private fun sampleTrip(): Trip {
        return Trip(
            id = "trip-expense",
            name = "Lisbon",
            destination = "Portugal",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(1),
            baseCurrency = "EUR",
            isGroupTrip = true
        )
    }

    private fun sampleDay(tripId: String, date: LocalDate): TripDay {
        return TripDay(
            id = "day-expense",
            tripId = tripId,
            date = date,
            dayNumber = 1
        )
    }

    private class StubCurrencyApi : CurrencyApi {
        override suspend fun latestRates(base: String): CurrencyRatesResponse {
            return CurrencyRatesResponse(result = "success", baseCode = base, rates = emptyMap())
        }
    }

    private class StubOnlineSyncApi : OnlineSyncApi {
        override suspend fun push(
            authorization: String,
            request: com.triloo.data.remote.SyncPushRequest
        ): SyncPushResponse {
            return SyncPushResponse()
        }

        override suspend fun pull(authorization: String, since: Long): SyncPullResponse {
            return SyncPullResponse()
        }
    }
}
