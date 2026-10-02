package com.sigil.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Date/time strings for list rows and detail headers, ported from the app.js formatters. */
object TimeFormat {
    private const val DAY_MS = 86_400_000L

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** The "12:11 PM" / "Yesterday" / "Wednesday" / "27 Sept" shown in list rows. */
    fun shortTime(t: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
        val d = Calendar.getInstance(locale).apply { timeInMillis = t }
        val today = Calendar.getInstance(locale).apply { timeInMillis = now }
        if (sameDay(d, today)) return SimpleDateFormat("h:mm a", locale).format(Date(t))

        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
        if (sameDay(d, yesterday)) return "Yesterday"

        if (now - t < 6 * DAY_MS) return SimpleDateFormat("EEEE", locale).format(Date(t))

        val sameYear = d.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
        return SimpleDateFormat(pattern, locale).format(Date(t))
    }

    /** "just now" / "2 hours ago" / "4 months ago", for "Edited ..." captions. */
    fun relativeTime(t: Long, now: Long = System.currentTimeMillis()): String {
        val diffSec = (now - t) / 1000.0
        val a = abs(diffSec)
        return when {
            a < 60 -> "just now"
            a < 3600 -> ago((diffSec / 60).roundToLong(), "minute")
            a < 86400 -> ago((diffSec / 3600).roundToLong(), "hour")
            a < 86400.0 * 30 -> ago((diffSec / 86400).roundToLong(), "day")
            a < 86400.0 * 365 -> ago((diffSec / 86400 / 30).roundToLong(), "month")
            else -> ago((diffSec / 86400 / 365).roundToLong(), "year")
        }
    }

    private fun ago(n: Long, unit: String): String {
        val an = abs(n).coerceAtLeast(1)
        return "$an $unit${if (an == 1L) "" else "s"} ago"
    }

    enum class Bucket(val label: String) { TODAY("Today"), WEEK("Previous 7 Days"), MONTH("Previous 30 Days"), EARLIER("Earlier") }

    /** Which list-row group heading `t` falls under. */
    fun bucket(t: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): Bucket {
        val d = Calendar.getInstance(locale).apply { timeInMillis = t }
        val today = Calendar.getInstance(locale).apply { timeInMillis = now }
        if (sameDay(d, today)) return Bucket.TODAY
        val diff = now - t
        return when {
            diff < 7 * DAY_MS -> Bucket.WEEK
            diff < 30 * DAY_MS -> Bucket.MONTH
            else -> Bucket.EARLIER
        }
    }
}
