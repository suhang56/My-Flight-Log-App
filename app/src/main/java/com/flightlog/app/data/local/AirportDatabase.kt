package com.flightlog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flightlog.app.data.local.dao.AirportDao
import com.flightlog.app.data.local.entity.Airport

@Database(entities = [Airport::class], version = 1, exportSchema = false)
abstract class AirportDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao
}
