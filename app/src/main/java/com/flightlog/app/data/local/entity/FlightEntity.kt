package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "flights")
data class FlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val calendarEventId: Long,
    val flightNumber: String,
    val airline: String,
    val departureAirport: String,
    val arrivalAirport: String,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val title: String,
    val notes: String = "",
    val syncedAt: Instant = Instant.now()
)
