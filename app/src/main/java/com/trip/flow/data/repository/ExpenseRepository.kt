package com.trip.flow.data.repository

import com.trip.flow.data.local.dao.ExpenseDao
import com.trip.flow.data.local.dao.TripDao
import com.trip.flow.data.model.Balance
import com.trip.flow.data.model.CurrencyRate
import com.trip.flow.data.model.Expense
import com.trip.flow.data.model.ExpenseCategory
import com.trip.flow.data.model.ExpenseSplit
import com.trip.flow.data.model.ExpenseSummary
import com.trip.flow.data.model.SplitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val tripDao: TripDao
) {
    
    // ══════════════════════════════════════════════════════════
    // Expense CRUD
    // ══════════════════════════════════════════════════════════
    
    fun observeExpensesByTrip(tripId: String): Flow<List<Expense>> =
        expenseDao.observeExpensesByTrip(tripId)
    
    fun observeTotalExpenses(tripId: String): Flow<Double?> =
        expenseDao.observeTotalExpenses(tripId)
    
    suspend fun getExpensesByTrip(tripId: String): List<Expense> =
        expenseDao.getExpensesByTrip(tripId)
    
    suspend fun getExpenseById(expenseId: String): Expense? =
        expenseDao.getExpenseById(expenseId)
    
    suspend fun addExpense(expense: Expense): String {
        expenseDao.insertExpense(expense)
        
        // Create splits based on split type
        if (expense.splitType != SplitType.PAYER_ONLY) {
            val participants = tripDao.getParticipants(expense.tripId)
            if (participants.isNotEmpty()) {
                val splits = when (expense.splitType) {
                    SplitType.EQUAL -> createEqualSplits(expense, participants.map { it.userId to it.displayName })
                    SplitType.EXACT -> createExactSplits(expense)
                    else -> emptyList()
                }
                expenseDao.insertExpenseSplits(splits)
            }
        }
        
        return expense.id
    }
    
    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
        // Recalculate splits
        expenseDao.deleteSplitsForExpense(expense.id)
        if (expense.splitType != SplitType.PAYER_ONLY) {
            val participants = tripDao.getParticipants(expense.tripId)
            if (participants.isNotEmpty()) {
                val splits = createEqualSplits(expense, participants.map { it.userId to it.displayName })
                expenseDao.insertExpenseSplits(splits)
            }
        }
    }
    
    suspend fun deleteExpense(expenseId: String) {
        expenseDao.deleteSplitsForExpense(expenseId)
        expenseDao.deleteExpenseById(expenseId)
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
                userName = userId, // Will be resolved later
                shareAmount = amount,
                shareAmountInBaseCurrency = amount * expense.exchangeRate,
                isPaid = false
            )
        } ?: emptyList()
    }
    
    // ══════════════════════════════════════════════════════════
    // Balance Calculations
    // ══════════════════════════════════════════════════════════
    
    suspend fun calculateBalances(tripId: String): List<Balance> {
        val expenses = expenseDao.getExpensesByTrip(tripId)
        val participants = tripDao.getParticipants(tripId)
        val trip = tripDao.getTripById(tripId) ?: return emptyList()
        
        // Calculate net balance for each participant
        // Positive = owes money, Negative = is owed money
        val netBalances = mutableMapOf<String, Double>()
        participants.forEach { netBalances[it.userId] = 0.0 }
        
        for (expense in expenses) {
            if (expense.splitType == SplitType.PAYER_ONLY) continue
            
            val splits = expenseDao.getSplitsForExpense(expense.id)
            
            // Payer paid the full amount
            netBalances[expense.paidByUserId] = 
                (netBalances[expense.paidByUserId] ?: 0.0) - expense.amountInBaseCurrency
            
            // Each person owes their share
            for (split in splits) {
                netBalances[split.userId] = 
                    (netBalances[split.userId] ?: 0.0) + split.shareAmountInBaseCurrency
            }
        }
        
        // Simplify debts using greedy algorithm
        return simplifyDebts(netBalances, participants.associate { it.userId to it.displayName }, trip.baseCurrency)
    }
    
    private fun simplifyDebts(
        netBalances: Map<String, Double>,
        userNames: Map<String, String>,
        currency: String
    ): List<Balance> {
        val balances = mutableListOf<Balance>()
        
        // Separate creditors (negative balance = owed money) and debtors (positive = owes money)
        val creditors = netBalances.filter { it.value < -0.01 }.toMutableMap()
        val debtors = netBalances.filter { it.value > 0.01 }.toMutableMap()
        
        // Greedy matching
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtor = debtors.maxByOrNull { it.value } ?: break
            val creditor = creditors.minByOrNull { it.value } ?: break
            
            val amount = minOf(debtor.value, -creditor.value)
            
            if (amount > 0.01) {
                balances.add(
                    Balance(
                        fromUserId = debtor.key,
                        fromUserName = userNames[debtor.key] ?: debtor.key,
                        toUserId = creditor.key,
                        toUserName = userNames[creditor.key] ?: creditor.key,
                        amount = kotlin.math.round(amount * 100) / 100,
                        currency = currency
                    )
                )
            }
            
            // Update balances
            val newDebtorBalance = debtor.value - amount
            val newCreditorBalance = creditor.value + amount
            
            if (newDebtorBalance < 0.01) {
                debtors.remove(debtor.key)
            } else {
                debtors[debtor.key] = newDebtorBalance
            }
            
            if (newCreditorBalance > -0.01) {
                creditors.remove(creditor.key)
            } else {
                creditors[creditor.key] = newCreditorBalance
            }
        }
        
        return balances
    }
    
    // ══════════════════════════════════════════════════════════
    // Expense Summary
    // ══════════════════════════════════════════════════════════
    
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
    
    // ══════════════════════════════════════════════════════════
    // Currency Rates
    // ══════════════════════════════════════════════════════════
    
    suspend fun getCurrencyRate(from: String, to: String, date: LocalDate): Double? {
        if (from == to) return 1.0
        return expenseDao.getCurrencyRate(from, to, date)?.rate
            ?: expenseDao.getLatestCurrencyRate(from, to)?.rate
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
}
