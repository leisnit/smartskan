package com.example.smartskan.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.smartskan.R
import java.text.DateFormat
import java.util.Calendar

/** Fields read from the machine-readable zone (MRZ) of a passport or ID card (ICAO 9303). */
data class MrzData(
    val format: Format,
    val documentCode: String,
    val issuingCountry: String,
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    val birthDate: String?,
    val sex: String,
    val expiryDate: String?,
    val optionalData: String,
    /** Number of check digits that matched, out of [checksTotal]. */
    val checksPassed: Int,
    val checksTotal: Int,
) {
    enum class Format(@StringRes val label: Int) {
        TD1(R.string.label_id_card),
        TD2(R.string.label_id_document),
        TD3(R.string.label_passport),
    }

    val verified get() = checksPassed == checksTotal
    val fullName get() = listOf(givenNames, surname).filter { it.isNotBlank() }.joinToString(" ")

    fun fields(context: Context): List<Pair<String, String>> = listOfNotNull(
        context.getString(R.string.mrz_surname) to surname,
        context.getString(R.string.mrz_given_names) to givenNames,
        context.getString(R.string.mrz_doc_number) to documentNumber,
        context.getString(R.string.mrz_nationality) to nationality,
        birthDate?.let { context.getString(R.string.mrz_birth) to it },
        context.getString(R.string.mrz_sex) to sexLabel(context),
        expiryDate?.let { context.getString(R.string.mrz_expiry) to it },
        context.getString(R.string.mrz_issuing) to issuingCountry,
        context.getString(R.string.mrz_doc_type) to "${context.getString(format.label)} ($documentCode)",
        optionalData.takeIf { it.isNotBlank() }?.let { context.getString(R.string.mrz_optional) to it },
    ).filter { it.second.isNotBlank() }

    private fun sexLabel(context: Context) = context.getString(
        when (sex) {
            "Male" -> R.string.sex_male
            "Female" -> R.string.sex_female
            else -> R.string.sex_unspecified
        },
    )

    fun asText(context: Context): String = fields(context).joinToString("\n") { (k, v) -> "$k: $v" }
}

object Mrz {
    private val allowed = Regex("[^A-Z0-9<]")

    /**
     * Finds and parses an MRZ in OCR'd rows of text. Tries every format against every run of
     * consecutive candidate rows and keeps the reading with the most matching check digits.
     */
    fun parse(rows: List<String>): MrzData? {
        val candidates = rows
            .map { it.uppercase().replace('«', '<').replace('‹', '<').replace(allowed, "") }
            .filter { it.length >= 26 && (it.count { c -> c == '<' } >= 2 || it.count(Char::isDigit) >= 8) }
        var best: MrzData? = null
        fun consider(result: MrzData?) {
            if (result != null && (best == null || result.checksPassed > best!!.checksPassed)) best = result
        }
        for (i in candidates.indices) {
            val a = candidates[i]
            val b = candidates.getOrNull(i + 1) ?: continue
            if (near(a, 44) && near(b, 44)) consider(runCatching { parseTd3(fit(a, 44), fit(b, 44)) }.getOrNull())
            if (near(a, 36) && near(b, 36)) consider(runCatching { parseTd2(fit(a, 36), fit(b, 36)) }.getOrNull())
            val c = candidates.getOrNull(i + 2)
            if (c != null && near(a, 30) && near(b, 30) && near(c, 30)) {
                consider(runCatching { parseTd1(fit(a, 30), fit(b, 30), fit(c, 30)) }.getOrNull())
            }
        }
        // Require at least two independent check digits so random text never "parses".
        return best?.takeIf { it.checksPassed >= 2 }
    }

    // Passport: 2 lines × 44
    private fun parseTd3(l1: String, l2: String): MrzData? {
        if (l1[0] != 'P') return null
        return parseTwoLine(MrzData.Format.TD3, l1, l2, optionalEnd = 42, compositeAt = 43)
    }

    // Visa/ID (TD2): 2 lines × 36
    private fun parseTd2(l1: String, l2: String): MrzData? {
        if (l1[0] !in "ACIV") return null
        return parseTwoLine(MrzData.Format.TD2, l1, l2, optionalEnd = 35, compositeAt = 35)
    }

    private fun parseTwoLine(format: MrzData.Format, l1: String, l2: String, optionalEnd: Int, compositeAt: Int): MrzData {
        val docNumber = l2.substring(0, 9)
        val dob = digits(l2.substring(13, 19))
        val expiry = digits(l2.substring(21, 27))
        val checks = mutableListOf(
            check(docNumber, l2[9]),
            check(dob, l2[19]),
            check(expiry, l2[27]),
        )
        if (format == MrzData.Format.TD3) {
            val personal = l2.substring(28, 42)
            // An empty personal number may use '<' or '0' as its check digit.
            checks += check(personal, l2[42]) || (personal.all { it == '<' } && l2[42] == '<')
        }
        val composite = l2.substring(0, 10) + digits(l2.substring(13, 20)) + l2.substring(21, compositeAt).let {
            digits(it.substring(0, 7)) + it.substring(7)
        }
        checks += check(composite, l2[compositeAt])
        val (surname, given) = names(l1.substring(5))
        return MrzData(
            format = format,
            documentCode = clean(l1.substring(0, 2)),
            issuingCountry = clean(l1.substring(2, 5)),
            surname = surname,
            givenNames = given,
            documentNumber = clean(docNumber),
            nationality = clean(l2.substring(10, 13)),
            birthDate = date(dob, birth = true),
            sex = sex(l2[20]),
            expiryDate = date(expiry, birth = false),
            optionalData = clean(l2.substring(28, optionalEnd)),
            checksPassed = checks.count { it },
            checksTotal = checks.size,
        )
    }

    // ID card: 3 lines × 30
    private fun parseTd1(l1: String, l2: String, l3: String): MrzData? {
        if (l1[0] !in "ACI") return null
        var docNumber = l1.substring(5, 14)
        var docCheck = l1[14]
        var optional = l1.substring(15, 30)
        // Document numbers longer than 9 characters overflow into the optional field.
        if (docCheck == '<') {
            val overflow = optional.substringBefore('<')
            if (overflow.isNotEmpty()) {
                docNumber += overflow.dropLast(1)
                docCheck = overflow.last()
                optional = optional.substring(overflow.length)
            }
        }
        val dob = digits(l2.substring(0, 6))
        val expiry = digits(l2.substring(8, 14))
        val checks = listOf(
            check(docNumber, docCheck),
            check(dob, l2[6]),
            check(expiry, l2[14]),
            check(l1.substring(5, 30) + dob + l2[6] + expiry + l2[14] + l2.substring(18, 29), l2[29]),
        )
        val (surname, given) = names(l3)
        return MrzData(
            format = MrzData.Format.TD1,
            documentCode = clean(l1.substring(0, 2)),
            issuingCountry = clean(l1.substring(2, 5)),
            surname = surname,
            givenNames = given,
            documentNumber = clean(docNumber),
            nationality = clean(l2.substring(15, 18)),
            birthDate = date(dob, birth = true),
            sex = sex(l2[7]),
            expiryDate = date(expiry, birth = false),
            optionalData = clean(optional + l2.substring(18, 29)),
            checksPassed = checks.count { it },
            checksTotal = checks.size,
        )
    }

    // ---------- helpers ----------

    private fun near(s: String, length: Int) = s.length in (length - 3)..(length + 3)

    /** OCR often drops or adds filler at the end; pad/trim to the exact line length. */
    private fun fit(s: String, length: Int) = if (s.length >= length) s.substring(0, length) else s.padEnd(length, '<')

    /** Fixes letters that OCR commonly confuses with digits, for fields that must be numeric. */
    private fun digits(s: String) = s.map {
        when (it) {
            'O', 'Q', 'D' -> '0'
            'I', 'L' -> '1'
            'Z' -> '2'
            'S' -> '5'
            'G' -> '6'
            'B' -> '8'
            else -> it
        }
    }.joinToString("")

    private fun value(c: Char) = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'Z' -> c - 'A' + 10
        else -> 0
    }

    private fun check(data: String, checkChar: Char): Boolean {
        val weights = intArrayOf(7, 3, 1)
        val sum = data.foldIndexed(0) { i, acc, c -> acc + value(c) * weights[i % 3] }
        return digits(checkChar.toString()).firstOrNull() == '0' + sum % 10
    }

    private fun clean(s: String) = s.replace('<', ' ').trim().replace(Regex(" +"), " ")

    private fun names(field: String): Pair<String, String> {
        val letters = field.trimEnd('<').map {
            when (it) {
                '0' -> 'O'; '1' -> 'I'; '2' -> 'Z'; '5' -> 'S'; '6' -> 'G'; '8' -> 'B'
                else -> it
            }
        }.joinToString("")
        val surname = clean(letters.substringBefore("<<"))
        // Trailing '<' filler is sometimes read as a run of K's.
        val given = clean(letters.substringAfter("<<", "")).replace(Regex("(?:\\s*K){3,}$"), "").trim()
        return surname to given
    }

    private fun sex(c: Char) = when (c) {
        'M' -> "Male"
        'F' -> "Female"
        else -> "Unspecified"
    }

    private fun date(yymmdd: String, birth: Boolean): String? {
        if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return null
        val yy = yymmdd.substring(0, 2).toInt()
        val month = yymmdd.substring(2, 4).toInt()
        val day = yymmdd.substring(4, 6).toInt()
        if (month !in 1..12 || day !in 1..31) return null
        val currentYY = Calendar.getInstance().get(Calendar.YEAR) % 100
        val year = when {
            birth -> if (yy > currentYY) 1900 + yy else 2000 + yy
            else -> if (yy > currentYY + 50) 1900 + yy else 2000 + yy
        }
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(cal.time)
    }
}
