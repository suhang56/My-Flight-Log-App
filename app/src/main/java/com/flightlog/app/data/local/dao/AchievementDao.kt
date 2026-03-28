package com.flightlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.flightlog.app.data.local.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements")
    fun getAll(): Flow<List<Achievement>>

    @Upsert
    suspend fun upsert(achievement: Achievement)

    @Upsert
    suspend fun upsertAll(list: List<Achievement>)

    /** Insert only if not exists — used by ensureAllExist to avoid overwriting unlocked achievements */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(list: List<Achievement>)

    @Query("UPDATE achievements SET seenByUser = 1 WHERE unlockedAt IS NOT NULL")
    suspend fun markAllSeen()
}
