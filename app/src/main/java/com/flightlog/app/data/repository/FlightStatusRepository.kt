package com.flightlog.app.data.repository

import com.flightlog.app.data.local.dao.FlightStatusDao
import com.flightlog.app.data.local.entity.FlightStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightStatusRepository @Inject constructor(
    private val flightStatusDao: FlightStatusDao
) {

    fun getByFlightId(logbookFlightId: Long): Flow<FlightStatus?> =
        flightStatusDao.getByFlightId(logbookFlightId)

    suspend fun getByFlightIdOnce(logbookFlightId: Long): FlightStatus? =
        flightStatusDao.getByFlightIdOnce(logbookFlightId)

    suspend fun upsert(status: FlightStatus) =
        flightStatusDao.upsert(status)

    suspend fun disableTracking(logbookFlightId: Long) =
        flightStatusDao.disableTracking(logbookFlightId)
}
