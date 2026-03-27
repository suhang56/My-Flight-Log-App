package com.flightlog.app.data.repository

/** Outcome of a [CalendarRepository.syncFromCalendar] call. */
sealed class SyncResult {
    data class Success(val syncedCount: Int, val removedCount: Int) : SyncResult()
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}
