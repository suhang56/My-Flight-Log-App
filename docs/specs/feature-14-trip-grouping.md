# Feature 14 — Trip Grouping / Timeline

**Scope:** Medium | **Sprint:** 3 | **Dependencies:** F10 (AirportRepository for city names)
**No Room migration.** Trips are computed in-memory from existing `LogbookFlight` data.

---

## What Exists (do not duplicate)

- `LogbookViewModel.flights` — `StateFlow<List<LogbookFlight>>`, filtered + sorted, already sorted by `departureTimeUtc`
- `LogbookFilterState` — controls search, year, seat class, sort order
- `LogbookScreen` — `LazyColumn` of `FlightCard` items
- `AirportRepository.getByIata()` — provides city names (from F10)

---

## In-Memory Data Model

```kotlin
data class TripGroup(
    val id: String,                     // stable key: first flight's id as string
    val legs: List<LogbookFlight>,      // chronological, at least 1 flight
    val label: String,                  // "NRT → ORD → LAX" (unique airports in order)
    val dateRange: String,              // "Mar 20 – Mar 27, 2026"
    val totalDistanceNm: Int,           // sum of non-null distanceNm
    val totalDurationMin: Long?,        // sum of per-leg durations, null if any leg missing arrivalTime
    val isExpanded: Boolean = true
)
```

### Grouping Algorithm (`TripGrouper.kt`, pure function)

Input: `List<LogbookFlight>` sorted oldest-first (IMPORTANT: sort before grouping regardless of display sort order).

```kotlin
object TripGrouper {
    private const val GAP_MS = 48L * 60 * 60 * 1000  // 48 hours

    fun group(flights: List<LogbookFlight>): List<TripGroup>
}
```

Algorithm:
1. Sort by `departureTimeUtc` ascending
2. Walk flights sequentially — start a new group when gap between current flight's `departureTimeUtc` and previous flight's `departureTimeUtc` (or `arrivalTimeUtc` if available) exceeds 48h
3. Build `TripGroup` for each group: label = unique airports in visit order (dep of leg 1, then arr of each leg, deduplicating consecutive duplicates), city names resolved from `AirportRepository`
4. Return groups in display order (reversed for NEWEST_FIRST, natural for OLDEST_FIRST)

**Label construction example:** legs NRT→ORD, ORD→LAX → unique ordered airports = [NRT, ORD, LAX] → "NRT → ORD → LAX". If only 1 leg: "NRT → ORD".

---

## ViewModel Changes

Add to `LogbookViewModel`:

```kotlin
// Toggle between flat list and trip view
private val _tripViewEnabled = MutableStateFlow(false)
val tripViewEnabled: StateFlow<Boolean> = _tripViewEnabled.asStateFlow()

// Per-trip expanded state (key = TripGroup.id)
private val _expandedTrips = MutableStateFlow<Set<String>>(emptySet())  // empty = all expanded

// Trip-grouped view — recomputes whenever filtered flights change
val tripGroups: StateFlow<List<TripGroup>> = combine(flights, _expandedTrips) { f, expanded ->
    TripGrouper.group(f).map { trip ->
        trip.copy(isExpanded = trip.id !in expanded)  // not in set = expanded
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun toggleTripView() { _tripViewEnabled.update { !it } }
fun toggleTripExpanded(tripId: String) {
    _expandedTrips.update { set -> if (tripId in set) set - tripId else set + tripId }
}
```

**Note:** Trip view is disabled automatically when any filter/search is active (`filterState.isActive == true`) — grouping by trip while searching would be confusing. Show flat list instead with a "Clear filters to see trip view" hint.

---

## UI Changes (`LogbookScreen.kt`)

### View toggle

Add a `SegmentedButton` row (or `IconToggleButton`) below the existing filter chips:
- "List" icon (flat view) and "Trip" icon (grouped view)
- Disabled / greyed out when `filterState.isActive`

### Trip view in LazyColumn

Replace flat `items(flights)` with conditional rendering:

```
if tripViewEnabled && !filterState.isActive:
    items(tripGroups, key = { it.id }) { trip ->
        TripHeader(trip, onToggle = { viewModel.toggleTripExpanded(trip.id) })
        if (trip.isExpanded):
            items(trip.legs, key = { "leg_${it.id}" }) { flight ->
                FlightCard(flight, indented = true, onClick = ...)
            }
else:
    items(flights) { FlightCard(...) }  // existing flat list
```

### TripHeader composable (new)

```
┌─────────────────────────────────────────────────┐
│  NRT → ORD → LAX           Mar 20 – Mar 27      │
│  2 flights  •  6,043 NM  •  14h 30m        [v]  │
└─────────────────────────────────────────────────┘
```

- Tapping anywhere on header toggles expand/collapse
- Chevron icon rotates 180° when collapsed (animate with `animateFloatAsState`)
- `isExpanded = false` → legs hidden, chevron points right

---

## Files to Create / Edit

| Action | File |
|---|---|
| Create | `data/trips/TripGrouper.kt` — pure grouping algorithm |
| Create | `data/trips/TripGroup.kt` — data class |
| Create | `ui/logbook/TripHeader.kt` — composable |
| Edit | `ui/logbook/LogbookViewModel.kt` — add `tripViewEnabled`, `tripGroups`, `_expandedTrips`, toggle functions |
| Edit | `ui/logbook/LogbookScreen.kt` — view toggle button, conditional LazyColumn rendering |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| 0 flights | `tripGroups` = empty list, no crash |
| 1 flight | 1 trip with 1 leg; label = "DEP → ARR" |
| 2 flights with gap < 48h | Grouped into 1 trip |
| 2 flights with gap exactly 48h | Boundary: new trip starts at exactly 48h (use `> GAP_MS`, not `>=`) |
| 2 flights with gap > 48h | 2 separate trips |
| Flights sorted OLDEST_FIRST display order | Grouper always sorts ascending internally before grouping; display order applied after |
| LONGEST_DISTANCE sort active with trip view | Trip view disabled (filter active equivalent); flat list shown |
| Flight with null `arrivalTimeUtc` | Gap computed from `departureTimeUtc` of next flight vs `departureTimeUtc` of this flight (not arrival) |
| Multi-leg trip: ORD→LAX, LAX→ORD | Label = "ORD → LAX → ORD" (do NOT deduplicate non-consecutive repeats) |
| All flights in one trip (frequent short-hop user) | 1 trip group with all flights; expanded by default |
| `filterState.isActive = true` | Trip view toggle disabled; flat list shown; hint text displayed |
| User collapses trip, then adds a new flight | New flight appears in correct trip; expanded state preserved for other trips |
| City name lookup from AirportRepository returns null | Fall back to IATA code in label (e.g. "NRT → ORD" instead of "Tokyo → Chicago") |
| Total duration: one leg has null `arrivalTimeUtc` | `totalDurationMin = null`; UI shows "—" for duration |
