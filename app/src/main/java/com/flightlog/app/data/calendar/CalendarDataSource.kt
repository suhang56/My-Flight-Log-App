package com.flightlog.app.data.calendar

import android.content.ContentResolver
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContentResolver wrapper that queries CalendarContract.Events.
 *
 * Query window: events starting within the last 30 days through 90 days in the future.
 * This keeps the dataset bounded while covering recent past flights and upcoming bookings.
 *
 * Requires [android.Manifest.permission.READ_CALENDAR] to be granted at call time.
 */
@Singleton
class CalendarDataSource @Inject constructor() {

    companion object {
        private val LOOK_BACK_MS  = TimeUnit.DAYS.toMillis(30)
        private val LOOK_AHEAD_MS = TimeUnit.DAYS.toMillis(90)

        private val PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )
    }

    /**
     * Fetches calendar events within the rolling window and returns them as [RawCalendarEvent]s.
     * Skips events with blank titles. Returns an empty list rather than throwing when the cursor
     * is null (e.g. provider not available on the device).
     */
    fun queryEvents(
        contentResolver: ContentResolver,
        lookBackMs: Long = LOOK_BACK_MS,
        lookAheadMs: Long = LOOK_AHEAD_MS
    ): List<RawCalendarEvent> {
        val now        = System.currentTimeMillis()
        val rangeStart = now - lookBackMs
        val rangeEnd   = now + lookAheadMs

        val selection     = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(rangeStart.toString(), rangeEnd.toString())

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return emptyList()

        return cursor.use { c ->
            val idIdx    = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descIdx  = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locIdx   = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx   = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)

            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(titleIdx)
                    if (title.isNullOrBlank()) continue

                    add(
                        RawCalendarEvent(
                            eventId     = c.getLong(idIdx),
                            title       = title,
                            description = c.getString(descIdx).orEmpty(),
                            location    = c.getString(locIdx).orEmpty(),
                            dtStart     = c.getLong(startIdx),
                            dtEnd       = c.getLong(endIdx).takeIf { it > 0 }
                        )
                    )
                }
            }
        }
    }
}
