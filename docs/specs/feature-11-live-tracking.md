# Feature 11 — Live Flight Tracking + Push Notifications

**Scope:** Large | **Sprint:** 2 (after F10) | **Dependencies:** F10 (AirportRepository, soft)
**CRITICAL CONSTRAINT:** AviationStack free tier = 100 req/month. Every API call is precious.

---

## AviationStack API Fields (free tier, confirmed available)

The existing `AviationStackFlight` model must be extended with status fields:

```kotlin
// Extend existing model — add to AviationStackApi.kt
@JsonClass(generateAdapter = true)
data class AviationStackFlight(
    @Json(name = "departure") val departure: AviationStackEndpoint?,
    @Json(name = "arrival")   val arrival: AviationStackEndpoint?,
    @Json(name = "aircraft")  val aircraft: AviationStackAircraft?,
    @Json(name = "flight")    val flight: AviationStackFlightInfo?,  // NEW
    @Json(name = "live")      val live: AviationStackLive?           // NEW (null if not airborne)
)

@JsonClass(generateAdapter = true)
data class AviationStackEndpoint(
    @Json(name = "iata")      val iata: String?,
    @Json(name = "timezone")  val timezone: String?,
    @Json(name = "scheduled") val scheduled: String?,
    @Json(name = "estimated") val estimated: String?,   // NEW
    @Json(name = "actual")    val actual: String?,      // NEW
    @Json(name = "delay")     val delay: Int?,          // NEW — minutes
    @Json(name = "gate")      val gate: String?         // NEW
)

@JsonClass(generateAdapter = true)
data class AviationStackFlightInfo(
    @Json(name = "iata")   val iata: String?,
    @Json(name = "status") val status: String?  // "scheduled","active","landed","cancelled","diverted","incident","redirected"
)

@JsonClass(generateAdapter = true)
data class AviationStackLive(
    @Json(name = "latitude")  val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "altitude")  val altitude: Double?,
    @Json(name = "speed_horizontal") val speedKph: Double?
)
```

**Free tier note:** `flight_date` filter is unavailable. Query by `flight_iata` only and match by departure IATA + closest scheduled time to narrow to the right flight leg.

---

## Data Model

### FlightStatus entity (`FlightDatabase`, version 7 → 8)

```kotlin
@Entity(tableName = "flight_status", indices = [Index("logbookFlightId", unique = true)])
data class FlightStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logbookFlightId: Long,          // FK → logbook_flights.id
    val flightNumber: String,
    val status: String,                 // raw AviationStack status string
    val departureDelay: Int?,           // minutes, null = unknown
    val arrivalDelay: Int?,
    val departureGate: String?,
    val arrivalGate: String?,
    val actualDepartureUtc: Long?,
    val actualArrivalUtc: Long?,
    val liveLat: Double?,               // null if not airborne
    val liveLng: Double?,
    val liveAltitude: Double?,
    val liveSpeedKph: Double?,
    val lastPolledAt: Long,             // epoch millis
    val pollCount: Int = 0,             // budget tracking
    val trackingEnabled: Boolean = true // user-toggled per flight
)
```

### Migration 7 → 8

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS flight_status (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                logbookFlightId INTEGER NOT NULL UNIQUE,
                flightNumber TEXT NOT NULL,
                status TEXT NOT NULL,
                departureDelay INTEGER,
                arrivalDelay INTEGER,
                departureGate TEXT,
                arrivalGate TEXT,
                actualDepartureUtc INTEGER,
                actualArrivalUtc INTEGER,
                liveLat REAL,
                liveLng REAL,
                liveAltitude REAL,
                liveSpeedKph REAL,
                lastPolledAt INTEGER NOT NULL,
                pollCount INTEGER NOT NULL DEFAULT 0,
                trackingEnabled INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
    }
}
```

---

## Request Budget Manager

**Target: stay within 80 req/month** (20 req safety buffer). Reset on the 1st of each month.

```kotlin
object TrackingBudgetManager {
    private const val PREFS = "tracking_budget"
    private const val KEY_COUNT = "req_count"
    private const val KEY_MONTH = "req_month"
    private const val MONTHLY_LIMIT = 80

    fun canMakeRequest(context: Context): Boolean { ... }   // checks count < MONTHLY_LIMIT
    fun recordRequest(context: Context) { ... }             // increments count, resets on new month
    fun remainingRequests(context: Context): Int { ... }
    fun resetIfNewMonth(context: Context) { ... }           // called at app start
}
```

Stored in SharedPreferences (not DataStore — synchronous read needed in WorkManager).

**Per-flight hard cap: max 6 polls** (stored in `FlightStatus.pollCount`). At 6 polls the worker stops scheduling more work for that flight regardless of budget remaining. Reasoning: 6 polls × 15 min = 90 min window — covers gate + departure + cruise + landing for short-haul. For long-haul, polling stops mid-flight but the landing notification fires when user reopens the app and status is fetched on-demand.

---

## FlightStatusWorker

`PeriodicWorkRequest` with 15-minute minimum interval (WorkManager minimum). Only enqueued for flights whose departure is within 24 hours and not yet landed.

```
Worker name: "track_flight_{logbookFlightId}"
Constraints: NetworkType.CONNECTED
Tags: "flight_tracking"
```

Worker logic:
1. Check `TrackingBudgetManager.canMakeRequest()` — if false, log and exit with `Result.success()` (do not retry)
2. Check `FlightStatus.pollCount >= 6` — if true, cancel this worker
3. Call `AviationStackApi.getFlightByNumber(flightIata)` — no date param (free tier)
4. From response list, find best match: departure IATA == logbook flight's departure AND scheduled departure closest to stored `departureTimeUtc`
5. Record request via `TrackingBudgetManager.recordRequest()`
6. Update `FlightStatus` row with new status + increment `pollCount`
7. Compare new status to previous — if changed, fire notification
8. If status is `"landed"` or `"cancelled"`, cancel this worker after notification

---

## Status Display on FlightDetailScreen

**"Track This Flight" button** — shown only when:
- flight has a non-empty `flightNumber`
- departure is in the future or within 6 hours past
- `FlightStatus.trackingEnabled == true` OR no status row exists yet

When tapped:
1. Create/update `FlightStatus` row with `trackingEnabled = true`
2. Enqueue `FlightStatusWorker` for this flight
3. Button changes to "Tracking" state with spinner

**LiveStatusCard** — shown above `TimelineSection` when a `FlightStatus` row exists for this flight:

```
┌──────────────────────────────────────┐
│  IN AIR  •  NH847  •  Last updated 3m ago  │
│  DEP: On time  14:35 HND → ARR: Est 18:20 NRT  │
│  Gate: 64  •  Delay: None             │
└──────────────────────────────────────┘
```

Status badge colors:
- `scheduled` → outline (no color)
- `active` / in air → primaryContainer (blue)
- `landed` → tertiaryContainer (green)
- `cancelled` / `diverted` → errorContainer (red)

**Budget warning**: if `remainingRequests <= 10`, show `"X requests remaining this month"` in small text below card.

**Live position on RouteMapCanvas**: when `liveLat`/`liveLng` are non-null, pass to `RouteMapCanvas` as optional `livePosition` parameter. Draw a filled circle on the arc at the interpolated position.

---

## Push Notifications

**NotificationChannel setup** (create in `FlightLogApplication.onCreate()`):

| Channel ID | Name | Importance |
|---|---|---|
| `flight_status` | Flight Status | HIGH |
| `flight_delay` | Delays & Gate Changes | DEFAULT |

**Android 13+ POST_NOTIFICATIONS permission**: request alongside calendar permission in onboarding page 3, or on first "Track This Flight" tap if not yet granted.

**Notification triggers** (compare previous vs new `FlightStatus`):

| Trigger | Channel | Example |
|---|---|---|
| Status → `active` (departed) | `flight_status` | "NH847 has departed HND — now en route to NRT" |
| Status → `landed` | `flight_status` | "NH847 has landed at NRT — welcome!" |
| Status → `cancelled` | `flight_status` | "NH847 has been cancelled" |
| `departureDelay` increases by ≥ 15 min | `flight_delay` | "NH847 delayed 45 min — new departure 16:20" |
| `departureGate` changes | `flight_delay` | "NH847 gate changed to 64" |

Notifications deep-link to `FlightDetailScreen` via PendingIntent with the flight's logbook ID.

---

## Implementation Steps

1. Extend `AviationStackFlight`, `AviationStackEndpoint` models with new fields
2. `MIGRATION_7_8` + bump `FlightDatabase` to version 8, add `FlightStatus` entity + `FlightStatusDao`
3. `TrackingBudgetManager.kt` (SharedPreferences, synchronous)
4. `FlightStatusRepository.kt` — getByLogbookId (Flow), upsert, enableTracking, disableTracking
5. `FlightStatusWorker.kt` — budget check → API call → match flight → diff → notify → increment
6. Notification channel setup in `FlightLogApplication.onCreate()`
7. `FlightDetailViewModel` — inject `FlightStatusRepository`, expose `statusFlow`, `onTrackFlight()`
8. `LiveStatusCard` composable + "Track This Flight" button in `FlightDetailScreen`
9. `RouteMapCanvas` — add optional `livePosition: LatLng?` parameter, draw position dot
10. POST_NOTIFICATIONS permission request (in onboarding or on-demand)
11. `FlightStatusWorkerTest.kt` + `TrackingBudgetManagerTest.kt`

---

## Files to Create / Edit

| Action | File |
|---|---|
| Edit | `data/network/AviationStackApi.kt` — add new model fields |
| Create | `data/local/entity/FlightStatus.kt` |
| Create | `data/local/dao/FlightStatusDao.kt` |
| Create | `data/preferences/TrackingBudgetManager.kt` |
| Create | `data/repository/FlightStatusRepository.kt` |
| Create | `worker/FlightStatusWorker.kt` |
| Edit | `data/local/FlightDatabase.kt` — version 8, add entity + DAO + MIGRATION_7_8 |
| Edit | `di/DatabaseModule.kt` — provide FlightStatusDao + FlightStatusRepository |
| Edit | `FlightLogApplication.kt` — create notification channels |
| Edit | `ui/logbook/FlightDetailViewModel.kt` — inject repo, expose statusFlow + onTrackFlight() |
| Edit | `ui/logbook/FlightDetailScreen.kt` — add LiveStatusCard, "Track" button, live position param |
| Edit | `ui/logbook/RouteMapCanvas.kt` — add livePosition dot rendering |
| Edit | `AndroidManifest.xml` — POST_NOTIFICATIONS permission |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| `flightNumber` is blank | "Track This Flight" button not shown |
| Budget exhausted (80 req used) | Worker exits with `Result.success()`, no API call, status card shows budget warning |
| `pollCount >= 6` | Worker cancels itself, no more polls for this flight |
| AviationStack returns multiple flights for same number | Select best match by departure IATA + closest scheduled time; if tie, pick first |
| API returns 0 results for flight number | No status update, no notification, pollCount still incremented (request was made) |
| API returns HTTP 429 / rate limit | Worker returns `Result.retry()`, does NOT increment pollCount (request failed) |
| API returns HTTP 401 (invalid key) | Worker returns `Result.failure()`, cancel worker, show error in status card |
| Status changes from `active` → `landed` in same poll | Fire "landed" notification only (skip "departed" notification that was already missed) |
| `departureDelay` fluctuates: 30 → 15 → 45 min | Notify only when delay increases by ≥ 15 min from last notified value; do not notify on decrease |
| Gate changes back to original gate | Still notify — any gate change triggers notification |
| `liveLat`/`liveLng` both null (flight not yet airborne) | Position dot not shown on map; no crash |
| `liveLat`/`liveLng` = 0.0, 0.0 (Null Island) | Guard: treat as null (same fix as Feature 10) |
| User disables tracking mid-flight | `trackingEnabled = false`, worker cancelled, "Track" button reappears |
| Monthly budget resets on the 1st | `resetIfNewMonth()` called at app start; `req_count` reset to 0 if stored month != current month |
| Flight tracked across month boundary (e.g. Jan 31 → Feb 1) | Budget resets; February starts fresh — flight may get additional polls if below per-flight cap |
| POST_NOTIFICATIONS denied by user | Tracking still works (polls + updates status card), no notifications sent — no crash |
| WorkManager task enqueued twice for same flight (double-tap) | Use `ExistingPeriodicWorkPolicy.KEEP` — second enqueue is a no-op |
