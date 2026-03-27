# Feature Spec: Phase 1 Feature 4 — Logbook Search & Filter

**Date:** 2026-03-27
**Author:** Planner
**Status:** Ready for Designer + Developer

---

## Overview

The LogbookScreen currently shows all flights in reverse-chronological order with no way to search or narrow the list. Once a user has more than ~30 flights, navigation becomes impractical. This feature adds:

- A real-time debounced search bar (flight number, route, notes)
- Filter chips: seat class, year
- Sort toggle: newest first (default), oldest first, longest distance
- A "no results" empty state distinct from the "logbook is empty" state
- A clear-all button that resets search + filters + sort

All filtering and sorting is done in-memory in the ViewModel by transforming the existing `repository.getAll()` Flow. No new DAO queries are required. No Room migration is needed.

---

## User Stories

1. As a pilot with 100+ flights, I want to type "NRT" in the search bar and immediately see only flights involving Narita, so I can find a specific trip without scrolling.
2. As a pilot, I want to filter by seat class "Business" to quickly see all my business class flights for expense reporting.
3. As a pilot, I want to filter by year "2025" to review last year's flying activity before preparing my log.
4. As a pilot, I want to sort by longest distance to see my longest hauls at the top.
5. As a pilot, I want to tap a single "Clear" button to reset all search, filter, and sort state at once.
6. As a pilot, I want to see a clear message when my search returns no results, distinct from the empty logbook state.

---

## Data Model Changes

None. All filtering is performed in-memory on the `List<LogbookFlight>` emitted by the existing `repository.getAll()` Flow.

One new DAO query is needed to populate the year filter chip options:

```sql
SELECT DISTINCT strftime('%Y', departureTimeUtc / 1000, 'unixepoch') AS year
FROM logbook_flights
ORDER BY year DESC
```

This returns the set of years present in the logbook, used to build the year filter chip list dynamically. Returns UTC-based years — acceptable since we want consistency with how the statistics screen computes `yearMonth`.

One new DAO query for distinct seat class values (to drive chip options):

```sql
SELECT DISTINCT seatClass
FROM logbook_flights
WHERE seatClass IS NOT NULL AND seatClass != ''
ORDER BY seatClass ASC
```

Both are read-only `Flow` queries. No schema changes.

---

## Architecture Decision: In-Memory Filtering vs. Dynamic SQL

**Chosen approach: in-memory filtering in the ViewModel.**

Rationale:
- The existing `getAll()` flow already loads all `LogbookFlight` rows. Re-querying Room for every search keystroke would create a new Flow subscription per query, complicating lifecycle management.
- In-memory filtering with `combine` on a single Flow + filter state is idiomatic Kotlin/Compose and easy to test.
- A personal logbook is unlikely to exceed a few thousand flights — in-memory filtering of even 5,000 rows takes <1ms on modern Android hardware.
- Avoids the need for `@RawQuery` or multiple conditional `@Query` overloads.

**The one exception:** year and seat class chip options are populated from DAO queries (two new `Flow<List<String>>` queries), not derived from the in-memory list. This ensures the filter options reflect the full dataset, not the currently filtered subset.

---

## Implementation Steps

### Step 1: Add new DAO queries to `LogbookFlightDao`

Add two new `Flow`-returning queries:

```kotlin
@Query("""
    SELECT DISTINCT strftime('%Y', departureTimeUtc / 1000, 'unixepoch') AS year
    FROM logbook_flights
    ORDER BY year DESC
""")
fun getDistinctYears(): Flow<List<String>>

@Query("""
    SELECT DISTINCT seatClass
    FROM logbook_flights
    WHERE seatClass IS NOT NULL AND seatClass != ''
    ORDER BY seatClass ASC
""")
fun getDistinctSeatClasses(): Flow<List<String>>
```

### Step 2: Expose new queries from `LogbookRepository`

Add two forwarding functions:

```kotlin
fun getDistinctYears(): Flow<List<String>> = logbookFlightDao.getDistinctYears()
fun getDistinctSeatClasses(): Flow<List<String>> = logbookFlightDao.getDistinctSeatClasses()
```

### Step 3: Define filter/sort state in `LogbookViewModel`

Add a new state class and enum:

```kotlin
enum class LogbookSortOrder { NEWEST_FIRST, OLDEST_FIRST, LONGEST_DISTANCE }

data class LogbookFilterState(
    val searchQuery: String = "",
    val selectedSeatClass: String? = null,   // null = no filter
    val selectedYear: String? = null,        // null = no filter; e.g. "2025"
    val sortOrder: LogbookSortOrder = LogbookSortOrder.NEWEST_FIRST
) {
    val isActive: Boolean get() =
        searchQuery.isNotBlank() || selectedSeatClass != null || selectedYear != null
            || sortOrder != LogbookSortOrder.NEWEST_FIRST
}
```

`isActive` drives the visibility of the "Clear" button.

### Step 4: Rewrite `LogbookViewModel` to use filtered + sorted flow

Replace the current `flights` StateFlow (which simply exposes `repository.getAll()`) with a derived Flow that combines the full list with filter state:

```kotlin
// Raw debounced search query — debounce 300ms to avoid filtering on every keystroke
private val _filterState = MutableStateFlow(LogbookFilterState())
val filterState: StateFlow<LogbookFilterState> = _filterState.asStateFlow()

val availableYears: StateFlow<List<String>> = repository.getDistinctYears()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val availableSeatClasses: StateFlow<List<String>> = repository.getDistinctSeatClasses()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val flights: StateFlow<List<LogbookFlight>> =
    combine(
        repository.getAll(),
        _filterState.debounce(300L)
    ) { allFlights, filter ->
        allFlights
            .filter { flight -> matchesSearch(flight, filter.searchQuery) }
            .filter { flight -> filter.selectedSeatClass == null || flight.seatClass == filter.selectedSeatClass }
            .filter { flight -> filter.selectedYear == null || flightYear(flight) == filter.selectedYear }
            .let { filtered ->
                when (filter.sortOrder) {
                    LogbookSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.departureTimeUtc }
                    LogbookSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.departureTimeUtc }
                    LogbookSortOrder.LONGEST_DISTANCE -> filtered.sortedByDescending { it.distanceNm ?: -1 }
                }
            }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Helper functions (private, in ViewModel):

```kotlin
private fun matchesSearch(flight: LogbookFlight, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().uppercase()
    return flight.flightNumber.uppercase().contains(q)
        || flight.departureCode.uppercase().contains(q)
        || flight.arrivalCode.uppercase().contains(q)
        || flight.notes.uppercase().contains(q)
}

private fun flightYear(flight: LogbookFlight): String =
    Instant.ofEpochMilli(flight.departureTimeUtc)
        .atZone(ZoneOffset.UTC)
        .year.toString()
```

Add update functions:

```kotlin
fun updateSearchQuery(query: String) { _filterState.update { it.copy(searchQuery = query) } }
fun toggleSeatClassFilter(seatClass: String) {
    _filterState.update { it.copy(selectedSeatClass = if (it.selectedSeatClass == seatClass) null else seatClass) }
}
fun toggleYearFilter(year: String) {
    _filterState.update { it.copy(selectedYear = if (it.selectedYear == year) null else year) }
}
fun setSortOrder(order: LogbookSortOrder) { _filterState.update { it.copy(sortOrder = order) } }
fun clearFilters() { _filterState.update { LogbookFilterState() } }
```

**Important:** `flightCount` and `totalDistanceNm` should reflect the FILTERED count, not the total. Update them:

```kotlin
val flightCount: StateFlow<Int> = flights.map { it.size }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

val totalDistanceNm: StateFlow<Int> = flights.map { list ->
    list.sumOf { it.distanceNm ?: 0 }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```

This makes the stats row at the top of `LogbookScreen` reflect the current search/filter context, which is more useful than always showing total.

### Step 5: Update `LogbookScreen` UI

**Search bar:** Add a `SearchBar` or `OutlinedTextField` at the top of the list (above the `StatsRow`), as a sticky item in the `LazyColumn` (or outside it, above the scaffold content). Use `ImeAction.Search` with `KeyboardActions(onSearch = { focusManager.clearFocus() })`.

**Filter chips row:** A horizontally scrollable `Row` of `FilterChip` components below the search bar. Show year chips first (e.g. "2025", "2024"), then seat class chips (e.g. "Business", "Economy"). Each chip shows a checkmark icon when selected.

**Sort toggle:** A `DropdownMenu` or `SegmentedButton` accessible via a sort icon button in the `TopAppBar` actions area. Options: "Newest First", "Oldest First", "Longest Distance".

**Clear button:** A `TextButton` labeled "Clear" in the `TopAppBar` actions area, visible only when `filterState.isActive == true`. Tapping calls `viewModel.clearFilters()`.

**No-results empty state:** When `flights.isEmpty()` AND `filterState.isActive`, show a different empty state message: "No flights match your search." with a "Clear filters" button. The existing empty-logbook state (icon + "No flights logged" + Add button) appears only when the logbook truly has no entries (i.e., `filterState.isActive == false && flights.isEmpty()`). Disambiguate: check `filterState.isActive` to choose which empty state to show.

**StatsRow label update:** When a filter is active, add a subtle label "(filtered)" beside the counts so the user knows the numbers reflect the current view:

```
47 Flights (filtered)    38,210 NM Total (filtered)
```

### Step 6: Wire up debounce in ViewModel (already in Step 4)

The `debounce(300L)` on `_filterState` in the `combine` call handles real-time search without firing on every keystroke. Note: debounce applies to ALL filter state changes (seat class, year, sort). For chip toggles this is instant enough (300ms is imperceptible), but if desired, the search query can be debounced separately by splitting it into its own `MutableStateFlow<String>` and combining. The simpler single-state debounce is acceptable.

### Step 7: Write unit tests

Create `LogbookViewModelSearchTest` (or extend an existing test file) covering the edge cases below.

---

## API Integration Details

No external API involved. All data comes from Room via the existing `LogbookRepository`.

---

## Edge Cases to Test

### E1: Empty search query — all flights shown
- Input: `searchQuery = ""`
- Expected: `matchesSearch` returns `true` for all flights; full list shown in default sort order

### E2: Search matches flight number (case-insensitive)
- Input: `searchQuery = "jl5"`, flight has `flightNumber = "JL5"`
- Expected: flight appears in results
- Input: `searchQuery = "JL"`, flights with "JL5", "JL006", "JL789"
- Expected: all three match

### E3: Search matches departure code
- Input: `searchQuery = "NRT"`, flight has `departureCode = "NRT"`, `arrivalCode = "HND"`
- Expected: flight matches

### E4: Search matches arrival code but not departure
- Input: `searchQuery = "HND"`, flight has `departureCode = "NRT"`, `arrivalCode = "HND"`
- Expected: flight matches (arrival code checked independently)

### E5: Search matches notes
- Input: `searchQuery = "window seat"`, flight has `notes = "Great window seat view of Mt. Fuji"`
- Expected: flight matches

### E6: Search matches nothing
- Input: `searchQuery = "ZZZZ"`, no flights have this string in any field
- Expected: `flights` emits empty list; no-results empty state shown (NOT the add-flight empty state)

### E7: Logbook empty + no filter active = empty-logbook state
- Precondition: 0 flights in logbook, `filterState.isActive == false`
- Expected: "No flights logged" empty state with Add button, NOT "No flights match" state

### E8: Filter active but logbook has flights — no-match empty state
- Precondition: 5 flights, none in "First" class
- Action: user selects "First" seat class chip
- Expected: "No flights match your search." empty state with "Clear filters" button

### E9: Seat class filter toggle — selecting same chip deselects it
- Action: tap "Business" chip → `selectedSeatClass = "Business"`
- Action: tap "Business" chip again → `selectedSeatClass = null`
- Expected: list returns to unfiltered state

### E10: Year filter — UTC year boundary
- Flight has `departureTimeUtc` = 2024-12-31 23:30 UTC (UTC year = 2024)
- User is in UTC+9 (Japan). Local date = 2025-01-01
- Filter year chip shows "2024" for this flight, not "2025"
- Expected: selecting "2024" year chip includes this flight; selecting "2025" does not
- Rationale: year is derived from UTC epoch, consistent with statistics screen

### E11: Year chip list derived from DAO, not from filtered results
- Precondition: user has flights in 2023, 2024, 2025
- Action: user searches "NRT" — filtered results only contain 2025 flights
- Expected: year chips still show "2023", "2024", "2025" (from `availableYears` DAO query, not from `flights`)

### E12: Seat class chip list shows only classes present in logbook
- Precondition: user has only "Economy" and "Business" flights (no "First")
- Expected: chip list shows "Economy", "Business" only — "First" chip not shown

### E13: Sort by oldest first
- Input: 3 flights with `departureTimeUtc` = T1 < T2 < T3, sort = OLDEST_FIRST
- Expected: list order is T1, T2, T3

### E14: Sort by longest distance — flights with null distanceNm sorted last
- Input: flights with `distanceNm` = 5000, null, 3000, null
- Expected: order = 5000, 3000, null, null (nulls treated as -1)

### E15: Sort by longest distance combined with search filter
- Input: search "NRT" returns 3 flights, sort = LONGEST_DISTANCE
- Expected: the 3 matching flights are sorted by distance, not the full list

### E16: Clear button resets all state
- Precondition: `searchQuery = "NRT"`, `selectedSeatClass = "Business"`, `selectedYear = "2024"`, `sortOrder = OLDEST_FIRST`
- Action: `clearFilters()`
- Expected: `filterState == LogbookFilterState()` (all defaults); `flights` re-emits full sorted-newest-first list

### E17: Clear button visibility
- `filterState.isActive == false` → Clear button NOT shown in TopAppBar
- After typing any non-blank query → Clear button appears
- After selecting any chip → Clear button appears
- After changing sort order from NEWEST_FIRST → Clear button appears
- After clearFilters() → Clear button hidden

### E18: Debounce — rapid typing does not cause excessive filtering
- Action: user types "N", "NR", "NRT" in quick succession (within 300ms)
- Expected: only one filter pass triggered (for "NRT"); intermediate states "N" and "NR" are debounced away
- Test approach: use `TestCoroutineScheduler` to advance time and verify combine emissions

### E19: StatsRow shows filtered counts
- Precondition: 10 flights total (5 Economy, 5 Business), 4 of Economy are NRT→HND
- Action: search "NRT"
- Expected: StatsRow shows "4 Flights" and the sum of those 4 flights' distances

### E20: Filter active + undo delete restores flight into filtered results if it matches
- Precondition: filter = "NRT", list shows 3 NRT flights
- Action: delete one NRT flight, then undo
- Expected: the restored flight re-appears in filtered results (because `repository.getAll()` re-emits, combine re-fires)

### E21: Search query with leading/trailing whitespace
- Input: `searchQuery = "  NRT  "`
- Expected: `matchesSearch` trims before comparing; NRT flights found correctly

### E22: Search query with special regex/SQL characters
- Input: `searchQuery = "%", "_", "'"` (SQL wildcard / injection characters)
- Expected: no crash, no SQL injection (in-memory string `.contains()` is safe; no SQL LIKE clause used)
- Note: this is why in-memory filtering is preferred over SQL LIKE for the search field

### E23: Very large logbook (performance boundary)
- Scenario: 2,000 flights, user types in search bar
- Expected: combine + filter pipeline completes within one frame (~16ms) on a mid-range device; no ANR
- Test: benchmark with a fake list of 2,000 `LogbookFlight` objects in a unit test; measure filter duration

### E24: filterState debounce does not delay chip taps visibly
- Action: user taps "Business" chip
- Expected: chip shows selected state immediately (optimistic UI via `filterState` emission), filtered list updates 300ms later
- Note: since `_filterState` emits immediately and debounce is on the combine, the chip UI itself (driven by `filterState`) updates instantly while the list waits 300ms. Verify this behavior is correctly wired.

### E25: Rotate screen mid-search — state preserved
- Action: user types "NRT", rotates device
- Expected: search query and filter state survive rotation (ViewModel survives configuration changes by default; no extra SavedStateHandle needed since this is not a critical navigation argument)

---

## Dependencies

| Item | Status | Notes |
|---|---|---|
| `LogbookFlightDao` | Exists, extend | Add 2 new Flow queries (getDistinctYears, getDistinctSeatClasses) |
| `LogbookRepository` | Exists, extend | Forward 2 new queries |
| `LogbookViewModel` | Exists, rewrite core | Replace `flights` StateFlow; add filter state + helpers |
| `LogbookScreen` | Exists, extend | Add search bar, chips row, sort menu, clear button, no-results state |
| `LogbookUiState` | Exists, unchanged | Sheet/delete/snackbar state unaffected |
| Room migration | Not needed | No entity changes |
| New dependencies | None | `kotlinx.coroutines.flow.debounce` is already in stdlib |

---

## Risks

1. **debounce import:** `Flow.debounce` requires `kotlinx-coroutines-core` 1.4+, which is almost certainly already present. Verify `build.gradle` before writing the combine block.

2. **`flightCount` and `totalDistanceNm` change semantics:** These now reflect the filtered list, not the full logbook total. This is intentional and more useful, but it means the StatsRow numbers will change as the user types. The developer must update the TopAppBar title or StatsRow label to make this clear (the "(filtered)" label in Step 5). If a future feature needs total-logbook stats regardless of filter, a separate `totalFlightCount` / `totalDistanceNm` StateFlow can be added from `repository.getCount()` / `repository.getTotalDistanceNm()` directly — keep those DAO queries in place.

3. **Year filter is UTC-based:** The year shown in chips and used for filtering is derived from UTC epoch milliseconds, consistent with the statistics screen. Users in UTC+14 or UTC-12 extremes may see a flight appear in a different year than their local date. This is a known, accepted limitation documented in E10.

4. **Filter state is not persisted across app restarts:** Clearing the app from recents resets all filters. This is acceptable for Phase 1. If persistence is wanted later, a `DataStore` preference can be added.

5. **Chip overflow:** If a user has flights in 10+ years or 5 seat classes, the chip row gets wide. The horizontal scroll handles this, but visual testing on a small-screen device (e.g. Pixel 4a at 5.8") is recommended.
