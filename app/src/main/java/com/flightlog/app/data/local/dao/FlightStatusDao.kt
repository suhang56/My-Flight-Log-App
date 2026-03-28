package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.flightlog.app.data.local.entity.FlightStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightStatusDao {

    @Upsert
    suspend fun upsert(status: FlightStatus)

    @Query("SELECT * FROM flight_status WHERE logbookFlightId = :logbookFlightId")
    fun getByFlightId(logbookFlightId: Long): Flow<FlightStatus?>

    @Query("SELECT * FROM flight_status WHERE logbookFlightId = :logbookFlightId")
    suspend fun getByFlightIdOnce(logbookFlightId: Long): FlightStatus?

    @Query("SELECT * FROM flight_status WHERE trackingEnabled = 1")
    suspend fun getAllTracking(): List<FlightStatus>

    @Query("UPDATE flight_status SET trackingEnabled = 0 WHERE logbookFlightId = :logbookFlightId")
    suspend fun disableTracking(logbookFlightId: Long)
}
