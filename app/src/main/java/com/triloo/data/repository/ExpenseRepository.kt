package com.triloo.data.repository

import com.triloo.data.local.dao.DeletionLogDao
import com.triloo.data.local.dao.ExpenseDao
import com.triloo.data.local.dao.TripDao
import com.triloo.data.model.Balance
import com.triloo.data.model.CurrencyRate
import com.triloo.data.model.DeletionLog
import com.triloo.data.model.Expense
import com.triloo.data.model.ExpenseSplit
import com.triloo.data.model.ExpenseSummary
import com.triloo.data.model.ParticipantRole
import com.triloo.data.model.RelayEntityType
import com.triloo.data.model.SplitType
import com.triloo.data.remote.CurrencyApi
import com.triloo.data.sync.OnlineSyncRepository
import com.triloo.data.user.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val tripDao: TripDao,
    private val deletionLogDao: DeletionLogDao,
    private val userProfileRepository: UserProfileRepository,
    private val currencyApi: CurrencyApi,
    private val onlineSyncRepository: OnlineSyncRepository
) {
    
    // CRUD-операции для расходов.
    
    fun observeExpensesByTrip(tripId: String): Flow<List<Expense>> =
        expenseDao.observeExpensesByTrip(tripId)
    
    fun observeTotalExpenses(tripId: String): Flow<Double?> =
        expenseDao.observeTotalExpenses(tripId)
    
    suspend fun getExpensesByTrip(tripId: String): List<Expense> =
        expenseDao.getExpensesByTrip(tripId)
    
    suspend fun getExpenseById(expenseId: String): Expense? =
        expenseDao.getExpenseById(expenseId)
    
    suspend fun addExpense(expense: Expense): String {
        requireExpensePermission(expense.tripId, "добавлять расходы")
        expenseDao.insertExpense(expense)

        val splits = buildSplits(expense)
        if (splits.isNotEmpty()) {
            expenseDao.insertExpenseSplits(splits)
        }
        onlineSyncRepository.syncTripAsync(expense.tripId)
        
        return expense.id
    }
    
    suspend fun updateExpense(expense: Expense) {
        requireExpensePermission(expense.tripId, "изменять расходы")
        expenseDao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
        expenseDao.deleteSplitsForExpense(expense.id)

        val splits = buildSplits(expense)
        if (splits.isNotEmpty()) {
            expenseDao.insertExpenseSplits(splits)
        }
        onlineSyncRepository.syncTripAsync(expense.tripId)
    }

    suspend fun setExpenseSettled(expenseId: String, isSettled: Boolean) {
        val expense = expenseDao.getExpenseById(expenseId) ?: return
        requireExpensePermission(expense.tripId, "закрывать долги")
        expenseDao.updateExpense(
            expense.copy(
                isSettled = isSettled,
                updatedAt = System.currentTimeMillis()
            )
        )
        onlineSyncRepository.syncTripAsync(expense.tripId)
    }
    
    suspend fun deleteExpense(expenseId: String) {
        var tripIdForRefresh: String? = null
        expenseDao.getExpenseById(expenseId)?.let { expense ->
            requireExpensePermission(expense.tripId, "удалять расходы")
            val deviceId = userProfileRepository.getOrCreateDeviceId()
            deletionLogDao.insertDeletion(
                DeletionLog(
                    tripId = expense.tripId,
                    entityType = RelayEntityType.EXPENSE,
                    entityId = expenseId,
                    deviceId = deviceId
                )
            )
            tripIdForRefresh = expense.tripId
        }
        expenseDao.deleteSplitsForExpense(expenseId)
        expenseDao.deleteExpenseById(expenseId)
        tripIdForRefresh?.let { onlineSyncRepository.syncTripAsync(it) }
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
    
    private fun createExactSplits(expense: Expense): List<ExpenseSplit> {
        return expense.splitAmounts?.map { (userId, amount) ->
            ExpenseSplit(
                expenseId = expense.id,
                userId = userId,
                userName = userId, // Имя будет подставлено позже.
                shareAmount = amount,
                shareAmountInBaseCurrency = amount * expense.exchangeRate,
                isPaid = false
            )
        } ?: emptyList()
    }

    private suspend fun buildSplits(expense: Expense): List<ExpenseSplit> {
        if (expense.splitType == SplitType.PAYER_ONLY) return emptyList()

        val participants = tripDao.getParticipants(expense.tripId)
        if (participants.isEmpty()) return emptyList()

        val participantPairs = participants.map { it.userId to it.displayName }
        return when (expense.splitType) {
            SplitType.EQUAL -> createEqualSplits(expense, participantPairs)
            SplitType.EXACT -> createExactSplits(expense)
            else -> emptyList()
        }
    }

    private suspend fun requireExpensePermission(tripId: String, action: String) {
        val trip = tripDao.getTripById(tripId) ?: return
        if (!trip.isGroupTrip) return

        val currentUserId = userProfileRepository.getProfile().userId
        val role = tripDao.getParticipant(tripId, currentUserId)?.role
            ?: throw IllegalStateException("Недостаточно прав: вы не участник поездки")
        if (role !in setOf(ParticipantRole.OWNER, ParticipantRole.ADMIN, ParticipantRole.MEMBER)) {
            throw IllegalStateException("Недостаточно прав, чтобы $action")
        }
    }
    
    // Расчёт балансов между участниками.
    
    suspend fun calculateBalances(tripId: String): List<Balance> {
        val expenses = expenseDao.getExpensesByTrip(tripId)
        val participants = tripDao.getParticipants(tripId)
        val trip = tripDao.getTripById(tripId) ?: return emptyList()
        
        // Считаем чистый баланс каждого участника.
        // Положительное значение = должен, отрицательное = должны ему.
        val netBalances = mutableMapOf<String, Double>()
        participants.forEach { netBalances[it.userId] = 0.0 }
        
        for (expense in expenses) {
            if (expense.splitType == SplitType.PAYER_ONLY || expense.isSettled) continue
            
            val splits = expenseDao.getSplitsForExpense(expense.id)
            
            // Плательщик внёс всю сумму расхода.
            netBalances[expense.paidByUserId] = 
                (netBalances[expense.paidByUserId] ?: 0.0) - expense.amountInBaseCurrency
            
            // Каждый участник должен свою долю.
            for (split in splits) {
                netBalances[split.userId] = 
                    (netBalances[split.userId] ?: 0.0) + split.shareAmountInBaseCurrency
            }
        }
        
        // Упрощаем долги жадным алгоритмом.
        return simplifyDebts(netBalances, participants.associate { it.userId to it.displayName }, trip.baseCurrency)
    }
    
    private fun simplifyDebts(
        netBalances: Map<String, Double>,
        userNames: Map<String, String>,
        currency: String
    ): List<Balance> {
        val balances = mutableListOf<Balance>()
        val threshold = BigDecimal("0.01")

        // Разделяем кредиторов (им должны) и должников (они должны).
        val creditors = netBalances
            .mapValues { BigDecimal.valueOf(it.value) }
            .filter { it.value < -threshold }
            .toMutableMap()
        val debtors = netBalances
            .mapValues { BigDecimal.valueOf(it.value) }
            .filter { it.value > threshold }
            .toMutableMap()

        // Жадно сопоставляем кредиторов и должников.
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtor = debtors.maxByOrNull { it.value } ?: break
            val creditor = creditors.minByOrNull { it.value } ?: break

            val amount = minOf(debtor.value, creditor.value.negate())

            if (amount > threshold) {
                balances.add(
                    Balance(
                        fromUserId = debtor.key,
                        fromUserName = userNames[debtor.key] ?: debtor.key,
                        toUserId = creditor.key,
                        toUserName = userNames[creditor.key] ?: creditor.key,
                        amount = amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        currency = currency
                    )
                )
            }

            // Обновляем остатки балансов после перевода.
            val newDebtorBalance = debtor.value - amount
            val newCreditorBalance = creditor.value + amount

            if (newDebtorBalance < threshold) {
                debtors.remove(debtor.key)
            } else {
                debtors[debtor.key] = newDebtorBalance
            }

            if (newCreditorBalance > -threshold) {
                creditors.remove(creditor.key)
            } else {
                creditors[creditor.key] = newCreditorBalance
            }
        }

        return balances
    }
    
    // Сводная статистика по расходам.
    
    suspend fun getExpenseSummary(tripId: String): ExpenseSummary? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val expenses = expenseDao.getExpensesByTrip(tripId)
        
        if (expenses.isEmpty()) {
            return ExpenseSummary(
                tripId = tripId,
                totalAmount = 0.0,
                currency = trip.baseCurrency,
                byCategory = emptyMap(),
                byPerson = emptyMap(),
                byDay = emptyMap(),
                averagePerDay = 0.0,
                averagePerPerson = 0.0
            )
        }
        
        val total = expenses.sumOf { it.amountInBaseCurrency }
        
        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, exps) -> exps.sumOf { it.amountInBaseCurrency } }
        
        val byPerson = expenses.groupBy { it.paidByUserId }
            .mapValues { (_, exps) -> exps.sumOf { it.amountInBaseCurrency } }
        
        val byDay = expenses.groupBy { it.date }
            .mapValues { (_, exps) -> exps.sumOf { it.amountInBaseCurrency } }
        
        val participants = tripDao.getParticipants(tripId)
        
        return ExpenseSummary(
            tripId = tripId,
            totalAmount = total,
            currency = trip.baseCurrency,
            byCategory = byCategory,
            byPerson = byPerson,
            byDay = byDay,
            averagePerDay = if (trip.durationDays > 0) total / trip.durationDays else total,
            averagePerPerson = if (participants.isNotEmpty()) total / participants.size else total
        )
    }
    
    // Работа с валютными курсами.
    
    suspend fun getCurrencyRate(from: String, to: String, date: LocalDate): Double? {
        val fromCode = from.uppercase()
        val toCode = to.uppercase()
        if (fromCode == toCode) return 1.0
        return expenseDao.getCurrencyRate(fromCode, toCode, date)?.rate
            ?: expenseDao.getLatestCurrencyRate(fromCode, toCode)?.rate
    }
    
    suspend fun saveCurrencyRate(from: String, to: String, rate: Double, date: LocalDate) {
        expenseDao.insertCurrencyRate(
            CurrencyRate(
                id = "${from}_${to}_$date",
                fromCurrency = from,
                toCurrency = to,
                rate = rate,
                date = date
            )
        )
    }

    suspend fun getOrFetchCurrencyRate(
        from: String,
        to: String,
        date: LocalDate
    ): Double? {
        val fromCode = from.uppercase()
        val toCode = to.uppercase()
        if (fromCode == toCode) return 1.0

        val cached = expenseDao.getCurrencyRate(fromCode, toCode, date)
            ?: expenseDao.getLatestCurrencyRate(fromCode, toCode)
        if (cached != null && !isRateStale(cached)) {
            return cached.rate
        }

        val fetched = fetchLatestRate(fromCode, toCode) ?: return cached?.rate
        saveCurrencyRate(fromCode, toCode, fetched, date)
        return fetched
    }

    private fun isRateStale(rate: CurrencyRate): Boolean {
        val now = System.currentTimeMillis()
        return abs(now - rate.fetchedAt) > RATE_MAX_AGE_MS
    }

    private suspend fun fetchLatestRate(from: String, to: String): Double? {
        val response = runCatching { currencyApi.latestRates(from) }.getOrNull() ?: return null
        if (response.result != "success") return null
        return response.rates[to]
    }

    companion object {
        private const val RATE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
