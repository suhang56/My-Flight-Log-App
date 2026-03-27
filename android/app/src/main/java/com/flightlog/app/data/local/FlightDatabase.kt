package com.flightlog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight

@Database(
    entities = [CalendarFlight::class],
    version = 1,
    exportSchema = false
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun calendarFlightDao(): CalendarFlightDao
}
