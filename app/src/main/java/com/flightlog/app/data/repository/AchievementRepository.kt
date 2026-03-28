package com.flightlog.app.data.repository

import com.flightlog.app.data.achievements.AchievementDefinitions
import com.flightlog.app.data.achievements.AchievementEvaluator
import com.flightlog.app.data.local.dao.AchievementDao
import com.flightlog.app.data.local.dao.LogbookFlightDao
import com.flightlog.app.data.local.entity.Achievement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepository @Inject constructor(
    private val achievementDao: AchievementDao,
    private val logbookFlightDao: LogbookFlightDao
) {

    fun getAll(): Flow<List<Achievement>> = achievementDao.getAll()

    /**
     * Ensures every defined achievement has a row in the database.
     * Idempotent — uses INSERT OR IGNORE so existing rows (including unlocked ones)
     * are never overwritten. Safe against race with concurrent checkAndUnlock().
     * Called at app startup on Dispatchers.IO.
     */
    suspend fun ensureAllExist() = withContext(Dispatchers.IO) {
        val allLocked = AchievementDefinitions.ALL.map { Achievement(id = it.id) }
        achievementDao.insertAllIgnore(allLocked)
    }

    /**
     * Evaluates all achievement conditions against the current flight list.
     * Newly unlocked achievements are upserted with the current timestamp.
     * Already-unlocked achievements are never overwritten.
     */
    suspend fun checkAndUnlock() = withContext(Dispatchers.IO) {
        val flights = logbookFlightDao.getAllOnce()
        val current = achievementDao.getAll().first()

        val newlyUnlocked = AchievementEvaluator.evaluate(flights, current)
        if (newlyUnlocked.isEmpty()) return@withContext

        val now = System.currentTimeMillis()
        val toUpsert = newlyUnlocked.map { id ->
            Achievement(id = id, unlockedAt = now, seenByUser = false)
        }
        achievementDao.upsertAll(toUpsert)
    }

    suspend fun markAllSeen() = withContext(Dispatchers.IO) {
        achievementDao.markAllSeen()
    }
}
