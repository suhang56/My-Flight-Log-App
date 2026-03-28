package com.flightlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val unlockedAt: Long? = null,
    val seenByUser: Boolean = false
)
