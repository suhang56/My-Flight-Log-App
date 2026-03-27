package com.flightlog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.flightlog.app.data.local.dao.FlightDao
import com.flightlog.app.data.local.entity.FlightEntity

@Database(
    entities = [FlightEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
}
