package com.triloo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Expense — одна запись о расходе внутри поездки.
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
    val currency: String,                       // Код валюты ISO 4217: USD, EUR, RUB и т.д.
    
    val amountInBaseCurrency: Double,           // Сумма, пересчитанная в базовую валюту поездки.
    val exchangeRate: Double,                   // Курс, использованный для конвертации.
    val exchangeRateDate: LocalDate,            // Дата курса для истории.
    
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    
    val paidByUserId: String,                   // Кто оплатил расход.
    val paidByName: String,                     // Отображаемое имя плательщика в UI.
    
    val splitType: SplitType = SplitType.EQUAL, // Как делится сумма между участниками.
    val splitAmounts: Map<String, Double>? = null, // userId -> amount для пользовательских сплитов.
    
    val date: LocalDate,
    val time: String? = null,                   // Время в формате "14:30".
    
    val placeId: String? = null,                // Ссылка на место, если расход связан с ним.
    val placeName: String? = null,
    
    val receiptImageUrl: String? = null,        // Фото чека.
    val notes: String? = null,
    
    val isSettled: Boolean = false,             // Был ли расход уже урегулирован.
    
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
    EQUAL("Поровну"),               // Делим сумму поровну между всеми участниками.
    EXACT("Точные суммы"),          // Для каждого участника задаётся точная сумма.
    PERCENTAGE("Проценты"),         // Доля каждого задаётся в процентах.
    SHARES("Доли"),                 // Деление по пользовательским долям, например 2:1:1.
    PAYER_ONLY("Только плательщик") // Личный расход без сплита.
}

/**
 * ExpenseSplit — распределение одного расхода между конкретными участниками.
 */
@Entity(
    tableName = "expense_splits",
    primaryKeys = ["expenseId", "userId"]
)
data class ExpenseSplit(
    val expenseId: String,
    val userId: String,
    val userName: String,
    
    val shareAmount: Double,                    // Сумма, которую должен этот участник.
    val shareAmountInBaseCurrency: Double,
    
    val isPaid: Boolean = false                 // Погашена ли эта доля.
)

/**
 * Balance — рассчитанный долг между двумя участниками поездки.
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
 * ExpenseSummary — агрегированная статистика расходов по поездке.
 */
data class ExpenseSummary(
    val tripId: String,
    val totalAmount: Double,
    val currency: String,
    
    val byCategory: Map<ExpenseCategory, Double>,
    val byPerson: Map<String, Double>,          // userId -> сколько всего потратил пользователь.
    val byDay: Map<LocalDate, Double>,
    
    val averagePerDay: Double,
    val averagePerPerson: Double
)

/**
 * Локальное хранилище валютного курса на конкретную дату.
 */
@Entity(tableName = "currency_rates")
data class CurrencyRate(
    @PrimaryKey
    val id: String,                             // Идентификатор формата "USD_RUB_2024-01-15".
    
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val date: LocalDate,
    
    val fetchedAt: Long = System.currentTimeMillis()
)


