# Feature Spec: Feature 5 — Data Export (CSV / JSON)

**Date:** 2026-03-27
**Author:** Planner
**Status:** Ready for Designer + Developer

---

## Overview

Allow users to export their full logbook as a CSV or JSON file, shared via the Android system share sheet. This is a free feature — Flighty paywalls the equivalent behind a $50/year subscription. A user who discovers this differentiator is immediately incentivized to choose our app.

Export always covers the **full logbook** (not the currently-filtered view). Rationale: export is a backup/archive action. A user who just searched "NRT" and hits Export does not want only their NRT flights in their CSV — they want everything. The filter state is intentionally ignored for export.

The export pipeline:
1. User taps "Export" (overflow menu in LogbookScreen TopAppBar)
2. A bottom sheet asks: CSV or JSON?
3. ViewModel fetches all flights from `repository.getAll()`, serializes to the chosen format
4. File written to app's private cache directory (`context.cacheDir/exports/`)
5. FileProvider URI shared via `ACTION_SEND` intent
6. System share sheet appears; user picks email, Drive, Files, etc.

---

## User Stories

1. As a pilot, I want to export my logbook to CSV so I can open it in Excel or Google Sheets for analysis.
2. As a pilot, I want to export to JSON so I can import into another tool or script a custom analysis.
3. As a pilot, I want the export to always include all my flights, not just the ones currently visible after filtering.
4. As a pilot, I want a clear choice between CSV and JSON so I can pick whichever format my tool needs.
5. As a pilot, I want the share sheet to appear immediately after selecting the format, so I can send it to Google Drive, email, or Files in one tap.
6. As a pilot with an empty logbook, I want the Export option to be disabled or absent so I don't get confused by an empty file.

---

## Design Decision: Export Scope

**Full logbook only.** The filtered `flights` StateFlow in `LogbookViewModel` reflects the current search/filter state. Export ignores this and calls `repository.getAll()` (one-shot suspend call, not a Flow) directly in the export service.

If a future version wants filtered export, the spec can be extended. For now, "Export" = "full logbook backup."

**Implication for DAO:** A new one-shot suspend function is needed:

```kotlin
@Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc ASC")
suspend fun getAllOnce(): List<LogbookFlight>
```

Ordered ascending (oldest first) — the natural chronological order for a logbook backup file, matching what a pilot would expect when opening the CSV.

---

## CSV Format Specification

**Filename:** `flight-log-YYYY-MM-DD.csv` (date = export date in local timezone)

**Encoding:** UTF-8 with BOM (`\uFEFF` prefix) — required for Excel on Windows to auto-detect UTF-8 correctly (Japanese airport names, notes with non-ASCII characters).

**Header row:**

```
date,flight_number,departure,arrival,departure_time_local,arrival_time_local,duration_minutes,distance_nm,aircraft_type,seat_class,seat_number,notes
```

**Field definitions:**

| CSV Column | Source field | Format | Notes |
|---|---|---|---|
| `date` | `departureTimeUtc` + `departureTimezone` | `YYYY-MM-DD` | Local date at departure airport; falls back to UTC date if timezone unknown |
| `flight_number` | `flightNumber` | raw string | Empty string if blank |
| `departure` | `departureCode` | raw string | 3-letter IATA |
| `arrival` | `arrivalCode` | raw string | 3-letter IATA |
| `departure_time_local` | `departureTimeUtc` + `departureTimezone` | `HH:mm` | Local time at departure airport; UTC if timezone unknown |
| `arrival_time_local` | `arrivalTimeUtc` + `arrivalTimezone` | `HH:mm` | Local time at arrival airport; empty string if `arrivalTimeUtc` is null |
| `duration_minutes` | `arrivalTimeUtc - departureTimeUtc` | integer | Empty string if `arrivalTimeUtc` is null or <= departureTimeUtc |
| `distance_nm` | `distanceNm` | integer | Empty string if null |
| `aircraft_type` | `aircraftType` | raw string | Empty string if blank |
| `seat_class` | `seatClass` | raw string | Empty string if blank |
| `seat_number` | `seatNumber` | raw string | Empty string if blank |
| `notes` | `notes` | RFC 4180 quoted | Wrap in double-quotes if contains comma, newline, or double-quote; escape internal double-quotes as `""` |

**RFC 4180 compliance:** All string fields must be quoted if they contain a comma, double-quote, or newline. The `notes` field is the primary risk — always apply quoting logic to it. Other fields (airport codes, flight numbers) are safe to write unquoted, but quoting them all is also acceptable.

**Example rows:**

```csv
date,flight_number,departure,arrival,departure_time_local,arrival_time_local,duration_minutes,distance_nm,aircraft_type,seat_class,seat_number,notes
2025-03-15,JL5,NRT,JFK,11:30,10:45,780,6732,Boeing 777-300ER,Business,4A,"Great flight, Mt. Fuji visible"
2025-04-02,AA100,JFK,LHR,22:15,10:30,435,3450,Boeing 777-200,Economy,32B,
2025-04-10,,,NRT,HND,09:00,09:35,35,180,,,
```

---

## JSON Format Specification

**Filename:** `flight-log-YYYY-MM-DD.json`

**Encoding:** UTF-8, no BOM (standard for JSON)

**Structure:**

```json
{
  "exported_at": "2026-03-27T14:30:00Z",
  "flight_count": 47,
  "flights": [
    {
      "id": 1,
      "date": "2025-03-15",
      "flight_number": "JL5",
      "departure": "NRT",
      "arrival": "JFK",
      "departure_time_utc": 1742041800000,
      "arrival_time_utc": 1742088300000,
      "departure_time_local": "11:30",
      "arrival_time_local": "10:45",
      "departure_timezone": "Asia/Tokyo",
      "arrival_timezone": "America/New_York",
      "duration_minutes": 780,
      "distance_nm": 6732,
      "aircraft_type": "Boeing 777-300ER",
      "seat_class": "Business",
      "seat_number": "4A",
      "notes": "Great flight, Mt. Fuji visible"
    }
  ]
}
```

**JSON includes the raw UTC epoch millis** (`departure_time_utc`, `arrival_time_utc`) in addition to the formatted local strings. This allows round-trip import in a future feature without loss of precision. Fields that are null in the database are omitted from JSON output (not serialized as `null`) to keep the output clean.

**Serialization approach:** Use Moshi, which is already in the project (used by AviationStack network layer). Define a `LogbookFlightExport` data class (separate from the Room entity) to control exact JSON field names and null omission, rather than annotating the entity directly. Moshi's `@Json(name = ...)` + `@JsonClass(generateAdapter = true)` provides clean control.

---

## Service Design

### New class: `ExportService`

A plain class (not a ViewModel, not a Hilt singleton — inject as needed) responsible for:
1. Accepting a `List<LogbookFlight>` and a format choice
2. Serializing to a `String`
3. Writing to a `File` in the app's cache directory
4. Returning the `File`

```kotlin
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToCsv(flights: List<LogbookFlight>): File
    suspend fun exportToJson(flights: List<LogbookFlight>, moshi: Moshi): File
    private fun fileNameFor(extension: String): String  // e.g. "flight-log-2026-03-27.csv"
    private fun cacheExportDir(): File                  // context.cacheDir / "exports"
}
```

Both `exportToCsv` and `exportToJson` are `suspend` functions that run on `Dispatchers.IO`. The caller (ViewModel) launches them in `viewModelScope`.

### New ViewModel: `ExportViewModel`

Separate ViewModel from `LogbookViewModel` — export logic should not live in an already-large ViewModel. Accessed from `LogbookScreen` via a second `hiltViewModel()` call scoped to the same screen.

```kotlin
sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Ready(val uri: Uri) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val exportService: ExportService,
    private val moshi: Moshi
) : ViewModel() {
    val exportState: StateFlow<ExportState>
    fun exportCsv()
    fun exportJson()
    fun clearExportState()
}
```

`Moshi` is already provided as a singleton in `NetworkModule` — inject it directly.

### FileProvider setup

**AndroidManifest.xml** — add inside `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

**`res/xml/file_provider_paths.xml`** (new file):

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="exports/" />
</paths>
```

**URI generation in ExportViewModel:**

```kotlin
val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    exportedFile
)
```

### Share intent

Triggered from `LogbookScreen` when `exportState` becomes `ExportState.Ready`:

```kotlin
LaunchedEffect(exportState) {
    if (exportState is ExportState.Ready) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isCsv) "text/csv" else "application/json"
            putExtra(Intent.EXTRA_STREAM, exportState.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share flight log"))
        viewModel.clearExportState()
    }
}
```

---

## Implementation Steps

### Step 1: Add `getAllOnce()` to `LogbookFlightDao`

```kotlin
@Query("SELECT * FROM logbook_flights ORDER BY departureTimeUtc ASC")
suspend fun getAllOnce(): List<LogbookFlight>
```

Forward from `LogbookRepository`:

```kotlin
suspend fun getAllOnce(): List<LogbookFlight> = logbookFlightDao.getAllOnce()
```

### Step 2: Add FileProvider to `AndroidManifest.xml` + create `file_provider_paths.xml`

Create `app/src/main/res/xml/file_provider_paths.xml` with the `<cache-path>` entry above.
Add the `<provider>` block to `AndroidManifest.xml`.

### Step 3: Define `LogbookFlightExport` data class

A serialization-only data class in a new file `data/export/LogbookFlightExport.kt`:

```kotlin
@JsonClass(generateAdapter = true)
data class LogbookFlightExport(
    val id: Long,
    val date: String,
    @Json(name = "flight_number") val flightNumber: String?,
    val departure: String,
    val arrival: String,
    @Json(name = "departure_time_utc") val departureTimeUtc: Long,
    @Json(name = "arrival_time_utc") val arrivalTimeUtc: Long?,
    @Json(name = "departure_time_local") val departureTimeLocal: String,
    @Json(name = "arrival_time_local") val arrivalTimeLocal: String?,
    @Json(name = "departure_timezone") val departureTimezone: String?,
    @Json(name = "arrival_timezone") val arrivalTimezone: String?,
    @Json(name = "duration_minutes") val durationMinutes: Long?,
    @Json(name = "distance_nm") val distanceNm: Int?,
    @Json(name = "aircraft_type") val aircraftType: String?,
    @Json(name = "seat_class") val seatClass: String?,
    @Json(name = "seat_number") val seatNumber: String?,
    val notes: String?
)
```

Null fields are omitted in JSON output via Moshi's default behavior with `@JsonClass(generateAdapter = true)`.

Define a `LogbookFlightExportWrapper` for the top-level JSON object:

```kotlin
@JsonClass(generateAdapter = true)
data class LogbookFlightExportWrapper(
    @Json(name = "exported_at") val exportedAt: String,
    @Json(name = "flight_count") val flightCount: Int,
    val flights: List<LogbookFlightExport>
)
```

### Step 4: Implement `ExportService`

New file: `data/export/ExportService.kt`

Key implementation notes:
- Use `Dispatchers.IO` for all file operations (wrap with `withContext(Dispatchers.IO)`)
- CSV: use `StringBuilder` + manual RFC 4180 quoting. Do NOT use a third-party CSV library — the format is simple enough and avoids a new dependency.
- JSON: use `Moshi.adapter(LogbookFlightExportWrapper::class.java).toJson(wrapper)`
- File written to `context.cacheDir/exports/flight-log-YYYY-MM-DD.csv` (or `.json`)
- If the exports directory does not exist, create it: `dir.mkdirs()`
- Overwrite any existing file with the same name (same-day re-export)

**Timezone formatting helper** (reuse existing `TimeFormatting.kt` pattern):

```kotlin
private fun formatLocalDate(utcMillis: Long, timezone: String?): String {
    val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    return Instant.ofEpochMilli(utcMillis).atZone(zone)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)  // YYYY-MM-DD
}

private fun formatLocalTime(utcMillis: Long, timezone: String?): String {
    val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    return Instant.ofEpochMilli(utcMillis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
```

**RFC 4180 CSV quoting helper:**

```kotlin
private fun csvQuote(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}
```

### Step 5: Implement `ExportViewModel`

New file: `ui/logbook/ExportViewModel.kt`

The ViewModel:
1. Exposes `exportState: StateFlow<ExportState>`
2. `exportCsv()` / `exportJson()` each: set Loading → call `repository.getAllOnce()` → call `exportService` → generate URI via FileProvider → set Ready(uri)
3. On exception: set Error("Export failed. Please try again.")
4. `clearExportState()` resets to Idle

### Step 6: Update `LogbookScreen` UI

**Export button placement:** Add a `MoreVert` (three-dot) overflow `IconButton` to the TopAppBar `actions` block, after the existing Sort button. The overflow dropdown contains:

- "Export as CSV"
- "Export as JSON"

Both items disabled (greyed out) when `flightCount == 0` (checked against the _total_ logbook count, not the filtered count — use a new `totalFlightCount` from `repository.getCount()` directly or pass it from a separate StateFlow).

**Loading indicator:** When `exportState is ExportState.Loading`, show a `CircularProgressIndicator` in place of the MoreVert icon (or overlay it). The overflow menu is not tappable during loading.

**Share trigger:** `LaunchedEffect(exportState)` as described in the Service Design section.

**Error handling:** When `exportState is ExportState.Error`, show the error message via `Snackbar`. Reuse the existing `snackbarHostState`.

**Note on `flightCount` for the disabled state:** After Feature 4, `flightCount` in `LogbookViewModel` reflects the filtered count. For the export disabled-state check, we need the _total_ unfiltered count. Options:
- Add `val totalFlightCount: StateFlow<Int> = repository.getCount().stateIn(...)` to `LogbookViewModel` (single extra line)
- Or use `allFlights.map { it.size }` which is already computed internally

Recommended: expose `totalFlightCount` separately in `LogbookViewModel` for clarity. Use it in `LogbookScreen` solely to disable/enable the export buttons.

### Step 7: Register `ExportService` with Hilt

Add `@Singleton` + `@Inject constructor` to `ExportService`. Hilt will inject `@ApplicationContext context` and `Moshi` automatically since both are already provided.

### Step 8: Write unit tests

Create `ExportServiceTest` covering the edge cases below. The CSV and JSON format tests are pure string/logic tests — no Android instrumentation needed.

---

## Edge Cases to Test

### E1: Empty logbook — export buttons disabled
- Precondition: `totalFlightCount == 0`
- Expected: "Export as CSV" and "Export as JSON" menu items are disabled (greyed out, not clickable)
- No file is written, no share sheet appears

### E2: Single flight export — CSV format correct
- Input: 1 flight, all fields populated
- Expected: CSV has 2 lines (header + 1 data row), correct column count (12), no trailing comma

### E3: Notes field with comma — RFC 4180 quoting
- Input: `notes = "Good flight, smooth landing"`
- Expected CSV cell: `"Good flight, smooth landing"` (wrapped in double-quotes)

### E4: Notes field with double-quote — RFC 4180 escaping
- Input: `notes = "Pilot said \"smooth air\""`
- Expected CSV cell: `"Pilot said ""smooth air"""`

### E5: Notes field with newline — RFC 4180 quoting
- Input: `notes = "First leg\nSecond leg"`
- Expected CSV cell: `"First leg\nSecond leg"` (quoted, newline preserved inside quotes)

### E6: Notes field is empty string
- Input: `notes = ""`
- Expected CSV cell: empty (no quotes needed)

### E7: arrivalTimeUtc is null
- Input: flight with `arrivalTimeUtc = null`
- Expected CSV: `arrival_time_local` column = empty string, `duration_minutes` column = empty string
- Expected JSON: `arrival_time_utc`, `arrival_time_local`, `duration_minutes` keys omitted entirely

### E8: distanceNm is null
- Input: flight with `distanceNm = null`
- Expected CSV: `distance_nm` column = empty string
- Expected JSON: `distance_nm` key omitted

### E9: departureTimezone is null — falls back to UTC
- Input: `departureTimezone = null`, `departureTimeUtc` = 2025-03-15 02:30:00 UTC
- Expected CSV `date`: `2025-03-15` (UTC date)
- Expected CSV `departure_time_local`: `02:30` (UTC time)

### E10: Timezone offset shifts date across midnight
- Input: `departureTimezone = "America/Los_Angeles"` (UTC-8), `departureTimeUtc` = 2025-03-15 07:00:00 UTC
- Expected CSV `date`: `2025-03-14` (local date = March 14 at 23:00 PST)
- Expected CSV `departure_time_local`: `23:00`

### E11: Overnight flight — arrival next day
- Input: departure 2025-03-15 22:00 local, arrival 2025-03-16 06:00 local, `duration_minutes` = 480
- Expected CSV: `departure_time_local = 22:00`, `arrival_time_local = 06:00`, `duration_minutes = 480`
- Note: arrival date is not shown in CSV (date column = departure date only); duration bridges the days correctly

### E12: Very long notes field (boundary)
- Input: `notes` = 10,000 character string with no commas or quotes
- Expected: CSV cell written without quotes (no quoting trigger), no truncation, file written successfully

### E13: Logbook with 1,000 flights — export completes without ANR
- Expected: `exportToCsv` / `exportToJson` runs on `Dispatchers.IO`, does not block main thread
- Test: measure wall-clock time in unit test with fake 1,000-entry list; should complete < 1 second

### E14: Same-day re-export overwrites previous file
- Action: user exports CSV at 10:00, exports again at 14:00 (same day)
- Expected: second export overwrites the first file (same filename); no stale file accumulation
- Cache directory does not grow unboundedly from repeated exports

### E15: FileProvider URI is valid and grantable
- Expected: URI generated via `FileProvider.getUriForFile(...)` uses the `${applicationId}.fileprovider` authority; `FLAG_GRANT_READ_URI_PERMISSION` is set on the intent; receiving apps can open the file

### E16: Share sheet cancelled by user
- Action: share sheet appears, user presses Back without selecting a destination
- Expected: `clearExportState()` has already been called (triggered by `LaunchedEffect` after Ready state); UI returns to Idle cleanly; no stale state on next open

### E17: Export during active filter
- Precondition: filter shows 5 of 50 flights
- Action: user exports CSV
- Expected: CSV contains all 50 flights (export always uses `getAllOnce()`, not filtered list)
- CSV row count = 51 (header + 50 data rows)

### E18: JSON null field omission
- Input: flight with `seatNumber = ""`, `aircraftType = ""`, `distanceNm = null`
- Expected JSON object: `seat_number`, `aircraft_type`, `distance_nm` keys absent (not `"seat_number": null`)
- Note: Moshi omits null fields with `@JsonClass(generateAdapter = true)` only if the Kotlin type is nullable AND the value is null. Empty strings (`""`) are NOT null and WILL appear in JSON. The `LogbookFlightExport` mapping must convert `""` to `null` for string fields before serialization.

### E19: Exported filename includes local date, not UTC date
- Action: user exports at 2026-03-27 23:00 local (= 2026-03-28 00:00 UTC for UTC+1 timezone)
- Expected filename: `flight-log-2026-03-27.csv` (local date), not `flight-log-2026-03-28.csv`
- Use `LocalDate.now()` (system default timezone) for the filename, not UTC

### E20: Unicode in notes — UTF-8 BOM in CSV
- Input: `notes = "富士山が見えた"` (Japanese)
- Expected: CSV file begins with UTF-8 BOM (`EF BB BF` byte sequence), content correctly encoded
- Expected: Excel on Windows opens the file and displays Japanese characters correctly without manual encoding selection

### E21: Export button state during loading
- Action: user taps "Export as CSV"; `ExportState.Loading` is set
- Expected: overflow menu is dismissed/closed; loading indicator replaces the MoreVert icon; user cannot trigger a second export simultaneously

### E22: Concurrent export not possible
- Action: `exportCsv()` is called while a previous export Job is still running
- Expected: second call either no-ops (if `isLoading` guard is checked in ViewModel) or cancels-and-replaces the first job
- Recommended: no-op guard (`if (exportState.value is ExportState.Loading) return`) is simplest and safest

### E23: Cache directory creation on first export
- Precondition: `context.cacheDir/exports/` does not exist (fresh install)
- Expected: `ExportService.cacheExportDir()` calls `dir.mkdirs()`; directory created; file written successfully; no `FileNotFoundException`

### E24: JSON `exported_at` timestamp format
- Expected: ISO 8601 UTC format: `"2026-03-27T14:30:00Z"` — parseable by standard tools without ambiguity

---

## Dependencies

| Item | Status | Notes |
|---|---|---|
| `LogbookFlightDao.getAllOnce()` | New | Simple suspend query, no migration |
| `LogbookRepository.getAllOnce()` | New | Forward only |
| `LogbookFlightExport` data class | New | Serialization DTO, separate from Room entity |
| `ExportService` | New | `data/export/ExportService.kt` |
| `ExportViewModel` | New | `ui/logbook/ExportViewModel.kt` |
| `ExportState` sealed class | New | Defined in ExportViewModel file |
| `AndroidManifest.xml` FileProvider | New | One `<provider>` block |
| `res/xml/file_provider_paths.xml` | New | One new XML resource file |
| `Moshi` | Exists | Already provided as singleton in `NetworkModule` |
| `androidx.core:core-ktx` | Exists | Provides `FileProvider` |
| `LogbookViewModel.totalFlightCount` | New field | Separate from filtered `flightCount`; needed for export disabled state |
| Room migration | Not needed | No entity changes |
| New Gradle dependency | Not needed | Moshi, FileProvider, coroutines all present |

---

## Risks

1. **Empty string vs null in JSON (E18):** The `LogbookFlight` entity uses empty strings (`""`) as defaults for optional string fields, not nulls. Moshi omits `null` but will include `""`. The `LogbookFlightExport` DTO must map `""` to `null` explicitly for `flightNumber`, `aircraftType`, `seatClass`, `seatNumber`, `notes`. This mapping is easy to miss — the Developer must handle it carefully, and E18 must be a test case.

2. **FileProvider authority collision:** The authority `${applicationId}.fileprovider` must be unique. Since we control the manifest and this is the only FileProvider in the app, there is no collision risk. Verify there is no existing `<provider>` with a conflicting authority before shipping.

3. **Cache directory eviction:** Android may evict `cacheDir` contents under storage pressure. The exported file only needs to survive long enough for the share sheet interaction (seconds). This is acceptable — the cache directory is the correct location for ephemeral shared files. Do NOT write to `filesDir` (not shareable via FileProvider without additional config) or external storage (requires permission on older APIs).

4. **MIME type compatibility:** `text/csv` is well-supported. Some share targets (e.g. Gmail) may not recognize `application/json` as a shareable file type — they may not show it as an attachment option. If this proves problematic, `text/plain` for JSON is an acceptable fallback. The Developer should test both on a real device.

5. **Large logbook memory spike:** `getAllOnce()` loads all flights into memory at once. For a typical personal logbook (< 10,000 flights), this is fine (each `LogbookFlight` is ~500 bytes; 10,000 flights = ~5MB). If future concerns arise, streaming output can be added later.
