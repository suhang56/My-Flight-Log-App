package com.flightlog.app.di

import android.content.Context
import androidx.room.Room
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.dao.CalendarFlightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFlightDatabase(@ApplicationContext context: Context): FlightDatabase {
        return Room.databaseBuilder(
            context,
            FlightDatabase::class.java,
            "flight_log_db"
        )
            .addMigrations(FlightDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideCalendarFlightDao(database: FlightDatabase): CalendarFlightDao {
        return database.calendarFlightDao()
    }
}
