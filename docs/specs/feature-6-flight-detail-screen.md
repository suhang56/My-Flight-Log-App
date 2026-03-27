# Feature Spec: Feature 6 — Flight Detail Screen

**Date:** 2026-03-27
**Author:** Planner
**Status:** Ready for Designer + Developer

---

## Overview

The current flight detail view is `LogbookDetailBottomSheet` — a `ModalBottomSheet` inside `LogbookScreen`. It shows all the right data but is cramped, cannot scroll comfortably on small screens, and has no room for future features (route map, photo attachments, share-a-flight). It is also architecturally awkward: the delete confirmation dialog fires from `LogbookViewModel` even though the sheet is about a single flight.

This feature promotes it to a dedicated full-screen `FlightDetailScreen` with a proper nav route. The bottom sheet is removed entirely. The `LogbookViewModel` state for `showDetailSheet`, `selectedFlight`, and the delete confirmation dialog that belongs to the detail view can all be cleaned up.

**Navigation change:** Tapping a `LogbookCard` now navigates to `logbook/detail/{flightId}` instead of opening a bottom sheet. The bottom nav bar is hidden on this screen (same pattern as `AddEditLogbookFlightScreen`).

---

## User Stories

1. As a pilot, I want to tap a flight in the logbook and see a full-screen view with all details clearly laid out, so I can read long notes and see times without squinting at a cramped sheet.
2. As a pilot, I want to see departure and arrival times in their local airport timezones side-by-side, so I can quickly understand my schedule at a glance.
3. As a pilot, I want to see flight duration and distance prominently, as these are the most-referenced stats when recalling a trip.
4. As a pilot, I want to tap Edit from the detail screen to go straight to the edit form.
5. As a pilot, I want to be able to share a single flight's details as text (e.g. copy to clipboard, share via messages), setting up future social features.
6. As a pilot, I want to see a map placeholder so the screen feels complete, even before the live map is built.

---

## Architecture Decision: ViewModel for the Detail Screen

**Chosen approach: `FlightDetailViewModel` with `SavedStateHandle`.**

The screen receives a `flightId: Long` nav argument and loads the flight from `repository.getById(flightId)`. This is identical to the pattern in `AddEditLogbookFlightViewModel`. The detail screen does not share a ViewModel with `LogbookScreen` — it is fully independent.

**LogbookViewModel cleanup:** Remove `showDetailSheet`, `selectedFlight`, `dismissDetailSheet`, `selectFlight` from `LogbookUiState` and `LogbookViewModel`. The delete confirmation dialog that currently lives in `LogbookScreen` and is driven by `LogbookViewModel` also moves: delete is now initiated from `FlightDetailScreen` and handled by `FlightDetailViewModel`, which navigates back to the logbook on completion.

This cleanup is a meaningful debt reduction — `LogbookViewModel` currently mixes list management with single-item sheet state. After this feature, it manages only the list, filter, export, and undo-delete snackbar.

---

## Screen Layout

### TopAppBar
- Back arrow (navigates to `logbook`)
- Title: flight number if present (e.g. "JL 5"), otherwise "Flight Detail"
- Actions: Share icon button (see Share section), Edit icon button

### Section 1: Route Header
Large, prominent departure → arrival display.

```
    NRT          →→→✈→→→         JFK
  Narita                      New York JFK
```

- Departure and arrival IATA codes: `headlineL` typography, monospace, bold
- Airport full names below each code: `bodyMedium`, `onSurfaceVariant`
  - Source: `AirportTimezoneMap` does not have names. Use a new lightweight `AirportDisplayNameMap` (code → display name) OR fall back to just the IATA code if name is unknown. See Dependencies.
- Animated flight icon between them (static `Flight` icon rotated 90°, same as the existing card)

### Section 2: Timeline
Departure and arrival times in a two-column layout.

```
  DEPARTED                    ARRIVED
  Thu, Mar 27                 Thu, Mar 27
  11:30 JST                   10:45 EDT
  Narita (NRT)                JFK (New York)
```

- Formatted using existing `formatInZone()` with `FULL_DATE_TIME_TZ_FORMATTER` (already in `TimeFormatting.kt`)
- Arrival column shows "—" in all three rows if `arrivalTimeUtc` is null

Duration badge centered below the two columns:
```
        ⏱  13h 15m
```
- Hidden if `arrivalTimeUtc` is null

### Section 3: Flight Info
A card or grouped section with labeled rows:

| Label | Value | Shown when |
|---|---|---|
| Flight | JL 5 | `flightNumber.isNotBlank()` |
| Aircraft | Boeing 777-300ER | `aircraftType.isNotBlank()` |
| Distance | 6,732 NM | `distanceNm != null` |
| Added | Mar 15, 2025 | always (from `addedAt`) |
| Source | Calendar sync / Manual | always |

"Source" is derived: `sourceCalendarEventId != null` → "Calendar sync", else → "Manually added".

### Section 4: Seat Info
Shown only when at least one of `seatClass`, `seatNumber` is non-blank.

```
  Seat Class   Business
  Seat Number  4A
```

### Section 5: Notes
Shown only when `notes.isNotBlank()`. Full multi-line text, selectable (for copy).

```
  NOTES
  Great window view of Mt. Fuji on descent.
  Excellent meal service on this sector.
```

### Section 6: Map Placeholder
A `Box` with a light grey background, rounded corners, dashed border, and centered text:

```
  [  Route map coming soon  ]
```

Height: 160.dp. This reserves space in the layout so the screen does not reflow when the real map is added in a future feature. The placeholder is always shown.

### Bottom Action Row
Two buttons, full-width, at the bottom of the scrollable content (not pinned to the scaffold bottom — scroll to reach):

```
  [ Delete ]       [ Edit ]
```

- Delete: `OutlinedButton` with error color (same as existing sheet)
- Edit: `Button` (filled, primary)
- Delete triggers an `AlertDialog` confirmation dialog within this screen, handled by `FlightDetailViewModel`

---

## Share Feature

**Scope for Feature 6:** Share the flight as a formatted text string via the Android share sheet (`ACTION_SEND`, `type = "text/plain"`). No FileProvider needed — just a plain string.

**Share text format:**

```
✈ JL 5: NRT → JFK
Thu, Mar 27, 2026  11:30 JST → 10:45 EDT
Duration: 13h 15m  •  Distance: 6,732 NM
Aircraft: Boeing 777-300ER  •  Business, Seat 4A
Logged with My Flight Log
```

Lines are omitted if the data is not available (e.g. no aircraft type → that line is skipped). The trailing attribution "Logged with My Flight Log" is always included.

**Implementation:** A simple `buildShareText(flight: LogbookFlight): String` function (top-level or companion) in `FlightDetailScreen.kt`. The share intent is fired from a `LaunchedEffect` or directly in the click handler (no async work needed).

---

## Implementation Steps

### Step 1: Add route constants and nav argument to `NavGraph.kt`

Add to `Routes`:

```kotlin
const val LOGBOOK_DETAIL = "logbook/detail/{flightId}"
fun logbookDetail(flightId: Long) = "logbook/detail/$flightId"
```

Add to `hideBottomBarRoutes`:

```kotlin
private val hideBottomBarRoutes = setOf(
    Routes.LOGBOOK_ADD,
    Routes.LOGBOOK_EDIT,
    Routes.LOGBOOK_DETAIL  // new
)
```

Add composable destination in `NavHost`:

```kotlin
composable(
    route = Routes.LOGBOOK_DETAIL,
    arguments = listOf(navArgument("flightId") { type = NavType.LongType })
) {
    FlightDetailScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEdit = { id -> navController.navigate(Routes.logbookEdit(id)) }
    )
}
```

Update `LogbookScreen` call in `NavHost`:

```kotlin
composable(Routes.LOGBOOK) {
    LogbookScreen(
        onAddFlight = { navController.navigate(Routes.LOGBOOK_ADD) },
        onEditFlight = { id -> navController.navigate(Routes.logbookEdit(id)) },
        onViewFlight = { id -> navController.navigate(Routes.logbookDetail(id)) }  // new
    )
}
```

### Step 2: Create `FlightDetailViewModel`

New file: `ui/logbook/FlightDetailViewModel.kt`

```kotlin
sealed class FlightDetailUiState {
    data object Loading : FlightDetailUiState()
    data class Success(val flight: LogbookFlight) : FlightDetailUiState()
    data object NotFound : FlightDetailUiState()
}

@HiltViewModel
class FlightDetailViewModel @Inject constructor(
    private val repository: LogbookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val flightId: Long = checkNotNull(savedStateHandle["flightId"])

    private val _uiState = MutableStateFlow<FlightDetailUiState>(FlightDetailUiState.Loading)
    val uiState: StateFlow<FlightDetailUiState> = _uiState.asStateFlow()

    val showDeleteConfirmation = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val flight = repository.getById(flightId)
            _uiState.value = if (flight != null)
                FlightDetailUiState.Success(flight)
            else
                FlightDetailUiState.NotFound
        }
    }

    fun requestDelete() { showDeleteConfirmation.value = true }
    fun cancelDelete() { showDeleteConfirmation.value = false }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(flightId)
            onDeleted()
        }
    }
}
```

Note: `confirmDelete` takes an `onDeleted` callback to trigger navigation back from the composable. This is preferable to a `navigateBack` event flow for a simple one-shot action.

### Step 3: Create `FlightDetailScreen.kt`

New file: `ui/logbook/FlightDetailScreen.kt`

Key composable signature:

```kotlin
@Composable
fun FlightDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: FlightDetailViewModel = hiltViewModel()
)
```

Structure:
- Collect `uiState` and `showDeleteConfirmation`
- When `Loading`: show `CircularProgressIndicator` centered
- When `NotFound`: show error message "Flight not found" + back button
- When `Success(flight)`: render full layout in a `Scaffold` with `TopAppBar` + scrollable `Column`

**Delete confirmation dialog** — rendered in this screen, calls `viewModel.confirmDelete { onNavigateBack() }`.

**Share button** — `IconButton` in TopAppBar actions with `Icons.Default.Share`. On click:

```kotlin
val context = LocalContext.current
val shareText = buildShareText(flight)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, shareText)
}
context.startActivity(Intent.createChooser(intent, null))
```

No `LaunchedEffect` needed — this is a direct click action.

### Step 4: Update `LogbookScreen` and `LogbookViewModel`

**`LogbookScreen`:**
- Add `onViewFlight: (Long) -> Unit` parameter
- In `LogbookCard` click: call `onViewFlight(flight.id)` instead of `viewModel.selectFlight(flight)`
- Remove `LogbookDetailBottomSheet` call block (lines 117–127 in current screen)
- Remove delete confirmation dialog block driven by `showDetailSheet` (lines 129–146)

**`LogbookViewModel`:**
- Remove `showDetailSheet`, `selectedFlight`, `showDeleteConfirmation`, `deletedFlight` (the detail-related ones — note: `deletedFlight` is used for undo-delete snackbar, keep that one)
- Remove `selectFlight()`, `dismissDetailSheet()`, `requestDelete()`, `cancelDelete()`, `confirmDelete()` methods
- Keep: `undoDelete()`, `clearSnackbar()`, `snackbarMessage`, `deletedFlight` (undo-delete path still lives in LogbookScreen via snackbar)

**Undo-delete after delete from detail screen:** When `FlightDetailViewModel.confirmDelete` runs and navigates back to LogbookScreen, the snackbar "Flight removed from logbook" with Undo should still appear. This requires `LogbookViewModel` to be notified of the deletion. Since `repository.delete()` causes `repository.getAll()` to re-emit (Room observability), the list updates automatically. But the snackbar state lives in `LogbookViewModel`.

**Recommended approach:** Pass the deleted flight back to `LogbookScreen` via a navigation result. Use `navController.previousBackStackEntry?.savedStateHandle?.set("deletedFlight", flightId)` before popping back, then observe it in `LogbookScreen` via `navController.currentBackStackEntry?.savedStateHandle`. `LogbookViewModel` then receives the flight ID and sets up the undo snackbar.

Alternative (simpler): drop the undo-delete path for delete-from-detail-screen in Feature 6. The delete confirmation dialog already asks "Are you sure?" — that is sufficient protection. Undo from the list-level delete (swipe or bottom sheet) can remain. This simplification is acceptable for Feature 6; undo from detail can be added later.

**Recommendation: use the simpler approach.** `FlightDetailViewModel.confirmDelete` calls `repository.delete(flightId)` and calls `onNavigateBack()`. No undo snackbar on this path. The dialog is the safeguard.

### Step 5: Write unit tests

Create `FlightDetailViewModelTest` covering the edge cases below.

---

## Edge Cases to Test

### E1: Valid flightId — flight loaded and displayed
- Input: `flightId = 42`, repository has a flight with id = 42
- Expected: `uiState` transitions Loading → Success(flight)

### E2: Invalid flightId — not found state
- Input: `flightId = 9999`, repository has no flight with id = 9999
- Expected: `uiState` transitions Loading → NotFound
- UI: shows error message and back button; no crash

### E3: flightId = 0 (edge — nav argument default)
- Input: nav argument not passed or malformed → `flightId = 0`
- Expected: `repository.getById(0)` returns null; NotFound state shown
- Guard: `checkNotNull(savedStateHandle["flightId"])` throws if key missing entirely — developer must ensure the route always passes a valid Long

### E4: Flight with all optional fields blank
- Input: `flightNumber = ""`, `aircraftType = ""`, `seatClass = ""`, `seatNumber = ""`, `notes = ""`, `arrivalTimeUtc = null`, `distanceNm = null`
- Expected: Section 3 shows only "Added" and "Source" rows; Section 4 (seat) hidden; Section 5 (notes) hidden; timeline arrival column shows "—"; duration badge hidden
- No empty `Text("")` components rendered

### E5: Flight with all optional fields populated
- All fields non-blank, all nullable fields non-null
- Expected: all sections visible; no "—" placeholders shown

### E6: arrivalTimeUtc is null — timeline and duration
- Expected: arrival column shows "—" for date, time, and airport label; duration badge not rendered

### E7: arrivalTimeUtc <= departureTimeUtc (bad data)
- Input: `arrivalTimeUtc = departureTimeUtc - 1000` (arrival 1 second before departure)
- Expected: duration not shown (treat as null/invalid, consistent with existing sheet behavior: `coerceAtLeast(0)` would show "0h 0m" — spec recommends NOT showing duration at all when diff <= 0)
- Note: the existing bottom sheet uses `coerceAtLeast(0)` which shows "0h 0m". The new screen should show nothing for duration in this case, which is cleaner.

### E8: Timezone null — fallback to system default
- Input: `departureTimezone = null`
- Expected: `formatInZone()` uses `ZoneId.systemDefault()` (existing behavior); no crash
- Times shown in device local timezone, no timezone abbreviation shown (or shows system tz abbreviation)

### E9: Invalid IANA timezone string
- Input: `departureTimezone = "Not/ATimezone"`
- Expected: `runCatching { ZoneId.of(it) }.getOrNull()` returns null; falls back to `ZoneId.systemDefault()`; no crash

### E10: Airport display name not in map
- Input: `departureCode = "ZZZ"` (unknown airport)
- Expected: sub-label under the IATA code shows nothing (or the code itself); no crash; no empty placeholder text

### E11: Very long flight number in TopAppBar title
- Input: `flightNumber = "ABCDEFGHIJK12345"` (unrealistically long)
- Expected: title text truncates with ellipsis (`overflow = TextOverflow.Ellipsis`), does not push action icons off screen

### E12: Very long notes field
- Input: `notes` = 5,000 character string
- Expected: notes section renders in full (scrollable content); no truncation; `Text` is selectable for copy

### E13: Delete confirmation — cancel keeps flight
- Action: tap Delete button → dialog appears → tap Cancel
- Expected: `showDeleteConfirmation` resets to false; flight still in repository; no navigation

### E14: Delete confirmation — confirm deletes and navigates back
- Action: tap Delete → confirm
- Expected: `repository.delete(flightId)` called once; `onNavigateBack()` called; no double-delete

### E15: Edit button navigation
- Action: tap Edit
- Expected: `onNavigateToEdit(flight.id)` called; navigates to `logbook/edit/{flightId}`; back from edit returns to `FlightDetailScreen` (standard back stack behavior)

### E16: Back from edit — detail screen shows updated data
- Action: user edits flight (changes notes), saves, navigates back to FlightDetailScreen
- Expected: detail screen re-loads flight from repository (via `getById` in init — but init only runs once)
- Issue: if `FlightDetailViewModel` only loads once in `init`, edits made in `AddEditLogbookFlightViewModel` are not reflected when returning to the detail screen
- Solution: use `repository.getById` wrapped in a `Flow` (if the DAO has a Flow-returning overload) OR reload in `onResume`-equivalent. The recommended approach is to add a Flow-based `getByIdFlow(id: Long): Flow<LogbookFlight?>` DAO query so the detail screen reactively updates.
- If Flow approach is used: `uiState` re-emits whenever the flight is edited, deleted, or otherwise modified.

### E17: Flight deleted from edit screen — detail screen handles gracefully
- Scenario: user navigates to detail → edit → deletes from edit screen → presses back twice → back at logbook list
- Expected: FlightDetailScreen is no longer on the back stack (edit screen called `onNavigateBack()` which popped to detail, then detail is stale); OR the Flow-based approach in E16 causes `uiState` to emit NotFound, triggering `onNavigateBack()` automatically
- If not using Flow: this edge case requires explicit handling — observe deletion events

### E18: Share text — all fields present
- Input: complete flight
- Expected share text matches format spec exactly; no extra blank lines; "Logged with My Flight Log" footer present

### E19: Share text — minimal flight (only required fields)
- Input: no flight number, no aircraft, no seat, no notes, no arrival time, no distance
- Expected: share text shows only route and departure time; optional lines omitted; no blank lines between sections

### E20: Share intent — no app can handle it
- Scenario: device has no apps that handle `ACTION_SEND` with `text/plain` (extremely unlikely but possible on restricted devices)
- Expected: `startActivity(Intent.createChooser(...))` — `createChooser` always shows a system dialog even if no targets exist; no crash

### E21: Rotate screen on detail screen — state preserved
- Action: device rotated while viewing detail
- Expected: ViewModel survives configuration change; flight data re-displayed without re-fetching from DB (StateFlow holds last value)

### E22: LogbookScreen back stack after navigation from detail
- Action: user taps Back from FlightDetailScreen
- Expected: returns to LogbookScreen with scroll position preserved (standard `popBackStack()` behavior); filter state unaffected; list is in same state as when user left

### E23: Source label — calendar-synced flight
- Input: `sourceCalendarEventId = 12345L`, `sourceLegIndex = 0`
- Expected: Source row shows "Calendar sync"

### E24: Source label — manually added flight
- Input: `sourceCalendarEventId = null`
- Expected: Source row shows "Manually added"

### E25: addedAt display
- Input: `addedAt = 1742041800000L` (some epoch millis)
- Expected: "Added" row shows a human-readable date (e.g. "Mar 15, 2025") using `DATE_FORMATTER` from `TimeFormatting.kt` in the device's local timezone (not UTC)

---

## Dependencies

| Item | Status | Notes |
|---|---|---|
| `Routes.LOGBOOK_DETAIL` | New | Add to `Routes` object in `NavGraph.kt` |
| `FlightDetailViewModel` | New | `ui/logbook/FlightDetailViewModel.kt` |
| `FlightDetailScreen` | New | `ui/logbook/FlightDetailScreen.kt` |
| `LogbookFlightDao.getByIdFlow()` | New (recommended) | Flow-based single-flight query for E16/E17 reactivity |
| `LogbookRepository.getByIdFlow()` | New (recommended) | Forward the Flow query |
| `LogbookViewModel` | Modify — cleanup | Remove sheet state, `selectFlight`, `dismissDetailSheet`, `requestDelete`, `cancelDelete`, `confirmDelete` |
| `LogbookScreen` | Modify | Remove bottom sheet, delete dialog from sheet path; add `onViewFlight` parameter |
| `NavGraph.kt` | Modify | Add route, add to `hideBottomBarRoutes`, update LogbookScreen call |
| `AirportDisplayNameMap` | New (optional) | Code → full name lookup; if too large to build now, fall back to showing only IATA code |
| `TimeFormatting.kt` | Unchanged | `formatInZone`, `FULL_DATE_TIME_TZ_FORMATTER` reused directly |
| Room migration | Not needed | No entity changes |
| New Gradle dependency | Not needed | All icons, navigation, compose components already present |

---

## AirportDisplayNameMap Decision

The spec calls for showing full airport names (e.g. "Narita" below "NRT"). The existing `AirportNameMap` is a city-name → code lookup, not the reverse. Two options:

**Option A:** Create a new `AirportDisplayNameMap` (code → short display name) with the ~200 most common airports. Adds a file but gives a polished result.

**Option B:** Skip full names in Feature 6; show only IATA codes in the route header. The sub-label slot is reserved in the layout but left empty or omitted when the name is unknown.

**Recommendation: Option B for Feature 6.** The screen is valuable without names. A future feature can add the display name map once the airport data is well-defined. Do not block the feature on building a 200-entry name map. The layout should accommodate the name sub-label when it exists (e.g. if the map is added later, no layout change needed).

---

## Risks

1. **E16 reactivity (back-from-edit stale data):** If `getById` is a one-shot suspend call (current DAO), the detail screen will show stale data after the user edits and returns. The recommended fix is adding `getByIdFlow(id): Flow<LogbookFlight?>` to the DAO. If the Developer uses the one-shot approach, E16 must be explicitly tested and accepted as a known limitation.

2. **LogbookViewModel undo-delete after detail-screen delete:** As noted in Step 4, the undo snackbar is intentionally not supported for the delete-from-detail path. The confirmation dialog is the safeguard. If this is unacceptable, the navigation-result approach (set deleted flightId on previous back stack entry) must be implemented — adds ~10 lines but restores full parity.

3. **Bottom nav bar hidden on detail screen:** `hideBottomBarRoutes` uses exact route string matching. `Routes.LOGBOOK_DETAIL` is `"logbook/detail/{flightId}"` — the literal template string, not a resolved path. The `currentDestination?.route` returns the template string, so this works correctly. Verify with the existing `LOGBOOK_EDIT` pattern which uses the same mechanism.

4. **`LogbookCard` click change:** The current `LogbookCard` calls `viewModel.selectFlight(flight)` which sets the bottom sheet state. After this change it calls `onViewFlight(flight.id)`. Any existing test that mocks `selectFlight` will break and must be updated.
