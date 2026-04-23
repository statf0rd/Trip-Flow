package com.triloo.data.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln

data class ParsedReceiptData(
    val merchantName: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseTime: String? = null,
    val rawText: String = ""
)

object ReceiptTextParser {
    private val totalKeywords = listOf(
        "итог", "итого", "к оплате", "сумма", "всего", "total", "amount", "due", "grand total"
    )
    private val ignoreDescriptionKeywords = listOf(
        "кассир", "чек", "receipt", "спасибо", "www", "http", "тел", "phone"
    )
    private val amountRegex = Regex("""(?<!\d)(\d{1,3}(?:[ \u00A0]\d{3})*(?:[.,]\d{2})|\d+(?:[.,]\d{2})|\d{2,6})(?!\d)""")
    private val timeRegex = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val datePatterns = listOf(
        DatePattern(Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4})\b"""), DateOrder.DAY_MONTH_YEAR),
        DatePattern(Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b"""), DateOrder.YEAR_MONTH_DAY),
        DatePattern(Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2})\b"""), DateOrder.DAY_MONTH_SHORT_YEAR)
    )

    fun parse(
        text: String,
        fallbackCurrency: String? = null
    ): ParsedReceiptData {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) {
            return ParsedReceiptData(rawText = text.trim())
        }

        val currency = detectCurrency(text) ?: fallbackCurrency?.uppercase(Locale.US)
        val amount = detectAmount(lines)
        val date = detectDate(lines)
        val time = detectTime(lines)
        val merchant = detectMerchant(lines)

        return ParsedReceiptData(
            merchantName = merchant,
            totalAmount = amount,
            currency = currency,
            purchaseDate = date,
            purchaseTime = time,
            rawText = lines.joinToString("\n")
        )
    }

    private fun detectAmount(lines: List<String>): Double? {
        val bestCandidate = lines.mapNotNull { line ->
            val amounts = amountRegex.findAll(line)
                .mapNotNull { parseAmount(it.value) }
                .filter { value -> value in 1.0..1_000_000.0 }
                .toList()
            if (amounts.isEmpty()) return@mapNotNull null

            val lower = line.lowercase(Locale.getDefault())
            val hasCurrency = detectCurrency(line) != null
            val looksLikeDate = datePatterns.any { it.regex.containsMatchIn(line) }
            val keywordScore = totalKeywords.count { keyword -> lower.contains(keyword) } * 10.0
            val currencyScore = if (hasCurrency) 4.0 else 0.0
            val datePenalty = if (looksLikeDate && !hasCurrency) -6.0 else 0.0
            val bestAmount = amounts.maxOrNull() ?: return@mapNotNull null
            val magnitudeScore = ln(bestAmount.coerceAtLeast(1.0))

            AmountCandidate(
                amount = bestAmount,
                score = keywordScore + currencyScore + datePenalty + magnitudeScore
            )
        }.maxByOrNull { candidate -> candidate.score }

        return bestCandidate?.amount
    }

    private fun detectCurrency(text: String): String? {
        val upper = text.uppercase(Locale.US)
        return when {
            upper.contains("₽") || upper.contains("RUB") || upper.contains("RUR") -> "RUB"
            upper.contains("€") || upper.contains("EUR") -> "EUR"
            upper.contains("$") || upper.contains("USD") -> "USD"
            upper.contains("₸") || upper.contains("KZT") -> "KZT"
            upper.contains("₺") || upper.contains("TRY") -> "TRY"
            upper.contains("BYN") -> "BYN"
            upper.contains("AED") -> "AED"
            upper.contains("GEL") -> "GEL"
            else -> null
        }
    }

    private fun detectDate(lines: List<String>): LocalDate? {
        return lines.firstNotNullOfOrNull { line ->
            datePatterns.firstNotNullOfOrNull { pattern ->
                pattern.regex.find(line)?.let { match ->
                    parseDateMatch(match.groupValues.drop(1), pattern.order)
                }
            }
        }
    }

    private fun detectTime(lines: List<String>): String? {
        return lines.firstNotNullOfOrNull { line ->
            timeRegex.find(line)?.value
        }
    }

    private fun detectMerchant(lines: List<String>): String? {
        return lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.getDefault())
            line.any { it.isLetter() } &&
                ignoreDescriptionKeywords.none { keyword -> lower.contains(keyword) } &&
                !datePatterns.any { it.regex.containsMatchIn(line) } &&
                !timeRegex.containsMatchIn(line) &&
                amountRegex.findAll(line).count() <= 1
        }?.take(80)
    }

    private fun parseAmount(raw: String): Double? {
        val normalized = raw
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(",", ".")
        return normalized.toDoubleOrNull()
    }

    private fun parseDateMatch(parts: List<String>, order: DateOrder): LocalDate? {
        val (year, month, day) = when (order) {
            DateOrder.DAY_MONTH_YEAR -> Triple(parts[2].toIntOrNull(), parts[1].toIntOrNull(), parts[0].toIntOrNull())
            DateOrder.YEAR_MONTH_DAY -> Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
            DateOrder.DAY_MONTH_SHORT_YEAR -> {
                val shortYear = parts[2].toIntOrNull() ?: return null
                Triple(2000 + shortYear, parts[1].toIntOrNull(), parts[0].toIntOrNull())
            }
        }

        val safeYear = year ?: return null
        val safeMonth = month ?: return null
        val safeDay = day ?: return null
        if (safeYear !in 2000..2100) return null
        return runCatching { LocalDate.of(safeYear, safeMonth, safeDay) }.getOrNull()
    }

    private data class AmountCandidate(
        val amount: Double,
        val score: Double
    )

    private data class DatePattern(
        val regex: Regex,
        val order: DateOrder
    )

    private enum class DateOrder {
        DAY_MONTH_YEAR,
        YEAR_MONTH_DAY,
        DAY_MONTH_SHORT_YEAR
    }
}
