package com.flightlog.app.di

import android.content.Context
import androidx.room.Room
import com.flightlog.app.data.local.FlightDatabase
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.dao.LogbookFlightDao
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
            .addMigrations(
                FlightDatabase.MIGRATION_1_2,
                FlightDatabase.MIGRATION_2_3,
                FlightDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    fun provideCalendarFlightDao(database: FlightDatabase): CalendarFlightDao {
        return database.calendarFlightDao()
    }

    @Provides
    fun provideLogbookFlightDao(database: FlightDatabase): LogbookFlightDao {
        return database.logbookFlightDao()
    }
}
