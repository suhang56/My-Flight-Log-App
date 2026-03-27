package com.flightlog.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short date: "Mar 27, 2026" */
val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** Date + time with timezone: "MMM d, yyyy  HH:mm z" */
val DATE_TIME_TZ_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm z", Locale.getDefault())

/** Full date + time with timezone: "Thursday, Mar 27, 2026  14:30 JST" */
val FULL_DATE_TIME_TZ_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy  HH:mm z", Locale.getDefault())

/** Time with timezone only: "14:30 JST" */
val TIME_TZ_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm z", Locale.getDefault())

/** Day of week + short date: "Thu, Mar 27" */
val DAY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** Formats epoch millis in the given IANA timezone, falling back to system default. */
fun formatInZone(
    epochMillis: Long,
    ianaTimezone: String?,
    formatter: DateTimeFormatter = DATE_TIME_TZ_FORMATTER
): String {
    val zone = ianaTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(zone).format(formatter)
}
