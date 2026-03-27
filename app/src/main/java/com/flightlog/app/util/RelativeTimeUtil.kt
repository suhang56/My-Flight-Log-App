package com.flightlog.app.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

object RelativeTimeUtil {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

    fun relativeLabel(departureTime: Instant, now: Instant = Instant.now()): String {
        val duration = Duration.between(now, departureTime)
        val totalMinutes = duration.toMinutes()
        val isFuture = totalMinutes > 0

        val absMinutes = totalMinutes.absoluteValue

        return when {
            absMinutes < 60 -> {
                if (isFuture) "in ${absMinutes}m"
                else "${absMinutes}m ago"
            }
            absMinutes < 1440 -> {
                val hours = absMinutes / 60
                if (isFuture) "in ${hours}h"
                else "${hours}h ago"
            }
            else -> {
                val days = absMinutes / 1440
                if (isFuture) "in ${days}d"
                else "${days}d ago"
            }
        }
    }

    fun formatDate(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DATE_FORMAT.format(instant.atZone(zoneId))

    fun formatTime(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMAT.format(instant.atZone(zoneId))

    fun formatDateTime(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DATETIME_FORMAT.format(instant.atZone(zoneId))
}
