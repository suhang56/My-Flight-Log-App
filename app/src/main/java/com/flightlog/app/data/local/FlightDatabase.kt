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
    version = 5,
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
                db.execSQL("ALTER TABLE calendar_flights ADD COLUMN departureTimezone TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE calendar_flights ADD COLUMN arrivalTimezone TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS logbook_flights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceCalendarEventId INTEGER,
                        sourceLegIndex INTEGER,
                        flightNumber TEXT NOT NULL DEFAULT '',
                        departureCode TEXT NOT NULL,
                        arrivalCode TEXT NOT NULL,
                        departureTimeUtc INTEGER NOT NULL,
                        arrivalTimeUtc INTEGER,
                        departureTimezone TEXT,
                        arrivalTimezone TEXT,
                        distanceNm INTEGER,
                        notes TEXT NOT NULL DEFAULT '',
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_logbook_flights_sourceCalendarEventId_sourceLegIndex ON logbook_flights (sourceCalendarEventId, sourceLegIndex)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_logbook_flights_departureTimeUtc ON logbook_flights (departureTimeUtc)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logbook_flights ADD COLUMN aircraftType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE logbook_flights ADD COLUMN seatClass TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE logbook_flights ADD COLUMN seatNumber TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
