package com.flightlog.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flightlog.app.data.repository.CalendarRepository
import com.flightlog.app.data.repository.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically syncs flight data from the device calendar.
 *
 * Hilt injects [CalendarRepository] via [AssistedInject]; the WorkerFactory must
 * be wired in the Application class (see AndroidManifest_PERMISSIONS.md).
 *
 * Schedule: every 6 hours with a 30-minute flex window.
 * Tag: [TAG_CALENDAR_SYNC] — use this tag to observe or cancel the work.
 */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarRepository: CalendarRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val contentResolver = applicationContext.contentResolver
        return when (val syncResult = calendarRepository.syncFromCalendar(contentResolver)) {
            is SyncResult.Success -> {
                Result.success(
                    workDataOf(
                        KEY_SYNCED_COUNT  to syncResult.syncedCount,
                        KEY_REMOVED_COUNT to syncResult.removedCount
                    )
                )
            }
            is SyncResult.Error -> {
                if (syncResult.cause is SecurityException) {
                    // Permission not granted — surface as failure so WorkManager
                    // does not attempt an exponential back-off retry.
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to syncResult.message))
                } else {
                    // Transient error (e.g. ContentProvider unavailable) — let
                    // WorkManager retry with its default back-off policy.
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val TAG_CALENDAR_SYNC  = "calendar_sync"
        private const val WORK_NAME  = "calendar_sync_periodic"

        private const val KEY_SYNCED_COUNT  = "synced_count"
        private const val KEY_REMOVED_COUNT = "removed_count"
        private const val KEY_ERROR_MESSAGE = "error_message"

        /**
         * Enqueues the periodic sync, or keeps the existing enqueued work if one is
         * already scheduled ([ExistingPeriodicWorkPolicy.KEEP]).
         *
         * Call this once after the user grants calendar permission, typically from
         * [com.flightlog.app.ui.calendarflights.CalendarFlightsViewModel.onPermissionResult].
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                repeatInterval      = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval    = 30,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG_CALENDAR_SYNC)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
