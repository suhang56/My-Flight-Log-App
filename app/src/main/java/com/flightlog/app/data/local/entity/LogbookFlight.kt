package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logbook_flights",
    indices = [Index(value = ["departureDateEpochDay"])]
)
data class LogbookFlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Soft link to CalendarFlight.calendarEventId — not a foreign key. */
    val sourceCalendarEventId: Long? = null,

    val flightNumber: String,
    val departureCode: String,
    val arrivalCode: String,

    /** LocalDate.toEpochDay() of the departure date in the departure timezone. */
    val departureDateEpochDay: Long,

    /** Departure time in epoch millis. */
    val departureTimeMillis: Long,

    /** Arrival time in epoch millis. Null when unknown. */
    val arrivalTimeMillis: Long? = null,

    val durationMinutes: Int? = null,

    /** IANA timezone ID for the departure airport. */
    val departureTimezone: String? = null,

    /** IANA timezone ID for the arrival airport. */
    val arrivalTimezone: String? = null,

    val aircraftType: String? = null,

    /** Aircraft registration / tail number (e.g. "JA812A"). */
    val registration: String? = null,

    /** One of: economy, premium_economy, business, first. */
    val seatClass: String? = null,

    val seatNumber: String? = null,

    /** Great-circle distance in kilometres. */
    val distanceKm: Int? = null,

    val notes: String? = null,

    /** User rating 1–5 stars, or null if not rated. */
    val rating: Int? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
