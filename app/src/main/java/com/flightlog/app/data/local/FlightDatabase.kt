package com.flightlog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight

@Database(
    entities = [CalendarFlight::class, LogbookFlight::class],
    version = 3,
    exportSchema = true
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun calendarFlightDao(): CalendarFlightDao
    abstract fun logbookFlightDao(): LogbookFlightDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add legIndex column with default 0 for existing single-leg rows.
                db.execSQL("ALTER TABLE calendar_flights ADD COLUMN legIndex INTEGER NOT NULL DEFAULT 0")
                // Drop the old unique index on calendarEventId alone.
                db.execSQL("DROP INDEX IF EXISTS index_calendar_flights_calendarEventId")
                // Create the new composite unique index.
                db.execSQL("CREATE UNIQUE INDEX index_calendar_flights_calendarEventId_legIndex ON calendar_flights (calendarEventId, legIndex)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS logbook_flights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceCalendarEventId INTEGER,
                        flightNumber TEXT NOT NULL,
                        departureCode TEXT NOT NULL,
                        arrivalCode TEXT NOT NULL,
                        departureDateEpochDay INTEGER NOT NULL,
                        departureTimeMillis INTEGER NOT NULL,
                        arrivalTimeMillis INTEGER,
                        durationMinutes INTEGER,
                        departureTimezone TEXT,
                        arrivalTimezone TEXT,
                        aircraftType TEXT,
                        seatClass TEXT,
                        seatNumber TEXT,
                        distanceKm INTEGER,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_logbook_flights_departureDateEpochDay ON logbook_flights (departureDateEpochDay)"
                )
            }
        }
    }
}
