package com.triloo

import com.triloo.data.ocr.ReceiptTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ReceiptTextParserTest {

    @Test
    fun parseExtractsRussianReceiptFields() {
        val receiptText = """
            Вкусно и точка
            КАССОВЫЙ ЧЕК
            24.03.2026 13:47
            ИТОГО 1 249,50 ₽
        """.trimIndent()

        val parsed = ReceiptTextParser.parse(receiptText, fallbackCurrency = "RUB")

        assertEquals("Вкусно и точка", parsed.merchantName)
        assertEquals(1249.50, parsed.totalAmount ?: 0.0, 0.01)
        assertEquals("RUB", parsed.currency)
        assertEquals(LocalDate.of(2026, 3, 24), parsed.purchaseDate)
        assertEquals("13:47", parsed.purchaseTime)
    }

    @Test
    fun parseFallsBackToLargestAmountWhenNoTotalKeyword() {
        val receiptText = """
            Coffee Point
            Latte 4.50 USD
            Muffin 3.20 USD
            7.70 USD
        """.trimIndent()

        val parsed = ReceiptTextParser.parse(receiptText, fallbackCurrency = "USD")

        assertNotNull(parsed.totalAmount)
        assertEquals(7.70, parsed.totalAmount ?: 0.0, 0.01)
        assertEquals("USD", parsed.currency)
    }
}
