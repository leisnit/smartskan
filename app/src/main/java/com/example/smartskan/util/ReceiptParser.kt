package com.example.smartskan.util

import java.util.Calendar
import java.util.Locale

/** Best-effort extraction of merchant, total and date from OCR'd receipt lines. */
object ReceiptParser {

    data class Result(val merchant: String?, val totalCents: Long?, val dateMillis: Long?)

    // Matches 12.34, 12,34, 1,234.56, 1.234,56 — but not dates like 12.05.24 or 3-decimal weights.
    private val amountRegex = Regex("""(?<![\d/.,])(\d{1,3}(?:[.,]\d{3}){1,2}|\d{1,7})[.,](\d{2})(?![\d/]|[.,]\d)""")

    private val totalKeywords = listOf("total", "amount due", "balance due", "to pay", "amount", "gesamt", "totale", "montant", "importe")
    private val skipKeywords = listOf("subtotal", "sub total", "sub-total", "change", "cash", "tender", "saving", "saved", "discount", "points")
    private val taxRegex = Regex("""\b(tax|vat|gst|hst)\b""")
    private val merchantSkip = Regex("""\b(receipt|welcome|invoice|tel|phone|www|http|https)\b""")

    private val isoDate = Regex("""(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val numericDate = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")

    fun parse(lines: List<String>, monthFirst: Boolean = Locale.getDefault().country == "US"): Result {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return Result(findMerchant(clean), findTotal(clean), findDate(clean, monthFirst))
    }

    /** All money amounts in a line, in cents. */
    fun findAmounts(line: String): List<Long> = amountRegex.findAll(line).map { m ->
        m.groupValues[1].replace(",", "").replace(".", "").toLong() * 100 + m.groupValues[2].toLong()
    }.toList()

    /** Parses user-typed amounts like "12", "12.5", "12,50", "£1,234.56" into cents. */
    fun parseAmount(input: String): Long? {
        val cleaned = input.trim().replace(Regex("""[^\d.,]"""), "")
        if (cleaned.isEmpty()) return null
        val simple = Regex("""^(\d{1,7})(?:[.,](\d{1,2}))?$""").matchEntire(cleaned)
            ?: return findAmounts(cleaned).singleOrNull()
        val whole = simple.groupValues[1].toLong()
        val frac = simple.groupValues[2]
        return whole * 100 + when (frac.length) {
            0 -> 0
            1 -> frac.toLong() * 10
            else -> frac.toLong()
        }
    }

    private fun isSkipped(lower: String): Boolean =
        skipKeywords.any { it in lower } || (taxRegex.containsMatchIn(lower) && "incl" !in lower)

    internal fun findTotal(lines: List<String>): Long? {
        val candidates = mutableListOf<Long>()
        lines.forEachIndexed { i, line ->
            val lower = line.lowercase()
            if (totalKeywords.none { it in lower } || isSkipped(lower)) return@forEachIndexed
            val here = findAmounts(line)
            // OCR often puts the label and the value on separate lines.
            candidates += here.ifEmpty {
                lines.getOrNull(i + 1)?.takeUnless { isSkipped(it.lowercase()) }?.let(::findAmounts).orEmpty()
            }
        }
        if (candidates.isNotEmpty()) return candidates.max()
        return lines.filterNot { isSkipped(it.lowercase()) }.flatMap(::findAmounts).maxOrNull()
    }

    internal fun findMerchant(lines: List<String>): String? = lines.take(6).firstOrNull { line ->
        val letters = line.count { it.isLetter() }
        val digits = line.count { it.isDigit() }
        val lower = line.lowercase()
        letters >= 3 && letters > digits && '@' !in lower && !merchantSkip.containsMatchIn(lower)
    }?.take(40)

    internal fun findDate(lines: List<String>, monthFirst: Boolean): Long? {
        for (line in lines) {
            isoDate.find(line)?.let { m ->
                val (y, mo, d) = m.destructured
                toMillis(y.toInt(), mo.toInt(), d.toInt())?.let { return it }
            }
            numericDate.find(line)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val y = m.groupValues[3].toInt().let { if (it < 100) it + 2000 else it }
                val (day, month) = when {
                    a > 12 -> a to b
                    b > 12 -> b to a
                    monthFirst -> b to a
                    else -> a to b
                }
                toMillis(y, month, day)?.let { return it }
            }
        }
        return null
    }

    private fun toMillis(year: Int, month: Int, day: Int): Long? {
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(year, month - 1, day, 12, 0, 0)
            }.timeInMillis
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
