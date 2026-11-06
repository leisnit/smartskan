package com.example.smartskan

import com.example.smartskan.util.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReceiptParserTest {

    @Test
    fun picksTotalNotSubtotalCashOrChange() {
        val lines = listOf(
            "CORNER GROCER", "12 High Street", "Milk 1.20", "Bread 2.35",
            "SUBTOTAL 3.55", "VAT 0.00", "TOTAL 3.55", "CASH 10.00", "CHANGE 6.45",
        )
        val r = ReceiptParser.parse(lines, monthFirst = false)
        assertEquals(355L, r.totalCents)
        assertEquals("CORNER GROCER", r.merchant)
    }

    @Test
    fun totalOnFollowingLine() {
        assertEquals(1299L, ReceiptParser.parse(listOf("Shop", "TOTAL", "12.99")).totalCents)
    }

    @Test
    fun europeanDecimalsAndThousands() {
        assertEquals(700L, ReceiptParser.parse(listOf("Café Roma", "Cappuccino 3,50", "Totale 7,00")).totalCents)
        assertEquals(123456L, ReceiptParser.parse(listOf("Store", "Total 1.234,56")).totalCents)
        assertEquals(123456L, ReceiptParser.parse(listOf("Store", "Total 1,234.56")).totalCents)
    }

    @Test
    fun totalInclVatIsNotSkipped() {
        assertEquals(1200L, ReceiptParser.parse(listOf("Store", "VAT 2.00", "Total incl. VAT 12.00")).totalCents)
    }

    @Test
    fun fallsBackToLargestAmount() {
        assertEquals(650L, ReceiptParser.parse(listOf("Shop", "Item 4.00", "Item 6.50")).totalCents)
    }

    @Test
    fun datesAreNotMistakenForAmounts() {
        assertEquals(500L, ReceiptParser.parse(listOf("Shop", "12.05.24 14:32", "Total 5.00")).totalCents)
        assertEquals(emptyList<Long>(), ReceiptParser.findAmounts("12.05.2024"))
    }

    @Test
    fun parsesDates() {
        fun check(lines: List<String>, monthFirst: Boolean, y: Int, m: Int, d: Int) {
            val millis = ReceiptParser.parse(lines, monthFirst).dateMillis!!
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            assertEquals(y, c.get(Calendar.YEAR))
            assertEquals(m, c.get(Calendar.MONTH) + 1)
            assertEquals(d, c.get(Calendar.DAY_OF_MONTH))
        }
        check(listOf("Date: 25/12/2024"), monthFirst = false, 2024, 12, 25)
        check(listOf("12/25/2024"), monthFirst = false, 2024, 12, 25)
        check(listOf("03/04/2024"), monthFirst = true, 2024, 3, 4)
        check(listOf("03/04/2024"), monthFirst = false, 2024, 4, 3)
        check(listOf("2025-01-31 10:00"), monthFirst = false, 2025, 1, 31)
        assertNull(ReceiptParser.parse(listOf("31/02/2024")).dateMillis)
    }

    @Test
    fun merchantSkipsBoilerplate() {
        assertEquals("Hotel Bella", ReceiptParser.parse(listOf("Welcome!", "Hotel Bella", "Tel 0123")).merchant)
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(1200L, ReceiptParser.parseAmount("12"))
        assertEquals(1250L, ReceiptParser.parseAmount("12.5"))
        assertEquals(1250L, ReceiptParser.parseAmount("12,50"))
        assertEquals(123456L, ReceiptParser.parseAmount("£1,234.56"))
        assertNull(ReceiptParser.parseAmount("abc"))
    }
}
