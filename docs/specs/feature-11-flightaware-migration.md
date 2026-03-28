# Feature 11 — FlightAware AeroAPI Migration + Live Flight Tracking

**Scope:** Large | **Sprint:** 2
**This spec supersedes `feature-11-live-tracking.md` (AviationStack version).**

Two goals combined in one PR:
1. **Migration**: replace AviationStack with FlightAware AeroAPI across the entire app
2. **Feature 11**: live tracking, status display, push notifications, live position on map

---

## FlightAware AeroAPI — Endpoints Used

Base URL: `https://aeroapi.flightaware.com/aeroapi/`
Auth: request header `x-apikey: <key>` (HTTPS only, no query param)

| Endpoint | Used For |
|---|---|
| `GET /flights/{ident}` | Route lookup (replaces AviationStack `v1/flights`) + live status |
| `GET /flights/{ident}/position` | Live lat/lng/altitude/speed (polling) |

`{ident}` = IATA flight number e.g. `NH847`. Optional query param `?start=` (ISO-8601) filters by date — **this works on all paid tiers**, unlike AviationStack free tier.

### /flights/{ident} — relevant response fields

```json
{
  "flights": [{
    "ident": "NH847",
    "ident_iata": "NH847",
    "status": "En Route / On Time",
    "departure_delay": 0,
    "arrival_delay": 0,
    "origin": { "code_iata": "HND", "timezone": "Asia/Tokyo" },
    "destination": { "code_iata": "NRT", "timezone": "Asia/Tokyo" },
    "scheduled_out": "2026-03-27T14:35:00+09:00",
    "estimated_out": "2026-03-27T14:35:00+09:00",
    "actual_out": null,
    "scheduled_in": "2026-03-27T18:20:00+09:00",
    "estimated_in": "2026-03-27T18:20:00+09:00",
    "actual_in": null,
    "gate_origin": "64",
    "gate_destination": null,
    "aircraft_type": "B788"
  }]
}
```

`status` is a human-readable string. Map to internal enum using prefix matching (see below).

### /flights/{ident}/position — relevant response fields

```json
{
  "last_position": {
    "fa_flight_id": "...",
    "latitude": 35.123,
    "longitude": 136.456,
    "altitude": 38000,
    "groundspeed": 510,
    "heading": 45,
    "timestamp": "2026-03-27T15:10:00Z"
  }
}
```

Returns `null` for `last_position` if flight is not yet airborne or has landed.

---

## Moshi Response Models (new file: `FlightAwareApi.kt`)

Replace `AviationStackApi.kt` entirely. Delete the old file.

```kotlin
interface FlightAwareApi {
    @GET("flights/{ident}")
    suspend fun getFlights(
        @Path("ident") ident: String,
        @Query("start") start: String? = null,   // ISO-8601 date, e.g. "2026-03-27"
        @Query("max_pages") maxPages: Int = 1
    ): Response<FlightAwareFlightsResponse>

    @GET("flights/{ident}/position")
    suspend fun getPosition(
        @Path("ident") ident: String
    ): Response<FlightAwarePositionResponse>
}

@JsonClass(generateAdapter = true)
data class FlightAwareFlightsResponse(
    @Json(name = "flights") val flights: List<FlightAwareFlight>?
)

@JsonClass(generateAdapter = true)
data class FlightAwareFlight(
    @Json(name = "ident_iata")         val identIata: String?,
    @Json(name = "status")             val status: String?,
    @Json(name = "departure_delay")    val departureDelay: Int?,      // seconds
    @Json(name = "arrival_delay")      val arrivalDelay: Int?,        // seconds
    @Json(name = "origin")             val origin: FlightAwareAirport?,
    @Json(name = "destination")        val destination: FlightAwareAirport?,
    @Json(name = "scheduled_out")      val scheduledOut: String?,     // ISO-8601
    @Json(name = "estimated_out")      val estimatedOut: String?,
    @Json(name = "actual_out")         val actualOut: String?,
    @Json(name = "scheduled_in")       val scheduledIn: String?,
    @Json(name = "estimated_in")       val estimatedIn: String?,
    @Json(name = "actual_in")          val actualIn: String?,
    @Json(name = "gate_origin")        val gateOrigin: String?,
    @Json(name = "gate_destination")   val gateDestination: String?,
    @Json(name = "aircraft_type")      val aircraftType: String?
)

@JsonClass(generateAdapter = true)
data class FlightAwareAirport(
    @Json(name = "code_iata") val codeIata: String?,
    @Json(name = "timezone")  val timezone: String?
)

@JsonClass(generateAdapter = true)
data class FlightAwarePositionResponse(
    @Json(name = "last_position") val lastPosition: FlightAwareLivePosition?
)

@JsonClass(generateAdapter = true)
data class FlightAwareLivePosition(
    @Json(name = "latitude")    val latitude: Double?,
    @Json(name = "longitude")   val longitude: Double?,
    @Json(name = "altitude")    val altitude: Int?,       // feet
    @Json(name = "groundspeed") val groundspeed: Int?,    // knots
    @Json(name = "heading")     val heading: Int?,
    @Json(name = "timestamp")   val timestamp: String?
)
```

**Note on delays:** FlightAware returns delays in **seconds**, not minutes. Convert: `departureDelay / 60`.
**Note on status:** Map `status` string to internal `FlightStatusEnum` by prefix:

```kotlin
enum class FlightStatusEnum { SCHEDULED, BOARDING, DEPARTED, EN_ROUTE, LANDED, CANCELLED, DIVERTED, UNKNOWN }

fun String?.toFlightStatusEnum(): FlightStatusEnum = when {
    this == null -> UNKNOWN
    startsWith("Scheduled") -> SCHEDULED
    startsWith("En Route") -> EN_ROUTE
    contains("Departed") -> DEPARTED
    contains("Arrived") || contains("Landed") -> LANDED
    contains("Cancelled") -> CANCELLED
    contains("Diverted") -> DIVERTED
    contains("Boarding") -> BOARDING
    else -> UNKNOWN
}
```

---

## NetworkModule Changes

```kotlin
// BEFORE
private const val BASE_URL = "http://api.aviationstack.com/"
fun provideAviationStackApi(retrofit: Retrofit): AviationStackApi

// AFTER
private const val BASE_URL = "https://aeroapi.flightaware.com/aeroapi/"

// Add x-apikey interceptor in OkHttpClient builder:
.addInterceptor { chain ->
    val request = chain.request().newBuilder()
        .addHeader("x-apikey", BuildConfig.FLIGHTAWARE_API_KEY)
        .build()
    chain.proceed(request)
}

// Replace provider:
fun provideFlightAwareApi(retrofit: Retrofit): FlightAwareApi
```

Remove `HttpLoggingInterceptor` from production builds (keep DEBUG only — already gated).

---

## BuildConfig Key Change

`app/build.gradle.kts`:
```kotlin
// REMOVE:
buildConfigField("String", "AVIATION_STACK_KEY", "\"${project.findProperty("AVIATION_STACK_KEY") ?: ""}\"")
// ADD:
buildConfigField("String", "FLIGHTAWARE_API_KEY", "\"${project.findProperty("FLIGHTAWARE_API_KEY") ?: ""}\"")
```

`local.properties` (user sets this, not committed):
```
FLIGHTAWARE_API_KEY=your_key_here
```

---

## network_security_config.xml Change

Delete the entire `<domain-config>` block — FlightAware uses HTTPS exclusively. The comment explains this:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- No cleartext exceptions needed. All API traffic uses HTTPS. -->
</network-security-config>
```

---

## FlightRouteServiceImpl Migration

The `FlightRouteService` interface signature is **unchanged** — only the implementation changes. This means `AddEditLogbookFlightViewModel` and all call sites are unaffected.

Key differences from AviationStack:
- Date filtering now works: pass `start = date.toString()` to narrow results to the correct day
- Delays in seconds → divide by 60 for minutes
- `aircraft_type` is now a direct field (no nested `aircraft.iata`)
- Timezone comes from `origin.timezone` / `destination.timezone` (same structure, different field names)

```kotlin
// New FlightRouteServiceImpl.lookupRoute():
val response = api.getFlights(
    ident = flightNumber,
    start = date.toString()   // "2026-03-27" — works on paid tier
)
val flight = response.body()?.flights?.firstOrNull { it.origin?.codeIata != null && it.destination?.codeIata != null }
    ?: return null
return FlightRoute(
    flightNumber = flightNumber,
    departureIata = flight.origin!!.codeIata!!,
    arrivalIata = flight.destination!!.codeIata!!,
    departureTimezone = flight.origin.timezone,
    arrivalTimezone = flight.destination.timezone,
    departureScheduledUtc = parseIsoToUtc(flight.scheduledOut),
    arrivalScheduledUtc = parseIsoToUtc(flight.scheduledIn),
    aircraftType = flight.aircraftType
)
```

---

## Live Tracking — Data Model

### FlightStatus entity (`FlightDatabase`, version 7 → 8)

```kotlin
@Entity(tableName = "flight_status", indices = [Index("logbookFlightId", unique = true)])
data class FlightStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logbookFlightId: Long,
    val flightNumber: String,
    val statusEnum: String,             // FlightStatusEnum.name
    val departureDelayMin: Int?,        // minutes (converted from seconds)
    val arrivalDelayMin: Int?,
    val departureGate: String?,
    val arrivalGate: String?,
    val estimatedDepartureUtc: Long?,
    val estimatedArrivalUtc: Long?,
    val actualDepartureUtc: Long?,
    val actualArrivalUtc: Long?,
    val liveLat: Double?,
    val liveLng: Double?,
    val liveAltitude: Int?,             // feet
    val liveSpeedKnots: Int?,
    val lastPolledAt: Long,
    val trackingEnabled: Boolean = true
)
```

No `pollCount` field — FlightAware is paid tier with no hard monthly cap. No request budget manager needed. Polling governed by WorkManager interval only.

### Migration 7 → 8

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS flight_status (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                logbookFlightId INTEGER NOT NULL UNIQUE,
                flightNumber TEXT NOT NULL,
                statusEnum TEXT NOT NULL,
                departureDelayMin INTEGER,
                arrivalDelayMin INTEGER,
                departureGate TEXT,
                arrivalGate TEXT,
                estimatedDepartureUtc INTEGER,
                estimatedArrivalUtc INTEGER,
                actualDepartureUtc INTEGER,
                actualArrivalUtc INTEGER,
                liveLat REAL,
                liveLng REAL,
                liveAltitude INTEGER,
                liveSpeedKnots INTEGER,
                lastPolledAt INTEGER NOT NULL,
                trackingEnabled INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
    }
}
```

---

## FlightStatusWorker

`PeriodicWorkRequest`, 15-minute interval, `NetworkType.CONNECTED` constraint.
Worker name: `"track_flight_{logbookFlightId}"` — cancellable by name.
Enqueued with `ExistingPeriodicWorkPolicy.KEEP` to prevent double-enqueue.

Worker logic:
1. Call `GET /flights/{ident}` with `start = departureDateLocal` (date from stored `departureTimeUtc` in departure timezone)
2. Pick best match: `origin.codeIata == logbook.departureCode` AND closest `scheduledOut` to stored `departureTimeUtc`
3. Call `GET /flights/{ident}/position` if status is `EN_ROUTE`
4. Update `FlightStatus` row
5. Diff against previous status → fire notification if changed
6. If `LANDED` or `CANCELLED` → cancel this worker after notification

---

## Status Display on FlightDetailScreen

Identical UI spec to the AviationStack version:
- "Track This Flight" button (shown when flightNumber non-empty + flight not historical)
- `LiveStatusCard` above `TimelineSection` when `FlightStatus` row exists
- Status badge colors: SCHEDULED=outline, EN_ROUTE=primaryContainer, LANDED=tertiaryContainer, CANCELLED/DIVERTED=errorContainer
- Live position dot on `RouteMapCanvas` when `liveLat`/`liveLng` non-null and non-zero

---

## Push Notifications

Same notification channels and triggers as the AviationStack spec:

| Trigger | Channel | Example |
|---|---|---|
| Status → `EN_ROUTE` (departed) | `flight_status` (HIGH) | "NH847 has departed HND" |
| Status → `LANDED` | `flight_status` (HIGH) | "NH847 has landed at NRT" |
| Status → `CANCELLED` | `flight_status` (HIGH) | "NH847 has been cancelled" |
| `departureDelayMin` increases ≥ 15 from last notified value | `flight_delay` (DEFAULT) | "NH847 delayed 45 min — new departure 16:20" |
| `departureGate` changes | `flight_delay` (DEFAULT) | "NH847 gate changed to 64" |

Android 13+ POST_NOTIFICATIONS permission requested on first "Track This Flight" tap.

---

## Files to Create / Delete / Edit

| Action | File |
|---|---|
| **Delete** | `data/network/AviationStackApi.kt` |
| **Create** | `data/network/FlightAwareApi.kt` — new Retrofit interface + all Moshi models |
| **Edit** | `data/network/FlightRouteServiceImpl.kt` — use `FlightAwareApi`, date param, new field names |
| **Edit** | `di/NetworkModule.kt` — HTTPS base URL, x-apikey interceptor, `FlightAwareApi` provider |
| **Edit** | `app/build.gradle.kts` — rename BuildConfig key |
| **Edit** | `res/xml/network_security_config.xml` — remove cleartext exception |
| **Create** | `data/local/entity/FlightStatus.kt` |
| **Create** | `data/local/dao/FlightStatusDao.kt` |
| **Create** | `data/repository/FlightStatusRepository.kt` |
| **Create** | `worker/FlightStatusWorker.kt` |
| **Edit** | `data/local/FlightDatabase.kt` — version 8, MIGRATION_7_8, FlightStatus entity + DAO |
| **Edit** | `di/DatabaseModule.kt` — provide FlightStatusDao + FlightStatusRepository |
| **Edit** | `FlightLogApplication.kt` — create notification channels |
| **Edit** | `ui/logbook/FlightDetailViewModel.kt` — inject repo, statusFlow, onTrackFlight() |
| **Edit** | `ui/logbook/FlightDetailScreen.kt` — LiveStatusCard, Track button, live position |
| **Edit** | `ui/logbook/RouteMapCanvas.kt` — optional livePosition dot |
| **Edit** | `AndroidManifest.xml` — POST_NOTIFICATIONS permission |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| `FLIGHTAWARE_API_KEY` is empty string in BuildConfig | API returns 401; worker logs error + returns `Result.failure()`, cancels itself, status card shows "API key not configured" |
| `GET /flights/{ident}` returns HTTP 401 | Worker: `Result.failure()`, cancel worker, surface error |
| `GET /flights/{ident}` returns HTTP 429 (rate limit) | Worker: `Result.retry()` — WorkManager backs off |
| `GET /flights/{ident}` returns empty `flights` list | No status update, no notification; worker continues polling |
| Response has multiple flights for same ident on same day | Pick by `origin.codeIata == logbook.departureCode` + closest `scheduledOut`; if still tie, pick `firstOrNull` |
| `departure_delay` / `arrival_delay` is null in response | Store as null; no delay notification |
| `departure_delay` decreases (e.g. 45→15 min) | No notification — only notify on increases ≥ 15 min from last notified value |
| `gate_origin` changes back to original value | Notify — any gate change triggers notification regardless of direction |
| `last_position` is null (flight not yet airborne) | `liveLat`/`liveLng` = null; no position dot on map; no crash |
| `last_position.latitude` = 0.0 and `longitude` = 0.0 (Null Island) | Guard: treat as null |
| Status jumps directly to `LANDED` without prior `EN_ROUTE` (missed polling window) | Fire "landed" notification only; do not retroactively fire "departed" |
| Worker enqueued twice for same flight (double-tap) | `ExistingPeriodicWorkPolicy.KEEP` — second enqueue is no-op |
| User disables tracking mid-flight | `trackingEnabled = false`, cancel worker by name; "Track" button reappears |
| Flight tracked across midnight (long-haul) | `start` date param derived from `departureTimeUtc` in departure timezone — correct date used regardless of local device timezone |
| POST_NOTIFICATIONS denied | Tracking + polling still works; no notifications sent; no crash |
| Old `AVIATION_STACK_KEY` present in `local.properties` | No compile error — old key simply unused; user must add `FLIGHTAWARE_API_KEY` |
| `network_security_config.xml` cleartext block removed but app still calls HTTP endpoint | Would be a new code bug — regression test: verify all API calls use HTTPS base URL |
