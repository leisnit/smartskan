package com.example.smartskan.util

import android.content.Context
import android.content.res.Resources
import com.example.smartskan.R
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** "1 page", "3 pages". */
fun pagesLabel(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.pages, count, count)

/** "1 file", "3 files". */
fun filesLabel(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.files, count, count)

/**
 * The region set on the device. The app's locale can't be used for this: a language picked in Settings
 * usually has no region, and changing language shouldn't change someone's country or currency.
 */
fun deviceRegion(): String = Resources.getSystem().configuration.locales[0].country

/** Default name for a new document, e.g. "Scan 2026-11-08 13.17". */
fun timestampName(prefix: String): String =
    "$prefix ${SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())}"

fun String.withoutPdfExtension(): String = if (endsWith(".pdf", ignoreCase = true)) dropLast(4) else this

fun formatMoney(cents: Long): String {
    val format = NumberFormat.getCurrencyInstance()
    // Fails for regions with no currency; the locale's default is used then.
    runCatching { format.currency = Currency.getInstance(Locale.Builder().setRegion(deviceRegion()).build()) }
    return format.format(cents / 100.0)
}

/** Amount for an editable text field, e.g. "12.50". */
fun plainAmount(cents: Long): String = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun startOfMonth(monthOffset: Int = 0): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.MONTH, monthOffset)
}.timeInMillis

/** Material DatePicker works in UTC midnight; our dates are local times. */
fun localToUtcDate(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun utcDateToLocalNoon(millis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}
