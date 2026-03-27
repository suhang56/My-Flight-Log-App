# Feature Spec: Phase 1 Feature 3 — Manual Flight Search/Add

**Date:** 2026-03-27
**Author:** Planner
**Status:** Ready for Designer + Developer

---

## Overview

Allow users to add flights to the logbook without a calendar event. The primary flow is: user enters a flight number and date, the app looks up the route via AviationStack, auto-fills the form fields, and the user saves. Manual entry (no lookup) is also supported as a fallback.

The existing `AddEditLogbookFlightScreen` and `AddEditLogbookFlightViewModel` are the foundation. Feature 3 extends them with a "Search" step before the form.

---

## User Stories

1. As a pilot, I want to type a flight number (e.g. "JL5") and a date, tap Search, and have the departure/arrival airports auto-filled so I don't have to look them up myself.
2. As a pilot, I want to fill in the full form manually (without a flight number lookup) so I can log charter or non-IATA flights.
3. As a pilot, I want the app to warn me if I'm adding a flight that looks like a duplicate of an existing logbook entry.
4. As a pilot, I want to see a loading indicator while the app is fetching flight data, and a clear error message if the lookup fails or returns no results.
5. As a pilot, I want to be able to dismiss the auto-fill and edit the form fields freely after the lookup.

---

## Data Model Changes

No schema changes required. The existing `LogbookFlight` entity already supports manually created entries:
- `sourceCalendarEventId = null`, `sourceLegIndex = null` for manual flights
- SQLite unique index treats all-NULL pairs as distinct, so no hard constraint blocks multiple manual flights

The `FlightRoute` returned by `FlightRouteService` provides `departureIata`, `arrivalIata`, `departureTimezone`, `arrivalTimezone` — all fields the form already handles.

**AviationStack API response gaps:** The current `AviationStackFlight` model only returns departure/arrival IATA and timezone. It does NOT return scheduled departure/arrival times or aircraft type. These must remain user-entered. This is an existing limitation in `FlightRouteServiceImpl` and there is no need to extend the API model for this feature.

---

## Feature Breakdown

### Sub-feature A: Flight Number Search Step

A search bar appears at the top of `AddEditLogbookFlightScreen` in "add" mode only (not edit mode). It consists of:
- A text field for the flight number (e.g. "JL5", "AA11")
- A date field (re-uses the existing `DatePickerField`, pre-filled to today)
- A "Search" button

On tap:
1. Show loading indicator (disable form and search button)
2. Call `FlightRouteService.lookupRoute(flightNumber, date)`
3. On success: auto-fill `departureCode`, `arrivalCode` in the form; show a dismissible "Auto-filled from flight data" chip/banner
4. On failure (null result or network error): show inline error "Flight not found. Enter details manually."
5. Loading state and error state are both dismissible; user can proceed to fill the form manually regardless

### Sub-feature B: Auto-fill Behavior

When a lookup succeeds, the ViewModel fills:
- `departureCode` ← `FlightRoute.departureIata`
- `arrivalCode` ← `FlightRoute.arrivalIata`
- `flightNumber` ← normalized input (trimmed, uppercased)

Fields that remain user-entered (API does not provide them):
- Date and departure/arrival times
- Aircraft type
- Seat class, seat number, notes

The user can freely overwrite any auto-filled field. The "Auto-filled" banner is dismissed when the user edits a filled field.

### Sub-feature C: Duplicate Prevention (existing, verify unchanged)

Already implemented in `AddEditLogbookFlightViewModel.save()` via `repository.existsByRouteAndDate()`. No changes needed. The UI already shows `AlertDialog` for `form.duplicateWarning`. Verify this path still triggers correctly for manually-added flights (i.e., `duplicateCheckPassed` resets when route or date changes).

### Sub-feature D: LogbookScreen Entry Point (existing FAB, verify)

`LogbookScreen` already has an `onAddFlight` callback that navigates to `Routes.LOGBOOK_ADD`. No route changes needed. Verify the FAB is visible and the nav argument `flightId` is absent (0 or not passed) so the ViewModel enters add mode.

---

## Implementation Steps

Steps are ordered for a single developer working top-down.

### Step 1: Extend `AddEditFormState` with search state fields

Add to `AddEditFormState` in `AddEditLogbookFlightViewModel.kt`:

```kotlin
val flightSearchQuery: String = "",         // raw user input for flight number search
val flightSearchDate: LocalDate = LocalDate.now(),
val isSearching: Boolean = false,
val searchError: String? = null,
val autoFillApplied: Boolean = false        // drives the "auto-filled" banner in UI
```

### Step 2: Add search logic to `AddEditLogbookFlightViewModel`

Inject `FlightRouteService` into the ViewModel (it is already bound in `NetworkModule` via Hilt). Add:

```kotlin
fun updateFlightSearchQuery(value: String) {
    _form.update { it.copy(flightSearchQuery = value.uppercase().trim(), searchError = null) }
}

fun updateFlightSearchDate(value: LocalDate) {
    _form.update { it.copy(flightSearchDate = value, searchError = null) }
}

fun searchFlight() {
    val query = _form.value.flightSearchQuery.ifBlank { return }
    _form.update { it.copy(isSearching = true, searchError = null) }
    viewModelScope.launch {
        val route = flightRouteService.lookupRoute(query, _form.value.flightSearchDate)
        if (route != null) {
            _form.update {
                it.copy(
                    isSearching = false,
                    flightNumber = route.flightNumber,
                    departureCode = route.departureIata,
                    arrivalCode = route.arrivalIata,
                    date = it.flightSearchDate,
                    autoFillApplied = true,
                    duplicateCheckPassed = false
                )
            }
        } else {
            _form.update {
                it.copy(
                    isSearching = false,
                    searchError = "Flight not found. Check the flight number and date, or enter details manually."
                )
            }
        }
    }
}

fun dismissAutoFillBanner() {
    _form.update { it.copy(autoFillApplied = false) }
}
```

Note: `updateDepartureCode` and `updateArrivalCode` already reset `duplicateCheckPassed = false`. They should also set `autoFillApplied = false` so the banner clears when the user manually edits auto-filled fields.

### Step 3: Update `AddEditLogbookFlightScreen` — add Search section

Above the "Route" section header, add a collapsible search block that is only visible in add mode (`!form.isEditMode`):

```
[ Flight Number input ] [ Date picker ] [ Search button ]
    "AA11"               Mar 27, 2026      [Search]
```

- Show `CircularProgressIndicator` in place of the Search button when `form.isSearching == true`
- Show inline `Text(form.searchError, color = MaterialTheme.colorScheme.error)` below the row when error is non-null
- Show a `SuggestionChip` or `AssistChip` reading "Auto-filled from flight data — tap to dismiss" when `form.autoFillApplied == true`

The search date picker reuses the existing `DatePickerField` composable unchanged.

The flight number search input should use `KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search)` with `KeyboardActions(onSearch = { viewModel.searchFlight() })`.

### Step 4: Wire up Hilt injection in ViewModel

`FlightRouteService` is already provided by `NetworkModule`. Add it to the constructor of `AddEditLogbookFlightViewModel`:

```kotlin
@HiltViewModel
class AddEditLogbookFlightViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val flightRouteService: FlightRouteService,
    savedStateHandle: SavedStateHandle
) : ViewModel()
```

### Step 5: Write unit tests in `AddEditFlightViewModelTest`

Create or extend a test file covering the new search flow (see Edge Cases section below).

---

## API Integration Details

**Service:** `FlightRouteService` (interface), implemented by `FlightRouteServiceImpl`
**Binding:** Already registered in `NetworkModule` (Hilt singleton)
**Method:** `suspend fun lookupRoute(flightNumber: String, date: LocalDate): FlightRoute?`

**What is returned on success:**
- `flightNumber: String` — normalized IATA flight number
- `departureIata: String` — 3-letter IATA departure code
- `arrivalIata: String` — 3-letter IATA arrival code
- `departureTimezone: String?` — IANA timezone string or null
- `arrivalTimezone: String?` — IANA timezone string or null

**What is NOT returned (must remain user-entered):**
- Scheduled departure/arrival times
- Aircraft type
- Seat information

**Failure modes (handled by `FlightRouteServiceImpl`):**
- HTTP error code → returns `null`, logs warning
- Empty `data` array → returns `null`, logs warning
- Missing IATA codes in response → returns `null`, logs warning
- Network exception → returns `null`, logs error

The ViewModel treats any `null` return as a search failure and shows `searchError`.

---

## Edge Cases to Test

### E1: Empty or blank flight number search
- Input: `flightSearchQuery = "   "` (whitespace only), Search tapped
- Expected: `searchFlight()` returns early without calling the API; no loading state, no error
- Guard: `if (query.isBlank()) return` in `searchFlight()`

### E2: Flight number with lowercase input
- Input: user types "aa11"
- Expected: normalized to "AA11" on each keystroke via `updateFlightSearchQuery`

### E3: API returns null (flight not found)
- Input: valid flight number "ZZ999", real date, API returns `null`
- Expected: `isSearching = false`, `searchError = "Flight not found..."`, form fields unchanged
- Banner: no auto-fill banner shown

### E4: API returns null and user proceeds manually
- Flow: search fails, user manually types "NRT" and "HND" into the form, saves
- Expected: save succeeds; `LogbookFlight` inserted with `sourceCalendarEventId = null`

### E5: Auto-fill applied, then user edits departure code
- Flow: search succeeds, "NRT"/"HND" auto-filled; user changes departure to "HND"
- Expected: `autoFillApplied` cleared (banner gone); `duplicateCheckPassed = false` reset

### E6: Duplicate flight detection after auto-fill
- Precondition: logbook already contains NRT→HND on 2026-03-27
- Flow: search for flight on same date, auto-fill succeeds, user taps Save
- Expected: duplicate warning dialog shown; user can "Save Anyway" or cancel
- Verify: `existsByRouteAndDate` called with correct UTC day boundary

### E7: Duplicate check UTC day boundary (timezone edge case)
- Scenario: user is in UTC+9 (Japan). Flight is NRT→HND, scheduled 00:30 JST on 2026-03-27 (= 2026-03-26 15:30 UTC)
- An existing logbook entry has the same route with `departureTimeUtc` = 2026-03-26 15:00 UTC
- Expected: duplicate is detected (both fall on the same UTC day: 2026-03-26)
- Scenario 2: existing entry is at 2026-03-26 23:50 UTC, new entry is 2026-03-27 00:10 UTC (different UTC days)
- Expected: NO duplicate warning (different UTC days, even though only 20 minutes apart)

### E8: Search triggered while already searching (double-tap)
- Flow: user taps Search twice quickly
- Expected: `isSearching = true` guard prevents second launch, or coroutine job is cancelled and replaced (prefer cancellation)

### E9: ViewModel rotated/process death during search
- Flow: search in progress (`isSearching = true`), screen rotated
- Expected: `SavedStateHandle` does not need to persist `isSearching`; on recompose `isSearching` resets to `false` (state is in-memory MutableStateFlow, not saved state). Search can be re-issued.

### E10: Flight number exactly 2 characters (boundary)
- Input: "AA" (2 chars, below typical 3-char minimum for IATA flight numbers)
- Expected: search is still attempted (validation is the API's job, not the ViewModel's); API returns null; error shown

### E11: Flight number very long (boundary)
- Input: 20-character string
- Expected: no crash; API called with trimmed string; likely returns null

### E12: API returns flight with null IATA codes
- Scenario: `AviationStackFlight.departure?.iata == null`
- Expected: `FlightRouteServiceImpl.lookupRoute` returns `null` (already handles this); ViewModel shows error

### E13: Concurrent add + calendar sync
- Scenario: user saves a manual flight at the same moment a calendar sync runs and inserts the same flight
- Expected: the `IGNORE` conflict strategy on `LogbookFlightDao.insert` prevents a crash; one insertion wins silently; no duplicate row for calendar flights (unique index on sourceCalendarEventId + sourceLegIndex). Manual flights are not affected by the unique index (both sourceCalendarEventId are null, SQLite treats as distinct).

### E14: Save with empty arrival time
- Input: departure code = "NRT", arrival code = "HND", departure time set, arrival time = null
- Expected: `LogbookFlight.arrivalTimeUtc = null`; save succeeds; statistics queries handle null arrivalTimeUtc gracefully (already the case in DAO queries)

### E15: Same departure and arrival airport
- Input: departureCode = "NRT", arrivalCode = "NRT"
- Expected: no validation error (uncommon but valid for training flights); `distanceNm = 0` (AirportCoordinatesMap returns 0 for same-airport distance)
- Statistics: such a flight should not distort the "longest by distance" query

### E16: Date picker shows future dates
- User picks a date 1 year in the future
- Expected: no validation error; AviationStack API called with that date; likely returns null (no scheduled data); error shown cleanly

### E17: Network unavailable during search
- Scenario: device offline, user taps Search
- Expected: `FlightRouteServiceImpl` catches the exception and returns null; ViewModel shows `searchError`; no crash

### E18: Logbook empty state — first manual flight
- Precondition: logbook has 0 entries
- Flow: user adds first flight manually
- Expected: empty-state UI in LogbookScreen is replaced by the new flight entry; statistics all update from zero baseline

---

## Dependencies

| Dependency | Status | Notes |
|---|---|---|
| `FlightRouteService` / `FlightRouteServiceImpl` | Exists | Injected via Hilt, ready to use |
| `AviationStackApi` | Exists | Already wired in `NetworkModule` |
| `AddEditLogbookFlightScreen` | Exists | Needs search section added |
| `AddEditLogbookFlightViewModel` | Exists | Needs `FlightRouteService` injected + search methods |
| `AddEditFormState` | Exists | Needs 5 new fields |
| `LogbookRepository.existsByRouteAndDate` | Exists | No changes needed |
| Room schema migration | Not needed | No entity changes |

---

## Risks

1. **AviationStack free tier rate limit:** The API has a request cap on the free plan. Frequent searches (e.g. autocomplete-style) would exhaust it quickly. Mitigation: search is triggered only by explicit button tap, not on every keystroke.

2. **AviationStack historical data gaps:** Flights older than ~1 week may not be in the API. Users logging past flights will get null results and fall back to manual entry. This is acceptable behavior; the error message should hint at this ("Check the flight number and date, or enter details manually.").

3. **IATA code discrepancies:** AviationStack may return codes inconsistent with `AirportTimezoneMap` or `AirportCoordinatesMap`. If the returned IATA code is not in those maps, timezone falls back to `ZoneId.systemDefault()` and `distanceNm` is null. This is the existing graceful fallback — no new risk.

4. **Form state after back navigation:** If the user navigates back from the add screen mid-search, the ViewModel coroutine is cancelled by `viewModelScope`. No leak risk with the current pattern.
