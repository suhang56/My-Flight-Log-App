package com.flightlog.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import com.flightlog.app.BuildConfig
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flightlog.app.data.local.entity.FlightStatus
import com.flightlog.app.data.network.FlightAwareApi
import com.flightlog.app.data.network.FlightAwareFlight
import com.flightlog.app.data.network.FlightStatusEnum
import com.flightlog.app.data.network.FlightRouteServiceImpl
import com.flightlog.app.data.network.toFlightStatusEnum
import com.flightlog.app.data.repository.FlightStatusRepository
import com.flightlog.app.data.repository.LogbookRepository
import com.flightlog.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@HiltWorker
class FlightTrackingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: FlightAwareApi,
    private val flightStatusRepository: FlightStatusRepository,
    private val logbookRepository: LogbookRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val logbookFlightId = inputData.getLong(KEY_LOGBOOK_FLIGHT_ID, -1L)
        if (logbookFlightId == -1L) return Result.failure()

        val logbookFlight = logbookRepository.getById(logbookFlightId) ?: return Result.failure()
        val flightNumber = logbookFlight.flightNumber
        if (flightNumber.isBlank()) return Result.failure()

        val existingStatus = flightStatusRepository.getByFlightIdOnce(logbookFlightId)
        if (existingStatus?.trackingEnabled == false) {
            cancelWorker(applicationContext, logbookFlightId)
            return Result.success()
        }

        return try {
            // Derive start date from departure time in departure timezone
            val depDate = deriveDepartureDate(logbookFlight.departureTimeUtc, logbookFlight.departureTimezone)

            val response = api.getFlights(ident = flightNumber, start = depDate)
            if (!response.isSuccessful) {
                Log.w(TAG, "API returned ${response.code()} for $flightNumber")
                return if (response.code() == 429) Result.retry()
                else {
                    cancelWorker(applicationContext, logbookFlightId)
                    Result.failure()
                }
            }

            val flights = response.body()?.flights
            if (flights.isNullOrEmpty()) return Result.success()

            // Pick best match: origin matches departure code + closest scheduled time
            val bestMatch = pickBestMatch(flights, logbookFlight.departureCode, logbookFlight.departureTimeUtc)
                ?: return Result.success()

            val statusEnum = bestMatch.status.toFlightStatusEnum()

            // Fetch position if en route
            var liveLat: Double? = null
            var liveLng: Double? = null
            var liveAltitude: Int? = null
            var liveSpeedKnots: Int? = null
            var liveHeading: Int? = null

            if (statusEnum == FlightStatusEnum.EN_ROUTE) {
                val posResponse = api.getPosition(ident = flightNumber)
                if (posResponse.isSuccessful) {
                    val pos = posResponse.body()?.lastPosition
                    if (pos != null && isValidPosition(pos.latitude, pos.longitude)) {
                        liveLat = pos.latitude
                        liveLng = pos.longitude
                        liveAltitude = pos.altitude
                        liveSpeedKnots = pos.groundspeed
                        liveHeading = pos.heading
                    }
                }
            }

            val now = System.currentTimeMillis()

            // Wrap read+upsert in a transaction to prevent race conditions
            var previousStatus: FlightStatus? = null
            var savedStatus: FlightStatus? = null
            flightStatusRepository.readAndUpsert(logbookFlightId) { existing ->
                previousStatus = existing
                val status = FlightStatus(
                    id = existing?.id ?: 0,
                    logbookFlightId = logbookFlightId,
                    flightNumber = flightNumber,
                    statusEnum = statusEnum.name,
                    departureDelayMin = bestMatch.departureDelay?.let { it / 60 },
                    arrivalDelayMin = bestMatch.arrivalDelay?.let { it / 60 },
                    departureGate = bestMatch.gateOrigin,
                    arrivalGate = bestMatch.gateDestination,
                    estimatedDepartureUtc = FlightRouteServiceImpl.parseIsoToUtc(bestMatch.estimatedOut),
                    estimatedArrivalUtc = FlightRouteServiceImpl.parseIsoToUtc(bestMatch.estimatedIn),
                    actualDepartureUtc = FlightRouteServiceImpl.parseIsoToUtc(bestMatch.actualOut),
                    actualArrivalUtc = FlightRouteServiceImpl.parseIsoToUtc(bestMatch.actualIn),
                    liveLat = liveLat,
                    liveLng = liveLng,
                    liveAltitude = liveAltitude,
                    liveSpeedKnots = liveSpeedKnots,
                    liveHeading = liveHeading,
                    lastPolledAt = now,
                    trackingEnabled = statusEnum !in setOf(FlightStatusEnum.LANDED, FlightStatusEnum.CANCELLED, FlightStatusEnum.DIVERTED)
                )
                savedStatus = status
                status
            }

            // Fire notifications on status changes
            savedStatus?.let { newStatus ->
                fireNotificationsIfNeeded(previousStatus, newStatus, logbookFlight.departureCode, logbookFlight.arrivalCode)
            }

            // Self-cancel on terminal states
            if (statusEnum in setOf(FlightStatusEnum.LANDED, FlightStatusEnum.CANCELLED, FlightStatusEnum.DIVERTED)) {
                cancelWorker(applicationContext, logbookFlightId)
            }

            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error tracking $flightNumber", e)
            else Log.e(TAG, "Error tracking $flightNumber")
            Result.retry()
        }
    }

    private fun fireNotificationsIfNeeded(
        old: FlightStatus?,
        new: FlightStatus,
        departureCode: String,
        arrivalCode: String
    ) {
        val oldEnum = old?.statusEnum?.let { runCatching { FlightStatusEnum.valueOf(it) }.getOrNull() }
        val newEnum = runCatching { FlightStatusEnum.valueOf(new.statusEnum) }.getOrNull() ?: return

        // Status change notifications
        if (oldEnum != newEnum) {
            when (newEnum) {
                FlightStatusEnum.EN_ROUTE, FlightStatusEnum.DEPARTED ->
                    notificationHelper.notifyDeparted(new.flightNumber, departureCode)
                FlightStatusEnum.LANDED ->
                    notificationHelper.notifyLanded(new.flightNumber, arrivalCode)
                FlightStatusEnum.CANCELLED ->
                    notificationHelper.notifyCancelled(new.flightNumber)
                else -> { /* no notification */ }
            }
        }

        // Delay notification: only on increase >= 15 min from previous notified value
        val oldDelay = old?.departureDelayMin ?: 0
        val newDelay = new.departureDelayMin ?: 0
        if (newDelay - oldDelay >= 15) {
            notificationHelper.notifyDelay(new.flightNumber, newDelay, new.estimatedDepartureUtc)
        }

        // Gate change notification
        if (old?.departureGate != null && new.departureGate != null && old.departureGate != new.departureGate) {
            notificationHelper.notifyGateChange(new.flightNumber, new.departureGate)
        }
    }

    private fun pickBestMatch(
        flights: List<FlightAwareFlight>,
        departureCode: String,
        departureTimeUtc: Long
    ): FlightAwareFlight? {
        val matching = flights.filter {
            it.origin?.codeIata.equals(departureCode, ignoreCase = true)
        }
        if (matching.isEmpty()) return flights.firstOrNull()

        return matching.minByOrNull { flight ->
            val scheduled = FlightRouteServiceImpl.parseIsoToUtc(flight.scheduledOut) ?: Long.MAX_VALUE
            abs(scheduled - departureTimeUtc)
        }
    }

    private fun deriveDepartureDate(departureTimeUtc: Long, departureTimezone: String?): String {
        val zone = try {
            departureTimezone?.let { ZoneId.of(it) } ?: ZoneId.of("UTC")
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }
        return Instant.ofEpochMilli(departureTimeUtc)
            .atZone(zone)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** Guard against Null Island (0.0, 0.0) and null positions. */
    private fun isValidPosition(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        return !(lat == 0.0 && lng == 0.0)
    }

    companion object {
        private const val TAG = "FlightTrackingWorker"
        const val KEY_LOGBOOK_FLIGHT_ID = "logbook_flight_id"

        fun enqueue(context: Context, logbookFlightId: Long, flightNumber: String) {
            val workName = "track_flight_$logbookFlightId"
            val request = PeriodicWorkRequestBuilder<FlightTrackingWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_LOGBOOK_FLIGHT_ID to logbookFlightId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelWorker(context: Context, logbookFlightId: Long) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("track_flight_$logbookFlightId")
        }
    }
}
