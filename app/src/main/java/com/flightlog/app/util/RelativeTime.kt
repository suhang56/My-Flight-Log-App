package com.flightlog.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatter for the "> 6 days" fallback: "Mar 27", "Dec 5", etc.
private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Converts an epoch-millis timestamp to a human-readable relative time label,
 * using calendar-day comparison (not a raw 24 h delta) for accuracy around midnight.
 *
 * Rules (relative to today's calendar date in the system time zone):
 *  - Same day          -> "Today"
 *  - One day ahead     -> "Tomorrow"
 *  - One day behind    -> "Yesterday"
 *  - 2-6 days ahead    -> "In N days"
 *  - 2-6 days behind   -> "N days ago"
 *  - > 6 days in either direction -> "Mar 27" (abbreviated month + day, no year)
 */
fun Long.toRelativeTimeLabel(
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val flightDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val today      = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    // ChronoUnit.DAYS.between is signed: positive = future, negative = past.
    val dayDelta = java.time.temporal.ChronoUnit.DAYS.between(today, flightDate)

    return when (dayDelta) {
        0L    -> "Today"
        1L    -> "Tomorrow"
        -1L   -> "Yesterday"
        in 2L..6L   -> "In $dayDelta days"
        in -6L..-2L -> "${-dayDelta} days ago"
        else  -> flightDate.format(MONTH_DAY_FORMATTER)   // e.g. "Mar 27"
    }
}

/**
 * Converts an epoch-millis timestamp to a human-readable elapsed-time label,
 * suitable for "Last synced: ..." subtitles where precision matters more than
 * calendar-day comparison.
 *
 * Rules:
 *  - < 1 min     -> "Just now"
 *  - 1-59 min    -> "N min ago"
 *  - 1-23 hours  -> "N hr ago"
 *  - >= 24 hours -> delegates to [toRelativeTimeLabel] for day-based labels
 */
fun Long.toRelativeElapsedLabel(
    now: Long = System.currentTimeMillis()
): String {
    val deltaMillis = (now - this).coerceAtLeast(0)
    val deltaMinutes = deltaMillis / 60_000
    val deltaHours = deltaMinutes / 60

    return when {
        deltaMinutes < 1  -> "Just now"
        deltaMinutes < 60 -> "$deltaMinutes min ago"
        deltaHours < 24   -> "$deltaHours hr ago"
        else              -> toRelativeTimeLabel(now)
    }
}

/**
 * Convenience that accepts a [LocalDate] directly — useful in unit tests
 * where you want to control both the flight date and "today" without epoch math.
 */
fun LocalDate.toRelativeTimeLabel(today: LocalDate = LocalDate.now()): String {
    val dayDelta = java.time.temporal.ChronoUnit.DAYS.between(today, this)
    return when (dayDelta) {
        0L    -> "Today"
        1L    -> "Tomorrow"
        -1L   -> "Yesterday"
        in 2L..6L   -> "In $dayDelta days"
        in -6L..-2L -> "${-dayDelta} days ago"
        else  -> this.format(MONTH_DAY_FORMATTER)
    }
}
