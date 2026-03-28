package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flight_status",
    indices = [Index("logbookFlightId", unique = true)]
)
data class FlightStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logbookFlightId: Long,
    val flightNumber: String,
    val statusEnum: String,
    val departureDelayMin: Int? = null,
    val arrivalDelayMin: Int? = null,
    val departureGate: String? = null,
    val arrivalGate: String? = null,
    val estimatedDepartureUtc: Long? = null,
    val estimatedArrivalUtc: Long? = null,
    val actualDepartureUtc: Long? = null,
    val actualArrivalUtc: Long? = null,
    val liveLat: Double? = null,
    val liveLng: Double? = null,
    val liveAltitude: Int? = null,
    val liveSpeedKnots: Int? = null,
    val liveHeading: Int? = null,
    val lastPolledAt: Long,
    val trackingEnabled: Boolean = true
)
