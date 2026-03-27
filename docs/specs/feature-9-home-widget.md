# Feature 9 — Home Screen Widget

## Overview

Add an Android home screen widget that displays quick flight stats — total flights, total distance, and most recent flight route + date. Tapping the widget opens the app to the Logbook screen. Built with the Jetpack Glance API (Compose-style widget DSL). Data is refreshed periodically by WorkManager.

## Goals

- Power-user stickiness: stats visible without opening the app.
- Two size variants: small (2x2 cells) and medium (4x2 cells).
- Periodic auto-refresh every 6 hours via WorkManager (already a project dependency).
- Tap opens app to Logbook screen.
- Graceful empty state when logbook has zero flights.
- No new data — all three displayed values come from existing DAO queries.

## Non-Goals

- Real-time/live widget updates on every database change (too battery-expensive).
- Per-widget configuration (e.g., choosing which stat to show).
- iOS widget or lock-screen widget.
- Showing the route map canvas inside the widget.

---

## Dependencies

**New dependencies required.** Glance is NOT included in the Compose BOM.

### `gradle/libs.versions.toml` additions

```toml
[versions]
glance = "1.1.1"

[libraries]
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

### `app/build.gradle.kts` additions

```kotlin
// Glance (home screen widget)
implementation(libs.androidx.glance.appwidget)
implementation(libs.androidx.glance.material3)
```

These two are the only new dependencies. WorkManager (`libs.androidx.work.runtime.ktx`) and Hilt WorkManager (`libs.androidx.hilt.work`) are already present.

---

## Architecture

### Data Flow

```
WorkManager (WidgetRefreshWorker, periodic 6h)
  └─ LogbookRepository (suspend one-shot reads via Flow.first())
       ├─ getCount()           → Flow<Int>      → .first() → Int
       ├─ getTotalDistanceNm() → Flow<Int>      → .first() → Int
       └─ getAll()             → Flow<List<..>> → .first().firstOrNull() → LogbookFlight?
  └─ WidgetDataStore (DataStore<Preferences> — persists last-known data across process deaths)
       └─ FlightLogWidget (GlanceAppWidget — reads from DataStore, renders UI)
```

**Why DataStore (Preferences) and not direct DB reads in the widget?**
Glance composables run in a `RemoteViews` context. They cannot call suspend functions directly — they read from a snapshot of data persisted between refreshes. DataStore<Preferences> is the standard pattern for passing widget data from a Worker to a Glance composable.

### Components

| Component | Location | Role |
|---|---|---|
| `FlightLogWidget` | `ui/widget/FlightLogWidget.kt` | `GlanceAppWidget` subclass — renders the widget UI |
| `FlightLogWidgetReceiver` | `ui/widget/FlightLogWidget.kt` | `GlanceAppWidgetReceiver` — Android entry point, triggers initial data load |
| `WidgetRefreshWorker` | `ui/widget/WidgetRefreshWorker.kt` | `CoroutineWorker` + Hilt injection — reads DB, writes to DataStore, calls `update()` |
| `WidgetDataKeys` | `ui/widget/WidgetDataKeys.kt` | `Preferences.Key<T>` constants for DataStore |
| `res/xml/widget_info_small.xml` | widget metadata | `appwidget-provider` for 2x2 variant |
| `res/xml/widget_info_medium.xml` | widget metadata | `appwidget-provider` for 4x2 variant |
| `res/layout/widget_preview_small.xml` | preview layout | Static XML preview shown in widget picker |
| `res/layout/widget_preview_medium.xml` | preview layout | Static XML preview shown in widget picker |

No new ViewModel. No changes to `NavGraph.kt`, `LogbookRepository`, or any DAO.

---

## Widget Sizes

### Small — 2x2 cells (approx 110x110dp minimum)

```
┌─────────────────────┐
│  MY FLIGHT LOG      │
│                     │
│  247 flights        │
│  143,210 nm         │
└─────────────────────┘
```

Shows: app name label, total flight count, total distance.

### Medium — 4x2 cells (approx 250x110dp minimum)

```
┌──────────────────────────────────────────────┐
│  MY FLIGHT LOG                   ✈           │
│                                              │
│  247 flights    143,210 nm                   │
│  Last: PEK→SHA  Mar 22, 2026                 │
└──────────────────────────────────────────────┘
```

Shows: everything in small + most recent flight route + departure date.

**Size declaration in `appwidget-provider`:**
- Small: `minWidth="110dp"`, `minHeight="110dp"`, `targetCellWidth="2"`, `targetCellHeight="2"`
- Medium: `minWidth="250dp"`, `minHeight="110dp"`, `targetCellWidth="4"`, `targetCellHeight="2"`

`targetCellWidth`/`targetCellHeight` require `minSdk = 26` (already satisfied) but are only respected on Android 12+ (API 31). On API 26-30, the system uses `minWidth`/`minHeight`.

---

## DataStore Keys (`WidgetDataKeys.kt`)

```kotlin
object WidgetDataKeys {
    val FLIGHT_COUNT     = intPreferencesKey("widget_flight_count")
    val TOTAL_DISTANCE_NM = intPreferencesKey("widget_total_distance_nm")
    val LAST_FLIGHT_DEP  = stringPreferencesKey("widget_last_dep")
    val LAST_FLIGHT_ARR  = stringPreferencesKey("widget_last_arr")
    val LAST_FLIGHT_DATE = longPreferencesKey("widget_last_flight_date_utc")
    val LAST_UPDATED_MS  = longPreferencesKey("widget_last_updated_ms")
}
```

`LAST_FLIGHT_DATE` stores `departureTimeUtc` as a Long epoch millis. The widget formats it to a locale-aware short date string using `java.time.Instant` + `ZoneId.systemDefault()` at render time (not stored pre-formatted, because the locale/timezone could change between refresh and render).

When the logbook is empty: `FLIGHT_COUNT` = 0, `LAST_FLIGHT_DEP` absent (key not present).

---

## Widget UI (`FlightLogWidget.kt`)

### GlanceAppWidget

```kotlin
class FlightLogWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),  // small
            DpSize(250.dp, 110.dp),  // medium
        )
    )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        val size = LocalSize.current

        val flightCount = prefs[WidgetDataKeys.FLIGHT_COUNT] ?: 0
        val distanceNm  = prefs[WidgetDataKeys.TOTAL_DISTANCE_NM] ?: 0
        val lastDep     = prefs[WidgetDataKeys.LAST_FLIGHT_DEP]
        val lastArr     = prefs[WidgetDataKeys.LAST_FLIGHT_ARR]
        val lastDateMs  = prefs[WidgetDataKeys.LAST_FLIGHT_DATE]

        val openAppAction = actionStartActivity<MainActivity>()  // tap opens app

        if (size.width >= 250.dp) {
            MediumWidgetContent(flightCount, distanceNm, lastDep, lastArr, lastDateMs, openAppAction)
        } else {
            SmallWidgetContent(flightCount, distanceNm, openAppAction)
        }
    }
}
```

`SizeMode.Responsive` renders the appropriate layout based on the actual widget size allocated by the launcher. The `250.dp` threshold matches the medium `minWidth`.

### Tap Action

`actionStartActivity<MainActivity>()` launches `MainActivity` (the app's single activity). The app will start at the Logbook screen (the default nav destination). No deep link is needed — the default back stack is sufficient.

### GlanceAppWidgetReceiver

```kotlin
@AndroidEntryPoint  // NOT applicable — Receiver cannot use @AndroidEntryPoint with Glance
class FlightLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FlightLogWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Enqueue a one-time WorkManager refresh so data is populated on first add
        WidgetRefreshWorker.enqueueOnce(context)
    }
}
```

Note: `GlanceAppWidgetReceiver` does NOT support `@AndroidEntryPoint`. Hilt injection in the receiver is not available. All Hilt-injected work happens in `WidgetRefreshWorker`.

---

## Data Refresh (`WidgetRefreshWorker.kt`)

```kotlin
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: LogbookRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val count        = repository.getCount().first()
        val distanceNm   = repository.getTotalDistanceNm().first()
        val lastFlight   = repository.getAll().first().firstOrNull()

        val prefs = PreferenceManager  // use DataStore<Preferences> via context
        // Write to DataStore
        applicationContext.widgetDataStore.edit { prefs ->
            prefs[WidgetDataKeys.FLIGHT_COUNT]      = count
            prefs[WidgetDataKeys.TOTAL_DISTANCE_NM] = distanceNm
            prefs[WidgetDataKeys.LAST_UPDATED_MS]   = System.currentTimeMillis()
            if (lastFlight != null) {
                prefs[WidgetDataKeys.LAST_FLIGHT_DEP]  = lastFlight.departureCode
                prefs[WidgetDataKeys.LAST_FLIGHT_ARR]  = lastFlight.arrivalCode
                prefs[WidgetDataKeys.LAST_FLIGHT_DATE] = lastFlight.departureTimeUtc
            } else {
                prefs.remove(WidgetDataKeys.LAST_FLIGHT_DEP)
                prefs.remove(WidgetDataKeys.LAST_FLIGHT_ARR)
                prefs.remove(WidgetDataKeys.LAST_FLIGHT_DATE)
            }
        }

        // Trigger Glance to re-render all widget instances
        FlightLogWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "widget_refresh_periodic"
        private const val WORK_NAME_ONCE     = "widget_refresh_once"

        fun enqueueOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }

        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS).build()
            )
        }
    }
}
```

**Why `Flow.first()` instead of new `suspend` DAO queries?**
`repository.getCount()` returns `Flow<Int>`. `Flow.first()` is the idiomatic way to do a one-shot read from a Room Flow without adding new DAO methods. No new DAO queries needed.

**Periodic schedule:** 6 hours. WorkManager may defer by up to 5 minutes in Doze mode — acceptable for a stats widget. Battery-safe.

**Where to call `enqueuePeriodic()`:** in `FlightLogApplication.onCreate()` (the app's `Application` class). This ensures the periodic work is registered after every app process start and survives app updates.

---

## Periodic Scheduling in Application Class

`WidgetRefreshWorker.enqueuePeriodic(this)` is called in `FlightLogApplication.onCreate()`.

`ExistingPeriodicWorkPolicy.KEEP` means if the periodic work already exists (from a previous app launch), it is not rescheduled — the existing schedule is preserved. This prevents the 6-hour timer from resetting every time the user opens the app.

---

## DataStore Instance

A single `DataStore<Preferences>` is needed for widget data. Create it as a top-level property in a new file `ui/widget/WidgetDataStore.kt`:

```kotlin
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "flight_log_widget"
)
```

This follows the official DataStore pattern (one instance per process via property delegate).

**Required dependency** — `androidx-datastore-preferences` must be added:

```toml
# libs.versions.toml
datastorePreferences = "1.1.1"
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
```

```kotlin
// build.gradle.kts
implementation(libs.androidx.datastore.preferences)
```

---

## AndroidManifest.xml Additions

```xml
<!-- Widget receivers — one per size variant is NOT required; one receiver handles both sizes -->
<receiver
    android:name=".ui.widget.FlightLogWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_info_medium" />
</receiver>
```

One receiver is sufficient. The `SizeMode.Responsive` in `FlightLogWidget` handles both size variants dynamically. A single `appwidget-provider` XML declares the medium size as the canonical descriptor (larger minimum area = safer for launchers that don't support responsive sizing).

---

## Widget Preview

Static XML layouts (`res/layout/widget_preview_small.xml` and `widget_preview_medium.xml`) show hardcoded sample data (e.g., "42 flights", "25,312 nm", "Last: JFK→LHR") in the system widget picker. These are referenced from the `appwidget-provider` XML via `android:previewLayout`.

The developer must create these as simple `LinearLayout` / `TextView` XML files using the app's style colors — Glance composables cannot be used as preview layouts.

---

## Files to Create / Modify

| File | Action |
|---|---|
| `ui/widget/FlightLogWidget.kt` | Create — `FlightLogWidget` + `FlightLogWidgetReceiver` |
| `ui/widget/WidgetRefreshWorker.kt` | Create — `@HiltWorker` CoroutineWorker |
| `ui/widget/WidgetDataKeys.kt` | Create — `Preferences.Key<T>` constants |
| `ui/widget/WidgetDataStore.kt` | Create — `Context.widgetDataStore` property delegate |
| `res/xml/widget_info_medium.xml` | Create — `appwidget-provider` XML |
| `res/layout/widget_preview_small.xml` | Create — static preview for small |
| `res/layout/widget_preview_medium.xml` | Create — static preview for medium |
| `AndroidManifest.xml` | Modify — add `<receiver>` block |
| `FlightLogApplication.kt` | Modify — call `WidgetRefreshWorker.enqueuePeriodic(this)` |
| `gradle/libs.versions.toml` | Modify — add `glance`, `datastorePreferences` versions + library entries |
| `app/build.gradle.kts` | Modify — add `glance-appwidget`, `glance-material3`, `datastore-preferences` |

---

## Implementation Steps

1. Add Glance and DataStore dependencies to `libs.versions.toml` and `build.gradle.kts`. Sync.
2. Create `WidgetDataKeys.kt`.
3. Create `WidgetDataStore.kt`.
4. Create `WidgetRefreshWorker.kt`. Register in `FlightLogApplication.onCreate()`.
5. Create `FlightLogWidget.kt` — widget UI composables + receiver.
6. Create `res/xml/widget_info_medium.xml` (appwidget-provider).
7. Create `res/layout/widget_preview_small.xml` and `widget_preview_medium.xml`.
8. Add receiver to `AndroidManifest.xml`.
9. Build + test on emulator: add widget to home screen, verify data appears, verify tap opens app.

---

## Edge Cases to Test

| # | Scenario | Input | Expected Output |
|---|---|---|---|
| 1 | Empty logbook | No flights in DB | Widget shows "0 flights", "0 nm"; no last-flight row in medium variant |
| 2 | Single flight | 1 flight in DB | Counts correct; last-flight row shows that flight's route and date |
| 3 | Last flight has empty departure code | `departureCode = ""` | Medium widget shows "— → ARR" (em dash or blank), no crash |
| 4 | Last flight has empty arrival code | `arrivalCode = ""` | Medium widget shows "DEP → —", no crash |
| 5 | Last flight has no departureTimeUtc (0L) | `departureTimeUtc = 0` | Date formats as epoch (Jan 1, 1970) rather than crashing; acceptable edge display |
| 6 | Widget added before first app launch | DataStore empty, no keys present | Widget shows "0 flights", "0 nm" (defaults from `?: 0` / `?: ""`) |
| 7 | Widget refresh Worker fails (DB error) | Worker throws exception | Worker returns `Result.retry()`; DataStore retains previous values; widget shows stale data |
| 8 | All flights have `distanceNm = null` | All distances unknown | `getTotalDistanceNm()` returns 0 (COALESCE in SQL); shows "0 nm" |
| 9 | Very large flight count | 10,000+ flights | Int does not overflow (`COUNT(*)` fits in Int); distance sum fits in Int up to ~2 billion nm (safe) |
| 10 | Widget on Android 12+ responsive sizing | Launcher resizes widget dynamically | `SizeMode.Responsive` re-renders at correct size breakpoint without flicker |
| 11 | Widget on Android 8-11 (API 26-30) | Old launcher ignores `targetCellWidth` | `minWidth`/`minHeight` used as fallback; medium layout renders at 250dp+ |
| 12 | Periodic work already scheduled | App updated / process restarted | `ExistingPeriodicWorkPolicy.KEEP` preserves schedule, does not reset 6h timer |
| 13 | `onUpdate` called by system | Launcher requests widget update | `enqueueOnce` fires a one-time WorkManager job; widget data refreshed promptly |
| 14 | Widget removed and re-added | User removes then re-pins widget | `onUpdate` fires again, one-time refresh enqueued, fresh data shown |
| 15 | App in background, Doze mode active | Device in Doze during 6h window | WorkManager defers Worker; widget may be up to ~11h stale (acceptable) |
| 16 | `widgetDataStore` accessed from Worker | Multiple Workers running concurrently | DataStore `edit {}` is serialized — safe; `updateAll()` is idempotent |
| 17 | Total distance exceeds Int range | `SUM(distanceNm)` > 2,147,483,647 | Not realistically reachable (~620,000 typical trans-Pacific round trips), but `getTotalDistanceNm()` returns `Int` — if overflow is a concern, DAO query should use `COALESCE(SUM(distanceNm), 0)` and return `Long`. Spec: change return type to `Long` if > 1 million flights anticipated. |
| 18 | `Flow.first()` in Worker on empty table | `getAll()` emits empty list immediately | `firstOrNull()` returns null; DataStore removes last-flight keys cleanly |
| 19 | Locale change between refresh and render | User changes system locale | Date is stored as epoch Long; re-formatted at render time with current `Locale.getDefault()` |
| 20 | Widget tap with app already in foreground | User taps widget while app is open | `actionStartActivity<MainActivity>()` brings app to front; no duplicate Activity stack |
| 21 | Widget preview in picker | User long-presses home → Widgets | Static preview XML shown with sample data; no live DB access at picker time |
| 22 | ProGuard / R8 release build | `isMinifyEnabled = true` | Glance internal reflection must be preserved; add `-keep class androidx.glance.**` to `proguard-rules.pro` |
