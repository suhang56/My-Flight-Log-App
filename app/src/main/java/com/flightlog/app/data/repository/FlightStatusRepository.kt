package com.flightlog.app.data.repository

import androidx.room.withTransaction
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.dao.FlightStatusDao
import com.flightlog.app.data.local.entity.FlightStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightStatusRepository @Inject constructor(
    private val flightStatusDao: FlightStatusDao,
    private val database: FlightDatabase
) {

    fun getByFlightId(logbookFlightId: Long): Flow<FlightStatus?> =
        flightStatusDao.getByFlightId(logbookFlightId)

    suspend fun getByFlightIdOnce(logbookFlightId: Long): FlightStatus? =
        flightStatusDao.getByFlightIdOnce(logbookFlightId)

    suspend fun upsert(status: FlightStatus) =
        flightStatusDao.upsert(status)

    /**
     * Atomically reads the existing status row, passes it to [buildStatus],
     * and upserts the result -- prevents race conditions between concurrent workers.
     */
    suspend fun readAndUpsert(
        logbookFlightId: Long,
        buildStatus: (existing: FlightStatus?) -> FlightStatus
    ) {
        database.withTransaction {
            val existing = flightStatusDao.getByFlightIdOnce(logbookFlightId)
            flightStatusDao.upsert(buildStatus(existing))
        }
    }

    suspend fun disableTracking(logbookFlightId: Long) =
        flightStatusDao.disableTracking(logbookFlightId)
}
