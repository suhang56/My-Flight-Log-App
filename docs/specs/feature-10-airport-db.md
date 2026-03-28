# Feature 10 — Offline Airport Database

**Scope:** Medium | **Sprint:** 1 (parallel with F12) | **Unlocks:** F11 (Live Tracking), F14 (Trip Grouping)

---

## What Exists (do not duplicate)

- `AirportCoordinatesMap` — ~200 airports, `coordinatesFor(iata)`, `distanceNm(dep, arr)` + Haversine
- `AirportTimezoneMap` — ~200 airports, `timezoneFor(iata)`
- `AirportNameMap` — US-heavy city-name → IATA lookup, used by `FlightEventParser`
- `FlightDatabase` at Room version 6, two entities (`CalendarFlight`, `LogbookFlight`)
- `DatabaseModule.kt` handles Hilt provision of `FlightDatabase`

---

## Approach: Separate Pre-populated Room Database

**Do NOT add Airport to `FlightDatabase`.** Use a separate `AirportDatabase` provisioned via `createFromAsset("airports.db")`. This avoids entangling the airport data migration lifecycle with user data migrations. The airports DB is read-only and never migrated — it is replaced wholesale on app update by shipping a new asset.

---

## Data Model

### Airport entity (in `AirportDatabase`)

```kotlin
@Entity(tableName = "airports")
data class Airport(
    @PrimaryKey val iata: String,       // 3-letter IATA, uppercase
    val icao: String?,                  // 4-letter ICAO, nullable (some airports IATA-only)
    val name: String,                   // full name e.g. "Narita International Airport"
    val city: String,                   // e.g. "Tokyo"
    val country: String,                // ISO 3166-1 alpha-2 e.g. "JP"
    val lat: Double,
    val lng: Double,
    val timezone: String?               // IANA timezone e.g. "Asia/Tokyo", nullable
)
```

### AirportDatabase

```kotlin
@Database(entities = [Airport::class], version = 1, exportSchema = false)
abstract class AirportDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao
}
```

Provisioned in `DatabaseModule` via:
```kotlin
Room.databaseBuilder(context, AirportDatabase::class.java, "airports.db")
    .createFromAsset("airports.db")
    .fallbackToDestructiveMigration()   // safe: read-only reference data
    .build()
```

### AirportDao

```kotlin
@Dao
interface AirportDao {
    @Query("SELECT * FROM airports WHERE iata = :iata LIMIT 1")
    suspend fun getByIata(iata: String): Airport?

    @Query("SELECT * FROM airports WHERE icao = :icao LIMIT 1")
    suspend fun getByIcao(icao: String): Airport?

    // For autocomplete: match name or city, IATA-only airports, limit results
    @Query("""
        SELECT * FROM airports
        WHERE (name LIKE '%' || :query || '%' OR city LIKE '%' || :query || '%' OR iata LIKE :query || '%')
        AND iata != ''
        ORDER BY iata ASC
        LIMIT 20
    """)
    suspend fun search(query: String): List<Airport>
}
```

---

## Asset Preparation (developer one-time task)

1. Download `airports.csv` from OurAirports (public domain, ~7,500 IATA airports with coordinates + timezone).
2. Filter: keep rows where `iata_code` is non-empty.
3. Convert to SQLite using a script (Python `sqlite3` or DB Browser for SQLite).
4. Place resulting `airports.db` in `app/src/main/assets/airports.db` (~2–3 MB).

---

## AirportRepository

New `@Singleton` repository wrapping `AirportDao` with static-map fallback:

```kotlin
suspend fun getByIata(iata: String): Airport? =
    airportDao.getByIata(iata) ?: staticFallback(iata)
```

`staticFallback()` constructs a synthetic `Airport` from the three existing static maps — ensures zero regression if a code is missing from the OurAirports dataset (e.g. private terminals, edge cases).

---

## Migration of Existing Usages

Replace static map calls with `AirportRepository` calls. All three static maps stay in place as fallback only — do not delete them.

| Current call site | Replacement |
|---|---|
| `AirportCoordinatesMap.coordinatesFor(iata)` | `airportRepo.getByIata(iata)?.let { LatLng(it.lat, it.lng) }` |
| `AirportCoordinatesMap.distanceNm(dep, arr)` | `airportRepo.distanceNm(dep, arr)` (move Haversine to repo) |
| `AirportTimezoneMap.timezoneFor(iata)` | `airportRepo.getByIata(iata)?.timezone` |
| `AirportNameMap` city-name lookup in `FlightEventParser` | `airportRepo.search(cityName).firstOrNull()?.iata` |
| Airport name display in Statistics, Detail screens | `airportRepo.getByIata(iata)?.name` |

Affected files: `FlightEventParser.kt`, `LogbookRepository.kt`, `StatisticsViewModel.kt`, `FlightDetailViewModel.kt`, `AddEditLogbookFlightViewModel.kt`, `RouteMapCanvas.kt`.

---

## Airport Autocomplete on Add/Edit Flight Form

- `AddEditLogbookFlightViewModel` gains `searchAirports(query: String): StateFlow<List<Airport>>`
- Debounce 300ms, min 2 chars, max 20 results
- UI: `ExposedDropdownMenuBox` on departure/arrival fields showing `"${airport.iata} — ${airport.name}, ${airport.city}"` rows
- Selecting a result fills the IATA field and triggers distance recalculation

---

## Files to Create / Edit

| Action | File |
|---|---|
| Create | `data/local/entity/Airport.kt` |
| Create | `data/local/AirportDatabase.kt` |
| Create | `data/local/dao/AirportDao.kt` |
| Create | `data/repository/AirportRepository.kt` |
| Create | `app/src/main/assets/airports.db` (binary asset) |
| Edit | `di/DatabaseModule.kt` — add `AirportDatabase` + `AirportDao` + `AirportRepository` provisions |
| Edit | `AddEditLogbookFlightViewModel.kt` — add `searchAirports()`, inject `AirportRepository` |
| Edit | `AddEditLogbookFlightScreen.kt` — add `ExposedDropdownMenuBox` autocomplete |
| Edit | `FlightEventParser.kt` — replace `AirportNameMap` with repo (suspend, inject) |
| Edit | `LogbookRepository.kt` — replace `AirportCoordinatesMap.distanceNm()` |
| Edit | `StatisticsViewModel.kt`, `FlightDetailViewModel.kt` — replace name/timezone lookups |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| IATA code not in OurAirports dataset (e.g. private terminal "XYZ") | Static map fallback returns coordinates/timezone if known; `null` otherwise — no crash |
| `airports.db` asset missing from APK (build error) | Room throws at DB init; caught by Hilt and surfaced as startup error — fail fast |
| Search query is empty string | Return empty list, do not query DB |
| Search query is 1 character | Return empty list (min 2 chars) |
| Search query matches 0 airports | Return empty list, show "No airports found" in dropdown |
| IATA code passed in lowercase | `getByIata()` normalizes to uppercase before query |
| `distanceNm()` called with one null coordinate airport | Returns `null`, UI shows "— nm" (existing behavior preserved) |
| Airport with null timezone | Falls back to `AirportTimezoneMap` static map, then UTC if still null |
| Autocomplete: user types fast (debounce) | Only the last query after 300ms fires; no stale dropdown results |
| Autocomplete: user selects airport, then clears field | IATA field clears, distance resets to null |
| DB asset replaced in app update (new airports.db version) | `fallbackToDestructiveMigration()` drops and recreates the read-only DB cleanly |
| `FlightEventParser` city-name lookup for an unlisted city | Falls back to existing `AirportNameMap` static entries, no regression |
| Concurrent `getByIata` calls from multiple ViewModels | Room handles concurrent reads safely on its internal thread pool |
