package com.flightlog.app.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flightlog.app.data.repository.LogbookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: LogbookRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val count = repository.getCount().first()
            val distanceNm = repository.getTotalDistanceNm().first()
            val lastFlight = repository.getMostRecentFlight()

            applicationContext.widgetDataStore.edit { prefs ->
                prefs[WidgetDataKeys.FLIGHT_COUNT] = count
                prefs[WidgetDataKeys.TOTAL_DISTANCE_NM] = distanceNm
                prefs[WidgetDataKeys.LAST_UPDATED_MS] = System.currentTimeMillis()
                if (lastFlight != null) {
                    prefs[WidgetDataKeys.LAST_FLIGHT_DEP] = lastFlight.departureCode
                    prefs[WidgetDataKeys.LAST_FLIGHT_ARR] = lastFlight.arrivalCode
                    prefs[WidgetDataKeys.LAST_FLIGHT_DATE] = lastFlight.departureTimeUtc
                } else {
                    prefs.remove(WidgetDataKeys.LAST_FLIGHT_DEP)
                    prefs.remove(WidgetDataKeys.LAST_FLIGHT_ARR)
                    prefs.remove(WidgetDataKeys.LAST_FLIGHT_DATE)
                }
            }

            FlightLogWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "widget_refresh_periodic"
        private const val WORK_NAME_ONCE = "widget_refresh_once"

        fun enqueueOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }

        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS).build()
            )
        }
    }
}
