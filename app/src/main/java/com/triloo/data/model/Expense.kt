package com.triloo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Expense — A single expense entry within a trip
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("paidByUserId"), Index("date")]
)
data class Expense(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val tripId: String,
    
    val description: String,
    val amount: Double,
    val currency: String,                       // ISO 4217: USD, EUR, RUB, etc.
    
    val amountInBaseCurrency: Double,           // Converted to trip's base currency
    val exchangeRate: Double,                   // Rate used for conversion
    val exchangeRateDate: LocalDate,            // Date of rate (for history)
    
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    
    val paidByUserId: String,                   // Who paid
    val paidByName: String,                     // Display name for UI
    
    val splitType: SplitType = SplitType.EQUAL, // How to split
    val splitAmounts: Map<String, Double>? = null, // userId -> amount (for custom splits)
    
    val date: LocalDate,
    val time: String? = null,                   // "14:30" format
    
    val placeId: String? = null,                // Link to a Place if relevant
    val placeName: String? = null,
    
    val receiptImageUrl: String? = null,        // Photo of receipt
    val notes: String? = null,
    
    val isSettled: Boolean = false,             // Has this been settled?
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ExpenseCategory(
    val emoji: String,
    val displayName: String,
    val colorHex: Long
) {
    FOOD("🍽️", "Еда", 0xFFF97316),
    TRANSPORT("🚕", "Транспорт", 0xFF6366F1),
    ACCOMMODATION("🏨", "Жильё", 0xFF8B5CF6),
    ENTERTAINMENT("🎭", "Развлечения", 0xFFEC4899),
    SHOPPING("🛍️", "Покупки", 0xFF14B8A6),
    TICKETS("🎫", "Билеты", 0xFF3B82F6),
    GROCERIES("🛒", "Продукты", 0xFF22C55E),
    COFFEE("☕", "Кофе", 0xFF92400E),
    DRINKS("🍺", "Напитки", 0xFFF59E0B),
    TIPS("💰", "Чаевые", 0xFFFBBF24),
    HEALTH("💊", "Здоровье", 0xFFEF4444),
    SOUVENIRS("🎁", "Сувениры", 0xFFD946EF),
    OTHER("📝", "Другое", 0xFF64748B);
    
    companion object {
        fun fromString(name: String): ExpenseCategory {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OTHER
        }
    }
}

enum class SplitType(val displayName: String) {
    EQUAL("Поровну"),              // Split equally among all participants
    EXACT("Точные суммы"),         // Each person pays exact amount
    PERCENTAGE("Проценты"),         // Each person pays percentage
    SHARES("Доли"),                // Custom shares (2:1:1 etc)
    PAYER_ONLY("Только плательщик") // No split, personal expense
}

/**
 * ExpenseSplit — How an expense is split among participants
 */
@Entity(
    tableName = "expense_splits",
    primaryKeys = ["expenseId", "userId"]
)
data class ExpenseSplit(
    val expenseId: String,
    val userId: String,
    val userName: String,
    
    val shareAmount: Double,                    // Amount this person owes
    val shareAmountInBaseCurrency: Double,
    
    val isPaid: Boolean = false                 // Has this share been settled?
)

/**
 * Balance — Calculated debt between two participants
 */
data class Balance(
    val fromUserId: String,
    val fromUserName: String,
    val toUserId: String,
    val toUserName: String,
    
    val amount: Double,
    val currency: String
)

/**
 * ExpenseSummary — Aggregated expense statistics for a trip
 */
data class ExpenseSummary(
    val tripId: String,
    val totalAmount: Double,
    val currency: String,
    
    val byCategory: Map<ExpenseCategory, Double>,
    val byPerson: Map<String, Double>,          // userId -> total spent
    val byDay: Map<LocalDate, Double>,
    
    val averagePerDay: Double,
    val averagePerPerson: Double
)

/**
 * Currency rates storage
 */
@Entity(tableName = "currency_rates")
data class CurrencyRate(
    @PrimaryKey
    val id: String,                             // "USD_RUB_2024-01-15"
    
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val date: LocalDate,
    
    val fetchedAt: Long = System.currentTimeMillis()
)



