package com.triloo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.triloo.data.auth.ServerSessionRepository
import com.triloo.data.local.TrilooDatabase
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseCategory
import com.triloo.data.model.Participant
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.SplitType
import com.triloo.data.model.Trip
import com.triloo.data.model.TripDay
import com.triloo.data.relay.RelayRepository
import com.triloo.data.remote.CurrencyApi
import com.triloo.data.remote.CurrencyRatesResponse
import com.triloo.data.repository.ExpenseRepository
import com.triloo.data.settings.AppSettingsRepository
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private lateinit var database: TrilooDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var currencyApi: StubCurrencyApi

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
            backendTripApi = StubBackendTripApi(),
            relayRepository = relayRepository,
            serverSessionRepository = ServerSessionRepository(context),
            appSettingsRepository = AppSettingsRepository(context),
            tripDao = database.tripDao()
        )
        currencyApi = StubCurrencyApi()
        repository = ExpenseRepository(
            expenseDao = database.expenseDao(),
            tripDao = database.tripDao(),
            deletionLogDao = database.deletionLogDao(),
            userProfileRepository = userProfileRepository,
            currencyApi = currencyApi,
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

    @Test
    fun debtsSimplifiedToMinimalTransfers() = runBlocking {
        val trip = sampleTrip()
        val day = sampleDay(trip.id, trip.startDate)
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(day)
        database.tripDao().insertParticipants(
            listOf(
                Participant(tripId = trip.id, userId = "alice", displayName = "Alice"),
                Participant(tripId = trip.id, userId = "bob", displayName = "Bob"),
                Participant(tripId = trip.id, userId = "charlie", displayName = "Charlie")
            )
        )

        repository.addExpense(
            Expense(
                tripId = trip.id,
                description = "Музей",
                amount = 120.0,
                currency = trip.baseCurrency,
                amountInBaseCurrency = 120.0,
                exchangeRate = 1.0,
                exchangeRateDate = trip.startDate,
                category = ExpenseCategory.FOOD,
                paidByUserId = "alice",
                paidByName = "Alice",
                splitType = SplitType.EQUAL,
                date = trip.startDate
            )
        )
        repository.addExpense(
            Expense(
                tripId = trip.id,
                description = "Такси",
                amount = 30.0,
                currency = trip.baseCurrency,
                amountInBaseCurrency = 30.0,
                exchangeRate = 1.0,
                exchangeRateDate = trip.startDate,
                category = ExpenseCategory.FOOD,
                paidByUserId = "bob",
                paidByName = "Bob",
                splitType = SplitType.EQUAL,
                date = trip.startDate
            )
        )

        val balances = repository.calculateBalances(trip.id)

        // Нетто-балансы: Alice кредитор 70, Charlie должен 50, Bob должен 20.
        // Минимальный набор переводов: Charlie -> Alice 50, Bob -> Alice 20.
        assertEquals(2, balances.size)
        assertTrue(balances.all { it.toUserId == "alice" })
        val byFrom = balances.associate { it.fromUserId to it.amount }
        assertEquals(50.0, byFrom["charlie"] ?: 0.0, 0.01)
        assertEquals(20.0, byFrom["bob"] ?: 0.0, 0.01)
    }

    @Test
    fun addExpenseStoresExpenseAndEqualSplits() = runBlocking {
        val trip = insertGroupTrip()
        val expense = sampleExpense(trip.id, amount = 100.0)

        repository.addExpense(expense)

        assertNotNull(repository.getExpenseById(expense.id))
        assertEquals(1, repository.getExpensesByTrip(trip.id).size)
        val splits = database.expenseDao().getSplitsForExpense(expense.id)
        assertEquals(2, splits.size)
        assertTrue(splits.all { it.shareAmount == 50.0 })
    }

    @Test
    fun updateExpenseRebuildsSplits() = runBlocking {
        val trip = insertGroupTrip()
        val expense = sampleExpense(trip.id, amount = 100.0)
        repository.addExpense(expense)

        repository.updateExpense(expense.copy(amount = 60.0, amountInBaseCurrency = 60.0))

        val splits = database.expenseDao().getSplitsForExpense(expense.id)
        assertEquals(2, splits.size)
        assertTrue(splits.all { it.shareAmount == 30.0 })
    }

    @Test
    fun deleteExpenseRemovesSplitsAndLogsDeletion() = runBlocking {
        val trip = insertGroupTrip()
        val expense = sampleExpense(trip.id)
        repository.addExpense(expense)

        repository.deleteExpense(expense.id)

        assertNull(repository.getExpenseById(expense.id))
        assertTrue(database.expenseDao().getSplitsForExpense(expense.id).isEmpty())
        val deletions = database.deletionLogDao().getDeletionsForTrip(trip.id)
        assertTrue(deletions.any { it.entityId == expense.id && it.entityType == RelayEntityType.EXPENSE })
    }

    @Test
    fun exactSplitUsesProvidedAmounts() = runBlocking {
        val trip = insertGroupTrip()
        val expense = sampleExpense(
            trip.id,
            amount = 100.0,
            splitType = SplitType.EXACT,
            splitAmounts = mapOf("alice" to 70.0, "bob" to 30.0)
        )

        repository.addExpense(expense)

        val splits = database.expenseDao().getSplitsForExpense(expense.id)
            .associate { it.userId to it.shareAmount }
        assertEquals(70.0, splits["alice"] ?: 0.0, 0.001)
        assertEquals(30.0, splits["bob"] ?: 0.0, 0.001)
    }

    @Test
    fun payerOnlyExpenseHasNoSplits() = runBlocking {
        val trip = insertGroupTrip()
        val expense = sampleExpense(trip.id, splitType = SplitType.PAYER_ONLY)

        repository.addExpense(expense)

        assertTrue(database.expenseDao().getSplitsForExpense(expense.id).isEmpty())
    }

    @Test
    fun expenseSummaryAggregatesTotalsCategoriesAndPeople() = runBlocking {
        val trip = insertGroupTrip()
        repository.addExpense(
            sampleExpense(trip.id, amount = 100.0, paidBy = "alice" to "Alice", category = ExpenseCategory.FOOD)
        )
        repository.addExpense(
            sampleExpense(trip.id, amount = 50.0, paidBy = "bob" to "Bob", category = ExpenseCategory.TRANSPORT)
        )

        val summary = repository.getExpenseSummary(trip.id)!!
        assertEquals(150.0, summary.totalAmount, 0.001)
        assertEquals(100.0, summary.byCategory[ExpenseCategory.FOOD] ?: 0.0, 0.001)
        assertEquals(50.0, summary.byCategory[ExpenseCategory.TRANSPORT] ?: 0.0, 0.001)
        assertEquals(100.0, summary.byPerson["alice"] ?: 0.0, 0.001)
        assertEquals(50.0, summary.byPerson["bob"] ?: 0.0, 0.001)
        assertEquals(75.0, summary.averagePerPerson, 0.001)
    }

    @Test
    fun expenseSummaryEmptyTripReturnsZeroes() = runBlocking {
        val trip = insertGroupTrip()
        val summary = repository.getExpenseSummary(trip.id)!!
        assertEquals(0.0, summary.totalAmount, 0.001)
        assertTrue(summary.byCategory.isEmpty())
    }

    @Test
    fun sameCurrencyRateIsOne() = runBlocking {
        assertEquals(1.0, repository.getOrFetchCurrencyRate("EUR", "EUR", LocalDate.now()) ?: 0.0, 0.0)
        assertEquals(1.0, repository.getCurrencyRate("usd", "USD", LocalDate.now()) ?: 0.0, 0.0)
    }

    @Test
    fun getOrFetchCurrencyRateFetchesThenServesFromCache() = runBlocking {
        val date = LocalDate.now()
        currencyApi.result = "success"
        currencyApi.rates = mapOf("USD" to 1.1)

        val first = repository.getOrFetchCurrencyRate("EUR", "USD", date)
        assertEquals(1.1, first ?: 0.0, 0.0001)

        // Меняем ответ API — повторный вызов должен вернуть закэшированное (без сети).
        currencyApi.rates = mapOf("USD" to 9.9)
        val second = repository.getOrFetchCurrencyRate("EUR", "USD", date)
        assertEquals(1.1, second ?: 0.0, 0.0001)
    }

    @Test
    fun getOrFetchCurrencyRateReturnsNullWhenApiFailsAndNoCache() = runBlocking {
        currencyApi.result = "error"
        currencyApi.rates = emptyMap()
        assertNull(repository.getOrFetchCurrencyRate("EUR", "GBP", LocalDate.now()))
    }

    @Test
    fun savedCurrencyRateIsReturnedDirectlyAndAsLatest() = runBlocking {
        val date = LocalDate.now()
        repository.saveCurrencyRate("EUR", "JPY", 160.0, date)

        assertEquals(160.0, repository.getCurrencyRate("EUR", "JPY", date) ?: 0.0, 0.0001)
        // Точного курса на другую дату нет — берётся последний известный.
        assertEquals(160.0, repository.getCurrencyRate("EUR", "JPY", date.plusDays(3)) ?: 0.0, 0.0001)
    }

    @Test
    fun observeExpensesAndTotalEmitCurrentData() = runBlocking {
        val trip = insertGroupTrip()
        repository.addExpense(sampleExpense(trip.id, amount = 100.0))
        repository.addExpense(sampleExpense(trip.id, amount = 50.0))

        assertEquals(2, repository.observeExpensesByTrip(trip.id).first().size)
        assertEquals(150.0, repository.observeTotalExpenses(trip.id).first() ?: 0.0, 0.001)
    }

    @Test
    fun addExpenseDeniedForNonParticipant() {
        val trip = sampleTrip("trip-noaccess")
        runBlocking {
            database.tripDao().insertTrip(trip)
            database.tripDao().insertParticipant(
                Participant(tripId = trip.id, userId = "bob", displayName = "Bob")
            )
        }
        val expense = sampleExpense(trip.id)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.addExpense(expense) }
        }
    }

    private fun sampleTrip(id: String = "trip-expense"): Trip {
        return Trip(
            id = id,
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

    private fun sampleExpense(
        tripId: String,
        amount: Double = 100.0,
        paidBy: Pair<String, String> = "alice" to "Alice",
        category: ExpenseCategory = ExpenseCategory.FOOD,
        splitType: SplitType = SplitType.EQUAL,
        splitAmounts: Map<String, Double>? = null,
        date: LocalDate = LocalDate.now()
    ): Expense = Expense(
        tripId = tripId,
        description = "expense",
        amount = amount,
        currency = "EUR",
        amountInBaseCurrency = amount,
        exchangeRate = 1.0,
        exchangeRateDate = date,
        category = category,
        paidByUserId = paidBy.first,
        paidByName = paidBy.second,
        splitType = splitType,
        splitAmounts = splitAmounts,
        date = date
    )

    private suspend fun insertGroupTrip(
        id: String = "trip-expense",
        participants: List<Pair<String, String>> = listOf("alice" to "Alice", "bob" to "Bob")
    ): Trip {
        val trip = sampleTrip(id)
        database.tripDao().insertTrip(trip)
        database.placeDao().insertTripDay(sampleDay(trip.id, trip.startDate))
        database.tripDao().insertParticipants(
            participants.map { (uid, name) -> Participant(tripId = trip.id, userId = uid, displayName = name) }
        )
        return trip
    }

    private class StubCurrencyApi : CurrencyApi {
        var result: String = "success"
        var rates: Map<String, Double> = emptyMap()

        override suspend fun latestRates(base: String): CurrencyRatesResponse {
            return CurrencyRatesResponse(result = result, baseCode = base, rates = rates)
        }
    }
}
