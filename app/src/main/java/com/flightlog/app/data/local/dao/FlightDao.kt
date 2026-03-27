package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flightlog.app.data.local.entity.FlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {

    @Query("SELECT * FROM flights ORDER BY departureTime DESC")
    fun getAllFlights(): Flow<List<FlightEntity>>

    @Query("SELECT * FROM flights WHERE departureTime > :now ORDER BY departureTime ASC")
    fun getUpcomingFlights(now: Long): Flow<List<FlightEntity>>

    @Query("SELECT * FROM flights WHERE departureTime <= :now ORDER BY departureTime DESC")
    fun getPastFlights(now: Long): Flow<List<FlightEntity>>

    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun getFlightById(id: Long): FlightEntity?

    @Query("SELECT calendarEventId FROM flights")
    suspend fun getAllCalendarEventIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlights(flights: List<FlightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlight(flight: FlightEntity)

    @Query("DELETE FROM flights WHERE calendarEventId NOT IN (:activeEventIds)")
    suspend fun deleteRemovedEvents(activeEventIds: List<Long>)

    @Query("SELECT COUNT(*) FROM flights")
    fun getFlightCount(): Flow<Int>
}
