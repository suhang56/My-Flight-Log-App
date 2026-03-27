package com.flightlog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flightlog.app.data.local.dao.CalendarFlightDao
import com.flightlog.app.data.local.entity.CalendarFlight

@Database(
    entities = [CalendarFlight::class],
    version = 2,
    exportSchema = false
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun calendarFlightDao(): CalendarFlightDao

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
    }
}
