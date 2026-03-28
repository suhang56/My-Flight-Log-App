package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "airports")
data class Airport(
    @PrimaryKey val iata: String,
    val icao: String?,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lng: Double,
    val timezone: String?
)
