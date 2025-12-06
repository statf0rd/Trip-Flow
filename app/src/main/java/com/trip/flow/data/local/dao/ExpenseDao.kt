package com.trip.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trip.flow.data.model.CurrencyRate
import com.trip.flow.data.model.Expense
import com.trip.flow.data.model.ExpenseCategory
import com.trip.flow.data.model.ExpenseSplit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    
    // ══════════════════════════════════════════════════════════
    // Expense CRUD
    // ══════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)
    
    @Update
    suspend fun updateExpense(expense: Expense)
    
    @Delete
    suspend fun deleteExpense(expense: Expense)
    
    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)
    
    @Query("DELETE FROM expenses WHERE tripId = :tripId")
    suspend fun deleteAllExpenses(tripId: String)
    
    // ══════════════════════════════════════════════════════════
    // Expense Queries
    // ══════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): Expense?
    
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, createdAt DESC")
    fun observeExpensesByTrip(tripId: String): Flow<List<Expense>>
    
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, createdAt DESC")
    suspend fun getExpensesByTrip(tripId: String): List<Expense>
    
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND date = :date ORDER BY createdAt DESC")
    fun observeExpensesByDate(tripId: String, date: LocalDate): Flow<List<Expense>>
    
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND category = :category ORDER BY date DESC")
    fun observeExpensesByCategory(tripId: String, category: ExpenseCategory): Flow<List<Expense>>
    
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND paidByUserId = :userId ORDER BY date DESC")
    fun observeExpensesByPayer(tripId: String, userId: String): Flow<List<Expense>>
    
    // ══════════════════════════════════════════════════════════
    // Aggregations
    // ══════════════════════════════════════════════════════════
    
    @Query("SELECT SUM(amountInBaseCurrency) FROM expenses WHERE tripId = :tripId")
    fun observeTotalExpenses(tripId: String): Flow<Double?>
    
    @Query("SELECT SUM(amountInBaseCurrency) FROM expenses WHERE tripId = :tripId")
    suspend fun getTotalExpenses(tripId: String): Double?
    
    @Query("SELECT SUM(amountInBaseCurrency) FROM expenses WHERE tripId = :tripId AND paidByUserId = :userId")
    suspend fun getTotalExpensesByUser(tripId: String, userId: String): Double?
    
    @Query("SELECT SUM(amountInBaseCurrency) FROM expenses WHERE tripId = :tripId AND category = :category")
    suspend fun getTotalExpensesByCategory(tripId: String, category: ExpenseCategory): Double?
    
    @Query("SELECT SUM(amountInBaseCurrency) FROM expenses WHERE tripId = :tripId AND date = :date")
    suspend fun getTotalExpensesByDate(tripId: String, date: LocalDate): Double?
    
    @Query("SELECT COUNT(*) FROM expenses WHERE tripId = :tripId")
    suspend fun getExpenseCount(tripId: String): Int
    
    @Query("""
        SELECT category, SUM(amountInBaseCurrency) as total
        FROM expenses 
        WHERE tripId = :tripId
        GROUP BY category
        ORDER BY total DESC
    """)
    suspend fun getExpenseSumByCategory(tripId: String): List<CategorySum>
    
    // ══════════════════════════════════════════════════════════
    // ExpenseSplit CRUD
    // ══════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseSplit(split: ExpenseSplit)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseSplits(splits: List<ExpenseSplit>)
    
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getSplitsForExpense(expenseId: String): List<ExpenseSplit>
    
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    fun observeSplitsForExpense(expenseId: String): Flow<List<ExpenseSplit>>
    
    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)
    
    // ══════════════════════════════════════════════════════════
    // Currency Rates
    // ══════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencyRate(rate: CurrencyRate)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencyRates(rates: List<CurrencyRate>)
    
    @Query("""
        SELECT * FROM currency_rates 
        WHERE fromCurrency = :from AND toCurrency = :to AND date = :date
        LIMIT 1
    """)
    suspend fun getCurrencyRate(from: String, to: String, date: LocalDate): CurrencyRate?
    
    @Query("""
        SELECT * FROM currency_rates 
        WHERE fromCurrency = :from AND toCurrency = :to
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun getLatestCurrencyRate(from: String, to: String): CurrencyRate?
    
    @Query("DELETE FROM currency_rates WHERE fetchedAt < :olderThan")
    suspend fun deleteOldRates(olderThan: Long)
}

data class CategorySum(
    val category: ExpenseCategory,
    val total: Double
)

