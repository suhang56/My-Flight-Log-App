# Feature 12 — Achievements & Gamification

**Scope:** Medium | **Sprint:** 1 (parallel with F10) | **Dependencies:** None
**Zero new libraries.** Builds on existing Room + Logbook + Stats infrastructure.

---

## Achievement List

| ID | Name | Tier | Unlock Condition |
|---|---|---|---|
| `first_flight` | First Takeoff | BRONZE | Log 1 flight |
| `first_manual_add` | Flight Historian | BRONZE | Manually add a flight (not via calendar) |
| `ten_flights` | Frequent Flyer | BRONZE | Log 10 flights |
| `five_airports` | Airport Hopper | BRONZE | Visit 5 unique airports (dep or arr) |
| `five_airlines` | Multi-Carrier | BRONZE | Fly 5 unique airlines (non-empty flightNumber prefix) |
| `fifty_flights` | Road Warrior | SILVER | Log 50 flights |
| `twenty_airports` | Globe Trotter | SILVER | Visit 20 unique airports |
| `three_seat_classes` | Class Act | SILVER | Log flights in 3 different seat classes |
| `distance_10k` | 10K Club | SILVER | Total logged distance >= 10,000 nm |
| `short_hop` | Short Hop | SILVER | Log 5 flights each with distanceNm < 300 nm |
| `century_club` | Century Club | GOLD | Log 100 flights |
| `fifty_airports` | World Wanderer | GOLD | Visit 50 unique airports |
| `long_hauler` | Long Hauler | GOLD | Single flight >= 5,000 nm |
| `distance_100k` | Around the World | GOLD | Total logged distance >= 100,000 nm |
| `night_owl` | Night Owl | GOLD | 3 flights departing 00:00–04:59 local time |
| `five_hundred_flights` | Elite Traveler | PLATINUM | Log 500 flights |
| `ultra_long_haul` | Ultra Marathon | PLATINUM | Single flight >= 8,000 nm |
| `distance_500k` | Circumnavigator | PLATINUM | Total logged distance >= 500,000 nm |

*Distance thresholds use `distanceNm` field (nautical miles). Null distance flights are excluded from distance checks.*

---

## Data Model

### Room Entity: `Achievement` (in `FlightDatabase`, version 6 → 7)

```kotlin
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val unlockedAt: Long?,          // epoch millis; null = locked
    val seenByUser: Boolean = false // false = show "NEW" badge dot on Stats nav
)
```

### Migration 6 → 7

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS achievements (id TEXT NOT NULL PRIMARY KEY, unlockedAt INTEGER, seenByUser INTEGER NOT NULL DEFAULT 0)"
        )
    }
}
```

### AchievementDefinition (in-memory only, not stored)

```kotlin
data class AchievementDefinition(val id: String, val name: String, val description: String, val tier: Tier)
enum class Tier { BRONZE, SILVER, GOLD, PLATINUM }
```

`AchievementDefinitions.kt` — static `val ALL: List<AchievementDefinition>` for all 18 entries.

---

## AchievementEvaluator

Pure function, no Room/Hilt dependency — unit-testable with plain data:

```kotlin
object AchievementEvaluator {
    fun evaluate(flights: List<LogbookFlight>, current: List<Achievement>): Set<String>
}
```

Returns IDs that should be unlocked but are not yet. `AchievementRepository.checkAndUnlock()` calls this, then upserts newly unlocked rows with `unlockedAt = System.currentTimeMillis()`. Does NOT overwrite existing `unlockedAt` — unlocks are permanent.

---

## AchievementDao

```kotlin
@Dao interface AchievementDao {
    @Query("SELECT * FROM achievements") fun getAll(): Flow<List<Achievement>>
    @Upsert suspend fun upsert(achievement: Achievement)
    @Upsert suspend fun upsertAll(list: List<Achievement>)
    @Query("UPDATE achievements SET seenByUser = 1 WHERE unlockedAt IS NOT NULL") suspend fun markAllSeen()
}
```

---

## AchievementRepository

- `ensureAllExist()` — called at app startup via `AppInitializer`; inserts locked rows for any missing IDs (idempotent)
- `checkAndUnlock(flights)` — calls `AchievementEvaluator.evaluate()`, upserts newly unlocked rows
- Trigger: `LogbookRepository` calls `checkAndUnlock()` after every add/edit/delete

---

## UI Placement

**Statistics screen** gains a `TabRow` with two tabs: "Stats" (existing, unchanged) and "Achievements".

Achievements tab layout:
- Header: "7 / 18 unlocked"
- Tier sections (PLATINUM → GOLD → SILVER → BRONZE, top-down)
- Each card: tier-colored left border, icon, name, description, unlock date or "Locked" (40% alpha)
- Newly unseen unlocked cards: shimmer highlight before `markAllSeen()` is called on tab open
- Statistics bottom nav icon: red badge dot when `achievements.any { it.unlockedAt != null && !it.seenByUser }`

---

## Implementation Steps

1. `MIGRATION_6_7` + bump `FlightDatabase` to version 7, add `Achievement` entity + `AchievementDao`
2. `AchievementDefinitions.kt` — 18 static definitions
3. `AchievementEvaluator.kt` — pure evaluation logic
4. `AchievementRepository.kt` — `ensureAllExist()`, `checkAndUnlock()`, `markAllSeen()`
5. Wire `ensureAllExist()` at startup and `checkAndUnlock()` into `LogbookRepository`
6. `AchievementsViewModel.kt` — combines `getAll()` flow with definitions, calls `markAllSeen()` on open
7. `AchievementsScreen.kt` composable — tier-grouped list
8. Add `TabRow` to `StatisticsScreen.kt`, wrap existing content in tab 0
9. Badge dot on Statistics bottom nav item in `NavGraph.kt`
10. `AchievementEvaluatorTest.kt` — unit tests for all 18 conditions + edge cases

---

## Files to Create / Edit

| Action | File |
|---|---|
| Create | `data/local/entity/Achievement.kt` |
| Create | `data/local/dao/AchievementDao.kt` |
| Create | `data/achievements/AchievementDefinitions.kt` |
| Create | `data/achievements/AchievementEvaluator.kt` |
| Create | `data/repository/AchievementRepository.kt` |
| Create | `ui/statistics/AchievementsScreen.kt` |
| Create | `ui/statistics/AchievementsViewModel.kt` |
| Edit | `data/local/FlightDatabase.kt` — version 7, add entity + DAO + MIGRATION_6_7 |
| Edit | `di/DatabaseModule.kt` — provide AchievementDao + AchievementRepository |
| Edit | `data/repository/LogbookRepository.kt` — call checkAndUnlock after write ops |
| Edit | `ui/statistics/StatisticsScreen.kt` — add TabRow |
| Edit | `ui/navigation/NavGraph.kt` — badge dot on Statistics nav item |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| 0 flights | All 18 locked, no crash |
| Exactly N flights at boundary (10, 50, 100, 500) | Unlocks at exactly N |
| Flight with null distanceNm | Excluded from all distance + long_hauler checks |
| `short_hop`: 4 flights < 300 nm, 5th is exactly 300 nm | NOT unlocked — condition is strictly < 300 |
| `night_owl`: departure timezone null | Falls back to UTC for hour check, no crash |
| `night_owl`: departure at 00:00 local | Counts (inclusive lower bound) |
| `night_owl`: departure at 05:00 local | Does NOT count (exclusive upper bound) |
| Already-unlocked achievement, user adds more flights | `unlockedAt` timestamp unchanged — no overwrite |
| User deletes flights that triggered an unlock | Achievement stays unlocked (no regression) |
| `ensureAllExist()` called twice on same install | Idempotent — existing rows preserved |
| Migration 6→7 on device with 50 existing flights | All earned achievements unlock on first `checkAndUnlock()` post-migration |
| All 18 unlocked | UI renders correctly, header shows "18 / 18 unlocked" |
| Multiple achievements unlock in one add operation | All unlocked in same evaluation pass, single `upsertAll()` call |
