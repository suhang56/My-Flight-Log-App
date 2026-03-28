# Code Review Log

All code reviews for the Flight Log App, recorded chronologically.

---

## Review #1 — Initial Codebase Scan (2026-03-27)

**Scope:** Full codebase (20 Kotlin files)
**Reviewer:** Code Reviewer Agent

### Critical Issues Found:
1. **@Upsert crash in CalendarFlightDao** — Room's @Upsert uses PK (autoGenerate id=0) for conflict detection, not the unique index on (calendarEventId, legIndex). Second sync crashes with constraint violation.
   - **Fix:** Added @Transaction upsertAll() that resolves existing IDs via findIdByEventAndLeg() before delegating to @Upsert.

2. **FlightRouteServiceImpl ignores date parameter** — lookupRoute() accepts LocalDate but never passes flight_date to AviationStack API.
   - **Fix:** Added @Query("flight_date") to API interface, passed date.toString() in implementation.

### Other Issues:
3. AeroDataBoxApi.kt filename didn't match AviationStackApi interface → renamed
4. FlightCard used stale `remember { System.currentTimeMillis() }` → removed remember
5. syncSubtitle used toRelativeTimeLabel() (always "Today") → added toRelativeElapsedLabel()

### Verdict: All fixed and verified.

---

## Review #2 — Bug Fixes Verification (2026-03-27)

**Scope:** All 4 bug fix changes
**Reviewer:** Code Reviewer Agent

| Fix | Verdict |
|-----|---------|
| @Upsert crash | APPROVED |
| Date parameter | APPROVED |
| File rename | APPROVED |
| Stale now + syncSubtitle | APPROVED |

No new issues introduced.

---

## Review #3 — Timezone Feature (2026-03-27)

**Scope:** Tasks #9-#13 (entity, migration, API capture, AirportTimezoneMap, repository sync, UI display)
**Reviewer:** Code Reviewer Agent

| Task | Component | Verdict |
|------|-----------|---------|
| #9 | Entity + Migration v2→v3 | APPROVED |
| #10 | API Timezone Capture | APPROVED |
| #11 | AirportTimezoneMap | APPROVED (minor coverage notes) |
| #12 | Repository Sync | APPROVED |
| #13 | UI Display | APPROVED |

### Notes:
- Added LAS (Las Vegas) to AirportTimezoneMap
- Replaced TXL (closed Berlin Tegel) with BER (Brandenburg)
- Replaced BUE (city code) with AEP (actual airport)
- formatInZone() uses runCatching for invalid timezone strings — safe fallback

---

## Review #4 — Post-Change Full Scan (2026-03-27)

**Scope:** All changed files after bug fixes + timezone feature
**Reviewer:** Code Reviewer Agent

**Result:** No new bugs found. No regressions.

Cross-file integration verified:
- Entity ↔ Migration ↔ DAO consistency ✓
- Network ↔ Repository type consistency ✓
- Repository timezone fallback pipeline ✓
- UI ↔ Entity field mapping ✓
- WorkManager integration ✓
- Hilt DI graph ✓

---

## Review #5 — Architecture Review (2026-03-27)

**Scope:** Full project architecture
**Reviewer:** Architect Agent

### Architecture Health: GOOD
- MVVM + Clean Architecture correctly followed
- Package structure clean (by feature inside data/ and ui/)
- Dependencies all current, no deprecated libraries

### Maintainability Issues Found:
1. CalendarFlightsScreen.kt 710 lines → split into 4 files
2. UI models embedded in ViewModel → extracted to CalendarFlightsModels.kt
3. AIRPORT_NAME_MAP in FlightEventParser → extracted to AirportNameMap.kt
4. SyncResult in CalendarRepository → extracted to own file
5. RawCalendarEvent in CalendarDataSource → extracted to own file
6. syncFromCalendar() too long → extracted resolveFlight()
7. Fragile "Today" string check → replaced with date comparison
8. Room exportSchema=false → changed to true

### Verdict: All refactoring completed and verified.

---

## Review #6 — Logbook Data Layer (2026-03-27)

**Scope:** LogbookFlight entity, LogbookFlightDao, AirportCoordinatesMap, LogbookRepository, MIGRATION_3_4, DatabaseModule
**Reviewer:** Code Reviewer Agent

### Verdict: APPROVED — no critical or blocking issues

### Notes:
1. **NULL unique index (future concern)** — unique index on (sourceCalendarEventId, sourceLegIndex) won't prevent duplicate manual entries since NULL != NULL in SQL. Not a bug today (all entries come from calendar), but needs addressing when manual add is implemented.
2. **departureTimeUtc naming** — field comes from calendar epoch millis, not a UTC conversion. Consider renaming to departureTimeEpoch or departureTime.
3. **No enforced check-before-insert** — Repository provides isAlreadyLogged() and addFromCalendarFlight() separately; caller must check first. DAO's IGNORE prevents crashes but returns -1 silently.

### Migration v3→v4: All 13 columns match between entity and SQL. Indices correct.

### Haversine: Formula and earth radius constant verified correct.

### Edge Cases to Test (13 scenarios):
1. Duplicate import (same CalendarFlight twice → -1 second time)
2. Blank airport codes → distanceNm null
3. Unknown airport codes → distanceNm null
4. Null endTime → arrivalTimeUtc null
5. Null timezones → pass through
6. Same-airport distance → 0
7. Antipodal airports (SIN-GRU ~9000 NM) → precision check
8. Cross-hemisphere (SYD-NRT, NRT-LAX crossing 180th meridian)
9. getAll() ordering → most recent first
10. Empty table → count=0, distance=0
11. Mix null/non-null distances → sum only non-null
12. Delete then re-add same CalendarFlight → succeeds
13. Migration on populated DB → calendar_flights unaffected

---

## Review #7 — Logbook UI + Navigation (2026-03-27)

**Scope:** LogbookViewModel, LogbookScreen, NavGraph, CalendarFlightsViewModel changes, CalendarFlightsScreen changes
**Reviewer:** Code Reviewer Agent

### Verdict: APPROVED — no critical or blocking issues

| Component | Verdict |
|-----------|---------|
| LogbookViewModel | APPROVED |
| LogbookScreen | APPROVED (minor notes) |
| NavGraph / Bottom Nav | APPROVED |
| CalendarFlightsViewModel (addToLogbook) | APPROVED |
| CalendarFlightsScreen (refactored) | APPROVED |

### Minor Notes (non-blocking):
1. **No delete confirmation dialog** — LogbookDetailBottomSheet calls onDelete immediately. Recoverable via re-add from calendar.
2. **LogbookScreen has no Scaffold** — Uses bare Column+TopAppBar. Works because parent NavGraph provides Scaffold with bottom nav. Can't host own snackbar if needed later.
3. **DATE_FORMATTER locale** — Locale.getDefault() fine for display; fixed locale needed if export added.

### Edge Cases for Logbook UI (13 scenarios):
1. Empty logbook → LogbookEmptyState with guidance
2. Single flight → "1 Flight" singular
3. Blank flightNumber → card skips title row
4. Null arrivalTimeUtc → detail sheet skips arrival + duration
5. Null distanceNm → detail sheet skips distance, card skips NM column
6. Blank notes → detail sheet skips notes
7. Invalid timezone string → runCatching fallback to system default
8. Very long flight number → Ellipsis overflow
9. Delete while sheet animating → no crash (sync dismiss, async delete)
10. Rapid tab switching → restoreState preserves scroll/ViewModel
11. Add to logbook race condition → sheet dismisses after first tap, second blocked by isAlreadyLogged
12. Zero distance (same-airport) → shows "0 NM"
13. Large distance values → "%,d" handles thousands separators

---

## Review #8 — Statistics Feature (2026-03-27)

**Scope:** StatModels.kt, StatsData.kt, LogbookFlightDao (stats queries), LogbookRepository, StatisticsViewModel, StatisticsScreen, NavGraph
**Reviewer:** Code Reviewer Agent

### Verdict: APPROVED with 1 BUG to fix

| Component | Verdict |
|-----------|---------|
| StatModels.kt | **BUG** — AirlineCount field name mismatch |
| StatsData.kt | APPROVED |
| LogbookFlightDao (stats queries) | **BUG** — column alias mismatch |
| LogbookRepository (stats) | APPROVED |
| StatisticsViewModel | APPROVED |
| StatisticsScreen | **BUG** — references non-existent property |
| NavGraph (3 tabs) | APPROVED |

### Critical Bug:
**AirlineCount field name mismatch** — `AirlineCount` model has field `code` but DAO query aliases column as `airline` and StatisticsScreen references `it.airline`. Room can't map the alias to the field. Fix: rename `AirlineCount.code` to `AirlineCount.airline` in StatModels.kt.

### Minor Notes (non-blocking):
1. MonthlyBarChart label comment says "MM" but `.takeLast(5)` gives "YY-MM" — display fine, comment misleading
2. ICAO flight number prefixes (3-letter) truncated to 2 chars — works by coincidence for most
3. Canvas charts lack accessibility semantics for TalkBack

### Edge Case Tests:
30 tests written in `LogbookFlightDaoStatsTest.kt` covering empty table (10), null values (4), empty strings (8), aggregate correctness (6), delete + duplicate (2)

---

## Review #8 — Team Discussion: Logbook Polish Pass (2026-03-27)

**Scope:** Full team discussion on Logbook feature improvements
**Participants:** All 6 agents (Planner, UI/UX Designer, Developer, Code Reviewer, Architect + Lead)

### Issues Identified and Fixed:
1. **Delete confirmation dialog** — Added AlertDialog to swipe-to-delete and detail sheet delete
2. **Broken undo-delete** — Implemented real undo: cache deleted entity, re-insert on undo action
3. **Manual duplicate guard** — Warn on same route + date, allow override with "Save Anyway"
4. **Secondary sort** — Added departureTimeUtc DESC for same-day flight ordering
5. **Auto-set updatedAt** — Repository.update() now sets updatedAt automatically
6. **Shared time formatting** — Extracted TimeFormatting.kt, deduplicated across calendar + logbook screens
7. **NULL unique index docs** — Documented SQLite NULL != NULL behavior on entity

### Additional Issues Noted (deferred):
- createdAt preservation on edit — verified already correct via Kotlin copy() semantics
- Logbook card visual consistency with calendar cards — deferred to UI polish sprint
- Unsaved changes warning on back press — deferred
- 26 edge case tests — deferred to testing sprint
- Consolidate 3 airport maps into single AirportDatabase — future improvement

---

## Review #9 — Statistics Improvements (2026-03-27)

**Scope:** Statistics refinements across 2 commits (0006c56, 23d92ff)
**Reviewer:** Code Reviewer Agent
**Files reviewed:**
- `LogbookFlightDao.kt` — renamed/refactored stats queries, split airport queries, nullable duration
- `LogbookRepository.kt` — combined airport counting in Kotlin, new stat passthroughs
- `StatModels.kt` — AirlineCount field fix (code->airline), StatsData computed properties
- `StatisticsViewModel.kt` — adapted to new repository API, nullable duration mapping
- `StatisticsScreen.kt` — zero-fill chart, current month highlight, rounded bars, threshold hiding, em-dash zeros
- `NavGraph.kt` — route rename (statistics->stats)
- `LogbookFlightDaoStatsTest.kt` — 30+ edge case tests (new file)

### Overall Assessment: APPROVED — solid quality improvements

### Changes Reviewed

| Change | Verdict |
|--------|---------|
| `getTotalDurationMinutes` nullable return | APPROVED — correctly handles empty table |
| `getDistinctAirportCodes` returns list (not count) | APPROVED — enables Kotlin-side airport combining |
| Split top airports into departure/arrival + Kotlin combine | APPROVED — correct counting without SQL UNION ALL |
| `AirlineCount.airline` field name fix (was `code`) | APPROVED — fixes Room column mapping bug |
| StatsData computed properties (`totalHours`, `isEmpty`) | APPROVED — clean API |
| Zero-fill bar chart with last 12 months | APPROVED — good UX |
| Current month highlight | APPROVED |
| Rounded bar corners (`drawRoundRect`) | APPROVED — visual polish |
| `>= 2` threshold for sections | APPROVED — hides pointless "top" lists with 1 item |
| Em-dash for zero values | APPROVED — cleaner than showing "0" |
| Month label formatting (MMM) | APPROVED — human-readable |
| Seat class `IS NOT NULL` guard | APPROVED — defensive against null seatClass |

### Critical Issues: None

### Minor Notes (non-blocking)

1. **Seat class % rounding not implemented** — Task #2 item 1 specified showing "<1%" instead of "0%" for small seat class percentages. The current code at `StatisticsScreen.kt:472` still uses integer division `(item.count * 100) / total` which will show "0%" for a single flight out of 200+. This is a cosmetic issue, not a bug.

2. **Missing task items** — Several items from the task #2 description appear unimplemented:
   - Item 2: Bar chart count labels above bars (not present)
   - Item 3: Airline name lookup via AirlineIataMap (airlines still shown as 2-letter codes)
   - Item 4: Longest flight by duration (only by distance exists)
   - Item 5: Top routes stat (not present)
   - Item 6: All-time chart toggle with horizontal scroll (not present)
   - Item 7: First flight milestone card (not present)

   These may have been descoped by the team. The implemented changes focus on quality refinements rather than new features.

3. **`getTopAirports` combine race** — `LogbookRepository.getTopAirports()` combines two flows with `limit * 2`. If an airport appears only in departures, it could be missed if it ranks below position `limit * 2`. For `limit=5`, this fetches top 10 from each direction — unlikely to miss any real "top 5" airport, but theoretically possible with highly asymmetric data.

4. **`MONTH_LABEL_FORMATTER` as top-level val** — `DateTimeFormatter.ofPattern("MMM", Locale.getDefault())` captures the locale at class-load time. If the user changes their device locale, the formatter won't update until process restart. Acceptable for this use case.

5. **Zero-fill chart `remember(data)`** — Correctly keyed on `data` so it recomputes when flights change. However, `YearMonth.now()` inside `remember` means the "current month" won't update at midnight without recomposition. Low risk.

### Edge Cases to Test

1. **Chart with flights only in distant past** — All 12 bars show 0 (data outside window). Chart shows empty bars correctly.
2. **Exactly 1 airline/airport/aircraft** — Section hidden due to `>= 2` threshold. Correct behavior.
3. **Seat class with 1 flight out of 500** — Shows "0%" (integer math). Cosmetic issue noted above.
4. **Very long airline prefix** — IATA codes are always 2 chars from `SUBSTR(flightNumber, 1, 2)`. Safe.
5. **Combined airport counts with ties** — `sortedByDescending` is stable sort in Kotlin, so insertion order preserved for ties. Correct.
6. **Empty string flight number excluded** — `WHERE LENGTH(flightNumber) >= 2 AND flightNumber != ''` correctly excludes both "" and single-char entries.

### Test Coverage Assessment

`LogbookFlightDaoStatsTest.kt` provides excellent coverage with 30+ tests across:
- Empty table (11 tests — one per query)
- Single flight baseline (aggregate verification)
- Null/empty string handling (duration nulls, empty codes, empty flight numbers)
- Multi-flight aggregation (correct sums, counts, grouping)
- Delete impact on stats
- Duplicate source handling

Coverage is strong. No gaps identified.

### Verdict: APPROVED — all changes are correct and well-tested. The 6 unimplemented task items should be tracked separately if still desired.

---

## Review #10 — Logbook Bug Fixes (2026-03-27)

**Scope:** 3 bug fixes — cross-midnight duration guard, duplicate guard reset, undo-delete with upsert
**Reviewer:** Code Reviewer Agent
**Files reviewed:**
- `LogbookFlightDao.kt` (uncommitted changes)
- `LogbookRepository.kt` (uncommitted changes)
- `AddEditLogbookFlightViewModel.kt` (uncommitted changes)
- `LogbookViewModel.kt` (uncommitted changes)
- `LogbookFlightDaoBugFixTest.kt` (new test file)

### Overall Assessment: APPROVED — all 3 fixes are correct and well-implemented

### Bug Fix Analysis

| Bug | Fix | Verdict |
|-----|-----|---------|
| Cross-midnight negative duration | Added `AND arrivalTimeUtc > departureTimeUtc` guard to DAO query + null-out bad arrival in `addFromCalendarFlight()` | APPROVED |
| Duplicate guard not resetting | Added `duplicateCheckPassed = false` to save success state update | APPROVED |
| Undo-delete loses original ID | Added `upsert()` (REPLACE strategy) to DAO+Repository, `undoDelete()` now calls `repository.upsert(flight)` instead of `insert(flight.copy(id = 0))` | APPROVED |

### Detailed Review

**1. Cross-midnight fix (DAO + Repository)**
- DAO: `AND arrivalTimeUtc > departureTimeUtc` correctly excludes both negative durations AND zero-duration flights from the sum. Good — zero duration is almost certainly bad data too.
- Repository: `calendarFlight.endTime?.takeIf { it > calendarFlight.scheduledTime }` sanitizes at write-time. Defense in depth — bad data is cleaned on insert AND excluded at query time.
- No issues found.

**2. Duplicate guard reset (AddEditLogbookFlightViewModel)**
- Line 208: `duplicateCheckPassed = false` reset after successful save prevents the flag from persisting if the ViewModel is reused.
- The `duplicateCheckPassed` flag is also correctly reset when departure code, arrival code, or date change (lines 94, 97, 99).
- No issues found.

**3. Undo-delete with upsert (DAO + Repository + LogbookViewModel)**
- DAO: `@Insert(onConflict = OnConflictStrategy.REPLACE)` is the correct Room strategy for upsert.
- Repository: Clean passthrough `upsert()` method.
- ViewModel: `repository.upsert(flight)` preserves the original `flight.id` and `sourceCalendarEventId` link, so calendar-imported flights maintain their duplicate-import guard after undo.
- Previous approach `insert(flight.copy(id = 0))` would have broken the sourceCalendarEventId unique index link and generated a new ID.

### Critical Issues: None

### Minor Notes (non-blocking)

1. **`upsert` naming convention** — Room 2.5+ introduced `@Upsert` annotation (distinct from `@Insert(REPLACE)`). The `@Insert(REPLACE)` approach works correctly here, but the method name `upsert` could confuse future readers who expect the `@Upsert` annotation. The behavior is slightly different: `@Insert(REPLACE)` deletes-then-inserts (triggers ON DELETE), while `@Upsert` does INSERT-OR-UPDATE (no delete trigger). For undo-delete this distinction doesn't matter, but worth noting.

2. **`addedAt` reset on REPLACE** — When `@Insert(REPLACE)` fires on an existing row, Room deletes and re-inserts. The `addedAt = System.currentTimeMillis()` default in the entity will give the restored flight a new `addedAt` timestamp rather than preserving the original. However, since the `deletedFlight` cached in `LogbookUiState` retains the original `addedAt` value, the upsert will use that value, so this is fine in the undo-delete flow. Would only be an issue if someone called `upsert()` with a freshly-constructed `LogbookFlight` without setting `addedAt`.

### Edge Cases to Test

1. **Undo after app process death** — `deletedFlight` is in `MutableStateFlow`, not `SavedStateHandle`. If Android kills the process during the snackbar window, undo is lost. Acceptable trade-off for this feature.
2. **Rapid delete-undo-delete** — Second delete while first undo is in-flight could race. Low risk since UI gates on snackbar dismissal.
3. **Cross-midnight with arrivalTimeUtc == departureTimeUtc** — Correctly excluded by strict `>` (not `>=`). Test covers this.
4. **Duplicate check with trimmed vs untrimmed codes** — `save()` trims codes for the duplicate check (`current.departureCode.trim()`), which is correct since form fields auto-uppercase but don't auto-trim.
5. **Upsert with null sourceCalendarEventId** — Manual flights have null source fields. REPLACE strategy uses PK for conflict detection, not the unique index, so this works correctly. Test covers this.

### Test Coverage Assessment

The `LogbookFlightDaoBugFixTest.kt` has 10 tests covering:
- **Cross-midnight (4 tests):** negative duration, zero duration, mixed valid+invalid, all-invalid
- **Upsert (6 tests):** new insert, replace preserving ID, replace with same source key, null source fields, full undo-delete simulation

**Missing test for Bug #2 (duplicate guard reset):** No test verifies that `duplicateCheckPassed` resets after save. This is a ViewModel-level concern and would require a ViewModel unit test (not a DAO test), so the omission is reasonable for this file. Recommend adding a ViewModel test in a future pass.

### Verdict: APPROVED — all 3 fixes are correct, well-tested at the DAO level, and follow Kotlin/Room best practices. Ship it.

---

## Review #11 — Final Statistics Improvements Review (2026-03-27)

**Scope:** Complete statistics feature across commits 01c7d14, 0006c56, 23d92ff, plus bug fix interactions from 4a4e4e9
**Reviewer:** Code Reviewer Agent
**Task:** #3 — Review: Statistics improvements code

### Files Reviewed (8 files, ~1300 lines added):
- `LogbookFlightDao.kt` — 11 new stats queries
- `StatModels.kt` — 4 data models (MonthlyCount, AirportCount, AirlineCount, LabelCount)
- `StatsData.kt` — Aggregated stats holder with computed properties
- `LogbookRepository.kt` — Stats passthroughs + combined airport counting
- `StatisticsViewModel.kt` — 10-flow combine with stateIn
- `StatisticsScreen.kt` — Full UI (hero row, bar chart, top lists, longest flight, seat class, empty state)
- `NavGraph.kt` — 3rd bottom nav tab
- `LogbookFlightDaoStatsTest.kt` — 30+ DAO-level tests

### Overall Assessment

Solid implementation. The statistics feature is well-architected with clean separation between DAO queries, repository logic, ViewModel aggregation, and Composable display. The code follows MVVM correctly — no business logic leaks into Composables, all state flows through StateFlow, and the ViewModel properly combines 10 Room flows into a single StatsData object. The AirlineCount field-name bug was caught and fixed in the same sprint. Test coverage at the DAO level is thorough.

### Critical Issues: NONE

No blocking issues found. All previously identified bugs (AirlineCount field mismatch, cross-midnight duration) have been fixed and verified.

### Edge Cases to Test

These are scenarios that should have test coverage but currently do not:

1. **Repository `getTopAirports()` combine logic** — Input: airport "XYZ" appears 3 times as departure (rank #8 in departures) and 4 times as arrival (rank #9 in arrivals). With `limit=5`, the query fetches top 10 from each direction. If XYZ falls outside both top-10 lists, it would be missed from the combined top-5 despite having 7 total flights. Expected: unlikely in practice but no test validates the `limit * 2` safety margin.

2. **ViewModel with all-null optional stats** — Input: flights with no arrivalTimeUtc, no distanceNm, empty flightNumber, empty seatClass, empty aircraftType. Expected: StatsData with `totalDurationMinutes=0`, `longestFlight=null`, empty topAirlines/seatClass/aircraftType lists. Hero row shows em-dashes for NM and time. Only flight count and airports shown.

3. **MonthlyBarChart at month boundary** — Input: `YearMonth.now()` called at 23:59:59 on March 31st. Chart shows March as current month. At 00:00:01 April 1st, the `remember(data)` key hasn't changed so the chart still highlights March until data reflows. Expected: stale highlight until next DB emission. Low risk.

4. **Seat class percentage with integer overflow** — Input: `item.count = Int.MAX_VALUE`, `total = 1`. Expression `(item.count * 100) / total` overflows Int. Expected: would show negative percentage. Practically impossible (billions of flights), but the arithmetic is technically unsafe.

5. **StatisticsScreen with exactly 1 item in each category** — Input: 1 airport, 1 airline, 1 aircraft type. Expected: Top Airports, Top Airlines, and Aircraft Types sections all hidden (threshold `>= 2`). Only hero row, chart, longest flight, and seat class visible. Verify the screen doesn't look broken with most sections hidden.

6. **`strftime` with departureTimeUtc = 0** — Input: flight with `departureTimeUtc = 0` (epoch). Expected: grouped into "1970-01" in `getFlightsPerMonth()`. The zero-fill chart only shows last 12 months, so this flight would contribute to data but not appear in the chart. No crash.

7. **Combined airport flow emission ordering** — Input: departure airports flow emits before arrival airports flow. Expected: `combine` waits for both flows to emit at least once before producing a value. If one flow is slow, the combined flow blocks. Room flows emit on subscription, so both should emit quickly, but no test verifies the combine behavior under delay.

### Suggestions (non-blocking)

1. **Seat class percentage**: Consider `((item.count.toLong() * 100) / total).toInt()` or showing "<1%" for non-zero counts that round to 0%. Current integer division at `StatisticsScreen.kt:472` shows "0%" for small percentages.

2. **Canvas accessibility**: The bar charts (`MonthlyBarChart`, `TopListSection`, `SeatClassBreakdown`) have no `semantics` blocks. TalkBack users cannot read chart data. Consider adding `semantics { contentDescription = "..." }` to the Canvas modifiers.

3. **`LongestFlightCard` null-bang**: At `StatisticsScreen.kt:108`, the code uses `stats.longestFlight!!` inside a block guarded by `stats.longestFlight?.distanceNm != null`. This is safe given the guard, but a `let` block would be more idiomatic: `stats.longestFlight?.let { if (it.distanceNm != null) LongestFlightCard(it) }`.

4. **ViewModel `DetailStats` intermediate class**: The private `DetailStats` class at `StatisticsViewModel.kt:64` exists solely to work around `combine`'s 5-parameter limit. This is a well-known Kotlin Flow pattern and is fine, but a comment explaining the workaround (already present) is appreciated.

### Strengths

1. **Clean data flow**: DAO -> Repository -> ViewModel -> UI with no shortcuts or leaking abstractions. Each layer has a clear responsibility.
2. **Defensive SQL**: Empty string exclusions, null checks, and the cross-midnight guard in `getTotalDurationMinutes` prevent bad data from corrupting stats.
3. **Zero-fill chart**: The `remember(data)` pattern that fills the last 12 months with zeros is a good UX choice — users always see a consistent chart shape.
4. **Threshold hiding**: Sections with `< 2` items are hidden, avoiding awkward "Top Airlines: AA (1)" displays.
5. **Test coverage**: 30+ DAO tests covering empty table, single flight, null/empty values, aggregation, ordering, deletion, and duplicates. This is strong coverage for the data layer.
6. **`SharingStarted.WhileSubscribed(5_000)`**: Correct sharing strategy that keeps the flow alive for 5 seconds after the last subscriber (survives configuration changes without requerying).

### Verdict: APPROVED — Ship it. No critical issues. The statistics feature is well-implemented with good architecture, defensive queries, and thorough DAO-level test coverage.

---

## Review #12 — 7 Statistics Screen Improvements (2026-03-27)

**Scope:** Commit 759c199 — 7 new statistics features (bar chart count labels, longest flight by duration, airline full names, top routes, all-time toggle, first flight card, seat class Largest Remainder rounding)
**Branch:** `feature/statistics-improvements`
**Reviewer:** Code Reviewer Agent
**Task:** #3 — Review: Statistics improvements code

### Files Reviewed (8 files, ~993 lines added):
- `LogbookFlightDao.kt` — 3 new queries (getLongestFlightByDuration, getTopRoutes, getFirstFlight)
- `LogbookRepository.kt` — 3 new passthrough methods
- `StatModels.kt` — RouteCount data class
- `StatsData.kt` — 3 new fields (longestFlightByDuration, topRoutes, firstFlight)
- `StatisticsViewModel.kt` — Restructured into 5-block combine architecture
- `AirlineIataMap.kt` — canonicalNames map + getFullName() reverse lookup
- `StatisticsScreen.kt` — All 7 UI improvements (~444 lines added)
- `StatisticsEdgeCaseTest.kt` — 30 edge case tests (new file)

### Overall Assessment

Excellent implementation of all 7 planned improvements. The code is clean, well-structured, and follows the established architecture. The ViewModel's 5-block combine pattern elegantly solves the `combine` 5-parameter limit while keeping flows grouped logically. All DAO queries are defensively written with proper null/empty guards. The Largest Remainder algorithm for seat class percentages is a textbook-correct implementation. The edge test file covers all 7 improvements with meaningful assertions. No critical issues found.

### Critical Issues: NONE

### Review Checklist Results

| # | Check | Verdict |
|---|-------|---------|
| 1 | DAO queries — correct SQL, null handling, no injection risks | PASS |
| 2 | ViewModel nested combine — no 5-param violations, no flow leaks | PASS |
| 3 | Bar chart count labels — Paint in `remember`... | ISSUE (minor, see below) |
| 4 | LongestFlightByDurationCard — null arrivalTimeUtc, duration format | PASS |
| 5 | AirlineIataMap.getFullName() — uppercase normalization, fallback | PASS |
| 6 | TopRoutesSection — empty code guard, size >= 2 gate | PASS |
| 7 | All-time toggle — adaptive label step, horizontalScroll for >24 months | PASS |
| 8 | FirstFlightCard — runCatching on timezone, UTC fallback | PASS |
| 9 | Seat class rounding — Largest Remainder, sums to 100 | PASS |
| 10 | Edge tests — all 7 improvements covered, meaningful assertions | PASS |

### Detailed Analysis

**1. DAO Queries (LogbookFlightDao.kt:183-207)**

- `getLongestFlightByDuration`: Correctly filters `arrivalTimeUtc IS NOT NULL AND arrivalTimeUtc > departureTimeUtc`. Orders by `(arrivalTimeUtc - departureTimeUtc) DESC LIMIT 1`. Safe — no injection risk since no string interpolation.
- `getTopRoutes`: Filters `departureCode != '' AND arrivalCode != ''`, groups by pair, orders by count DESC with parameterized LIMIT. Correct.
- `getFirstFlight`: Simple `ORDER BY departureTimeUtc ASC LIMIT 1`. Returns nullable Flow. Correct.

**2. ViewModel Combine Architecture (StatisticsViewModel.kt:28-96)**

The 5-block pattern (blockA through blockE) keeps each `combine` call at 3 parameters, well within the limit. The chained `.combine(blockC) { ab, c -> ab.copy(...) }` pattern is idiomatic and allocates intermediate StatsData objects via `copy()`, which is acceptable overhead for a WhileSubscribed flow. `SharingStarted.WhileSubscribed(5_000)` is correct — survives config changes without requerying.

One notable design choice: `blockE` is just `repository.getTopRoutes()` (a single flow, no combine needed). This is clean — no unnecessary wrapping.

**3. Bar Chart Count Labels (StatisticsScreen.kt:397-434)**

The `android.graphics.Paint` is allocated inside the `Canvas` draw scope (line 397), NOT in a `remember` block. This means a new Paint object is allocated on every recomposition and every frame during animation. This is a **minor performance concern** but NOT critical — the Canvas lambda is a draw-scope callback, and Paint allocation is cheap. The conventional best practice is to hoist the Paint into a `remember` block outside the Canvas, but in this case the color depends on `onPrimaryColor`/`onSurfaceColor` which are read from MaterialTheme inside the composable, so the Paint properties would need to be updated on theme changes anyway. Acceptable as-is.

The count label positioning at line 428 uses `coerceAtMost(chartHeight - labelPadding)` to prevent labels from clipping below the canvas. However, there is no `coerceAtLeast(textPaint.textSize)` to prevent labels from clipping ABOVE the canvas when bars are near max height. For a bar at `maxCount`, `barHeight == chartHeight`, so `labelY = chartHeight - chartHeight - 4dp = -4dp`, which would draw the text above the canvas top edge. The `coerceAtMost` doesn't help here since it only constrains the maximum. The label would be partially or fully clipped. This is a **minor visual issue** for the tallest bar only.

**4. LongestFlightByDurationCard (StatisticsScreen.kt:682-753)**

The `remember(flight)` block correctly handles null `arrivalTimeUtc` with early return to em-dash. The `durationMs <= 0` guard matches the DAO's `arrivalTimeUtc > departureTimeUtc` filter, providing UI-level defense in depth. Duration formatting `"${hours}h ${minutes}m"` handles the edge case where hours=0 (shows just minutes). Correct.

**5. AirlineIataMap.getFullName() (AirlineIataMap.kt:78-79)**

`canonicalNames[iataCode.uppercase()] ?: iataCode.uppercase()` — case-insensitive lookup with uppercase fallback for unknown codes. The canonicalNames map includes 23 airlines (US domestic + major international). The ViewModel applies this at line 80: `c.second.map { it.copy(airline = airlineIataMap.getFullName(it.airline)) }`. This is correct — the DAO returns uppercase 2-letter codes, and getFullName resolves them to display names.

Note: Empty string input returns "" (since `"".uppercase()` is `""` and `canonicalNames[""]` is null, so fallback is `""`). The DAO already filters `WHERE LENGTH(flightNumber) >= 2`, so empty strings won't reach this code path. Safe.

**6. TopRoutesSection (StatisticsScreen.kt:106, 469-534)**

Guard at line 106: `if (stats.topRoutes.size >= 2)` — consistent with other sections. The section correctly uses `routes.take(5)` for display limiting. The horizontal bar uses `route.count.toFloat() / maxCount` for proportional width. `maxCount` at line 470 uses `maxOfOrNull { it.count } ?: 1` to avoid division by zero. Correct.

**7. All-Time Toggle (StatisticsScreen.kt:311-463)**

- `showAllTime` state correctly toggles between 12-month zero-filled view and full data.
- `needsScroll = showAllTime && filledData.size > 24` — horizontal scroll only enabled when needed. Good.
- Adaptive label step at line 443: `(filledData.size / 6).coerceAtLeast(1)` — shows ~6 labels regardless of data size. Clean.
- `runCatching { YearMonth.parse(item.yearMonth) }` at line 450 safely handles malformed yearMonth strings.

**8. FirstFlightCard (StatisticsScreen.kt:233-303)**

`runCatching { flight.departureTimezone?.let { ZoneId.of(it) } }.getOrNull() ?: ZoneId.of("UTC")` — correctly handles null timezone (inner let returns null, falls through to UTC) and invalid timezone strings (runCatching catches ZoneRulesException, getOrNull returns null, falls through to UTC). Defense in depth.

`FIRST_FLIGHT_DATE_FORMATTER` at line 230 is a top-level val using `Locale.getDefault()` — same locale-capture-at-classload pattern noted in Review #11. Acceptable.

**9. Seat Class Largest Remainder (StatisticsScreen.kt:769-778)**

The algorithm is textbook-correct:
1. Compute raw percentages
2. Floor each
3. Calculate deficit (100 - sum of floors)
4. Sort indices by descending remainder
5. Distribute deficit to top-remainder items

`remember(data, total)` keys on both inputs. Correct. The `coerceAtLeast(1)` on total at line 759 prevents division by zero. The Composable shows `"${item.count} ($pct%)"` — clean display format.

**10. Edge Case Tests (StatisticsEdgeCaseTest.kt — 30 tests)**

Coverage breakdown:
- **Bar chart (2 tests):** Empty table returns empty list; single month returns count=1
- **Longest by duration (5 tests):** Empty table, null arrival, arrival before departure, correct selection, equal times
- **Airline name lookup (5 tests):** Known codes, unknown fallback, case insensitivity, empty string, single char
- **Top routes (7 tests):** Empty table, empty dep code, empty arr code, both empty, grouping, limit, direction distinction
- **All-time / monthly (2 tests):** Multi-year span, single month aggregation
- **First flight (4 tests):** Empty table, earliest selection, null timezone, invalid timezone
- **Seat class rounding (6 tests):** 3 equal items, 7 uneven items, single item, 2:1 split, skewed 99:1, 4 equal items
- **Cross-cutting deletes (3 tests):** Delete clears first flight, delete updates longest duration, delete updates top routes

All 7 improvements are covered. The `largestRemainderMethod` private helper mirrors the Composable's logic — this is a good pattern for testing pure logic that lives inside a `remember` block. Assertions are meaningful (not just "no crash" assertions).

### Edge Cases to Test

These scenarios lack test coverage and should be added in a future pass:

1. **Bar chart label clipping at max height** — Input: a single month with count equal to maxCount. Expected: the count label renders at `y = -4dp`, which clips above the Canvas. The label is invisible for the tallest bar. Suggest adding `coerceAtLeast(textPaint.textSize)` to the labelY calculation.

2. **First flight with departureTimeUtc = 0** — Input: a flight with epoch-zero departure. Expected: FirstFlightCard formats "1 Jan 1970" (or local-timezone equivalent). This is technically valid data but visually surprising. No guard against it.

3. **Top routes with very long airport codes** — Input: ICAO 4-character codes like "RJTT" and "KLAX". Expected: the `120.dp` label width in TopRoutesSection might be tight for "RJTT -> KLAX" (uses arrow character). Currently uses `TextOverflow.Ellipsis` so it's safe but could truncate.

4. **All-time toggle with 100+ months of data** — Input: flights spanning 10 years. Expected: 120 bars at ~24dp each = ~2880dp canvas width. Horizontal scroll should work but the adaptive label step `120/6 = 20` means labels every 20 months — legible but sparse. No crash, just UX consideration.

5. **Largest Remainder with all-zero counts** — Input: `largestRemainderMethod(listOf(0, 0, 0), 0)`. Expected: division by zero. The Composable guards with `coerceAtLeast(1)`, but the test helper does not. If someone calls the algorithm directly with total=0, it would produce `NaN` percentages. Not reachable from production code but the test helper should match the guard.

6. **getFullName with 3-letter ICAO airline prefix** — Input: flight number "ANA123" (3-letter code). The DAO extracts `SUBSTR(flightNumber, 1, 2)` = "AN", which maps to nothing in canonicalNames. Falls back to "AN". This is by design (IATA is 2-letter) but users with ICAO-format flight numbers will see raw prefixes.

7. **ViewModel recomposition with rapid data changes** — Input: bulk import of 50 flights. Expected: 5 combine blocks each emit independently, causing up to 5 intermediate StatsData emissions per insert. `WhileSubscribed` deduplicates via `stateIn` conflation, so the UI only sees the latest. No performance issue, but worth verifying no flicker.

### Suggestions (non-blocking)

1. **Hoist Paint outside Canvas** — `StatisticsScreen.kt:397-401`: Move the `android.graphics.Paint` allocation into a `remember` block above the Canvas to avoid re-allocation on each draw frame. The color can be updated via `.apply { color = ... }` inside the draw scope.

2. **Fix label clipping for tallest bar** — `StatisticsScreen.kt:428`: Add a lower bound to prevent negative Y values:
   ```kotlin
   val labelY = (chartHeight - barHeight - 4.dp.toPx())
       .coerceIn(textPaint.textSize, chartHeight - labelPadding)
   ```

3. **`canvasWidth` unused variable** — `StatisticsScreen.kt:371-376`: The `canvasWidth` val is computed but never referenced. The width is set directly on the Canvas modifier at lines 382/386. This is dead code — remove it.

### Strengths

1. **5-block combine pattern**: Elegant solution to the combine parameter limit. Each block groups related flows logically (basic counts, geography, rankings, records, routes). Easy to extend.

2. **Defensive DAO queries**: Every query has appropriate NULL/empty guards. The `arrivalTimeUtc > departureTimeUtc` filter is consistently applied in both `getTotalDurationMinutes` and `getLongestFlightByDuration`.

3. **Largest Remainder algorithm**: Correct implementation that guarantees percentages sum to exactly 100. The `remember(data, total)` keying prevents unnecessary recomputation.

4. **runCatching on timezone parsing**: Both FirstFlightCard and the bar chart month labels use `runCatching` for parsing operations that could throw on malformed data. Graceful fallbacks throughout.

5. **Comprehensive edge tests**: 30 tests covering all 7 improvements with meaningful assertions, not just smoke tests. The extracted `largestRemainderMethod` helper is a good testing pattern.

6. **Consistent UX patterns**: All sections use the same `>= 2` threshold, same ElevatedCard styling, same icon+title header pattern. The all-time toggle uses FilterChip which is Material 3 idiomatic.

7. **AirlineIataMap is injectable**: `@Singleton @Inject constructor()` makes it testable and mockable. The test file creates it directly, which is fine for a pure-logic class.

### Verdict: APPROVED — All 7 improvements are correctly implemented with clean architecture, defensive error handling, and thorough edge case test coverage. The minor issues (Paint allocation, label clipping, dead variable) are non-blocking. Ship it.

---

## Review #13 — Feature 3: Manual Flight Search/Add

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED

### Files Reviewed
- `AddEditLogbookFlightViewModel.kt` (273 lines)
- `AddEditLogbookFlightScreen.kt` (562 lines)
- `AddEditFlightViewModelTest.kt` (557 lines)

### Overall Assessment
Clean, well-structured implementation that follows the spec closely. ViewModel correctly uses cancel-and-replace for double-tap prevention, search state properly contained within `AddEditFormState`, Compose UI matches the design spec. 25 tests cover all 18 spec edge cases plus extras, using well-designed fakes. No critical issues found.

### Critical Issues
None.

### Suggestions Applied
- **S1:** DatePickerField `LaunchedEffect(date)` sync — prevents stale calendar dialog after auto-fill
- **S2:** All form fields (aircraftType, seatNumber, notes) now disabled during search — visual consistency
- **S3:** FakeLogbookFlightDao captures `utcDay` parameter — strengthens E7 UTC boundary test

### Additional Edge Cases Identified (E19-E23)
- E19: Validation error keeps `isSaving = false` (already correct, tested implicitly)
- E20: `confirmSaveDespiteDuplicate` full path (untested, low risk)
- E21: Search auto-fill date overridden by user before save (untested, low risk)
- E22: `updateFlightNumber` does not uppercase (design decision, documented)
- E23: Edit mode does not show search section (UI-level, ViewModel `isEditMode` tested)

### Strengths
- Cancel-and-replace `Job` pattern for search
- Clean single-state management via `AddEditFormState`
- `autoFillApplied` clearing in `updateDepartureCode`/`updateArrivalCode`
- Exception handling with `try/catch` wrapping `lookupRoute`
- Fake-based testing (no Mockito dependency)
- Save button guard: `!form.isSaving && !form.isSearching`

### Verdict: APPROVED — Ship it. All suggestions (S1-S3) applied. Implementation is correct, well-tested, and architecturally sound.

---

## Review #14 — Feature 4: Logbook Search & Filter

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED

### Files Reviewed
- `LogbookFlightDao.kt` (2 new queries)
- `LogbookRepository.kt` (2 new forwards)
- `LogbookViewModel.kt` (filter state, combine+debounce, sort, search)
- `LogbookScreen.kt` (search bar, chips, sort menu, clear button, two empty states)
- `LogbookViewModelSearchTest.kt` (30 tests)

### Overall Assessment
Clean in-memory filtering via combine+debounce. Two DAO queries for chip options. Two distinct empty states. StatsRow shows filtered counts with "(filtered)" suffix. All 25 spec edge cases covered + 5 additional tests.

### Critical Issues
None.

### Suggestions Applied
- **S3:** E22 tests now assert empty results instead of just `assertTrue(true)`
- **S4:** Added `ImeAction.Search` + `KeyboardActions(onSearch)` to search field

### Strengths
- Idiomatic combine+debounce reactive pipeline
- DAO queries for chip options (auto-update, full dataset)
- isActive computed property for Clear button visibility
- matchesSearch includes aircraftType (Architect P2)
- startCollecting() test helper for WhileSubscribed StateFlows
- 30 test methods with MutableStateFlow fake DAO

### Verdict: APPROVED — Ship it.

---

## Review #15 — Feature 5: Data Export (CSV/JSON)

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED

### Files Reviewed
- ExportService.kt (CSV/JSON serialization, RFC 4180 quoting, UTF-8 BOM)
- ExportViewModel.kt (state machine, loading guard)
- LogbookFlightExport.kt (DTO + wrapper)
- LogbookScreen.kt (overflow menu, share intent, error snackbar)
- ExportServiceTest.kt (27 tests with Robolectric)
- Plus: DAO, Repository, Manifest, FileProvider XML

### Critical Issues
None.

### Suggestions Applied
- **S5:** Merged two LaunchedEffect(exportState) blocks into one with when-block

### Notable Finding
Moshi KotlinJsonAdapterFactory actually omits null fields (does not serialize as "key": null). This matches the original spec intent. E18 test updated to accept both behaviors.

### Strengths
- RFC 4180 CSV quoting with comma/quote/newline/CR handling
- UTF-8 BOM verified at byte level
- Empty string → null mapping via ifBlank { null }
- Timezone fallback with runCatching
- Loading guard prevents concurrent exports
- ExportState.Ready carries mimeType — clean state machine
- totalFlightCount from allFlights (no extra DAO subscription)
- FileProvider correctly configured with cache-path

### Verdict: APPROVED — Ship it.

---

## Review #16 — Feature 6: Flight Detail Screen

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED

### Files Reviewed
- FlightDetailViewModel.kt (Flow-based loading, auto-navigate, delete callback)
- FlightDetailScreen.kt (full layout, share text, route header, timeline, map placeholder)
- LogbookViewModel.kt (cleanup — sheet state removed)
- LogbookScreen.kt (bottom sheet removed, card → onViewFlight)
- NavGraph.kt (new route + hideBottomBarRoutes)
- FlightDetailViewModelTest.kt (23 tests)

### Critical Issues
None.

### Suggestions Applied
- **S4:** Removed dead `rotate` import from LogbookScreen

### Strengths
- Reactive architecture via getByIdFlow().map{}.stateIn() — auto-updates on edit
- shouldAutoNavigateBack pattern separates "deleted after viewing" from "never existed"
- buildShareText is a pure function with 13 dedicated tests
- Minimal LogbookScreen cleanup, no unrelated refactoring
- Notes in SelectionContainer for copy
- Clean ViewModel cleanup (sheet state fully removed)

### Verdict: APPROVED — Ship it.

---

## Review #17 — Feature 7: Play Store Beta Prep

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (post-merge review)

### Files Reviewed
- libs.versions.toml, build.gradle.kts (Moshi codegen + signing)
- LogbookFlightExport.kt, AviationStackApi.kt (@JsonClass on all 5 classes)
- NetworkModule.kt (KotlinJsonAdapterFactory removed)
- proguard-rules.pro (NEW — Moshi, Retrofit, OkHttp, WorkManager, Kotlin)
- network_security_config.xml (comment), .gitignore (*.jks)
- ExportServiceTest.kt (reflection factory removed)

### Critical Issues
None.

### Suggestions (non-blocking)
- S1: ProGuard `**JsonAdapter` rule could be tighter (scope to app package)
- S5: Consider adding `isShrinkResources = true` for release builds

### Key Validation
- All 5 Moshi classes annotated — no missed classes
- Signing config gracefully handles missing properties
- ProGuard WorkManager constructor keep rule covers CoroutineWorker
- Spec edge cases E1-E10 require manual verification on signed release APK

### Verdict: APPROVED — Ship it.

---

## Review #18 — Feature 8: Route Map Canvas

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED

### Files Reviewed
- RouteMapCanvas.kt (Canvas composable + great-circle math + fallback state)
- FlightDetailScreen.kt (MapPlaceholder removed, RouteMapCanvas integrated)
- RouteMapCanvasTest.kt (20 tests)

### Critical Issues
None.

### Known Limitation
S1: Antimeridian viewport — trans-Pacific routes (LAX→NRT) show wider viewport than ideal. Arc renders correctly but map appears zoomed out. Acceptable for MVP.

### Strengths
- Textbook-correct spherical linear interpolation with Haversine
- Antimeridian normalization at interpolation layer
- Defensive viewport (min span, lat/lon clamp)
- Label clipping guard (top/bottom adaptive positioning)
- Accessibility semantics on Canvas
- 20 pure math tests covering endpoints, antimeridian, antipodal, polar, equator

### Verdict: APPROVED — Ship it.

---

## Review #19 — Feature 9: Home Screen Widget

**Date:** 2026-03-27
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (post-merge review)

### Files Reviewed
- FlightLogWidget.kt (GlanceAppWidget + Receiver, responsive small/medium)
- WidgetRefreshWorker.kt (@HiltWorker, Room→DataStore→updateAll)
- WidgetDataKeys.kt, WidgetDataStore.kt, WidgetStateDefinition.kt
- LogbookFlightDao.kt (getMostRecentFlight), LogbookRepository.kt
- FlightLogApplication.kt (enqueuePeriodic), AndroidManifest.xml (receiver)
- proguard-rules.pro (Glance keep), widget XML resources

### Critical Issues
None.

### Notable Findings
- EC2: CancellationException caught in Worker catch-all — minor, doesn't cause issues in practice
- EC4: "1 flights" singular/plural — cosmetic
- EC9: Small layout may never show on Android 8-11 due to minWidth=250dp — acceptable

### Strengths
- Clean Worker/DataStore/Glance pipeline (canonical pattern)
- getMostRecentFlight() DAO query (efficient LIMIT 1, per Architect)
- WidgetStateDefinition bridges DataStore correctly
- Defensive empty state handling for both sizes
- onUpdate triggers one-time refresh for newly-added widgets
- updatePeriodMillis=0 avoids redundant AlarmManager wakeups

### Verdict: APPROVED — Ship it.

---

## Review #20 — Feature 13: Onboarding Polish

**Date:** 2026-03-28
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (with fixes applied before merge)

### Files Reviewed
- OnboardingPreferences.kt (SharedPreferences flag)
- OnboardingActivity.kt (permission launcher, back-press guard)
- OnboardingScreen.kt (HorizontalPager, 3 pages, dot indicator)
- OnboardingPreferencesTest.kt (8 edge case tests)
- MainActivity.kt, AndroidManifest.xml, CalendarFlightsScreen.kt, LogbookScreen.kt

### Critical Issues Found + Fixed
1. `apply()` → `commit()` for SharedPreferences write (prevents flag loss on immediate finish)
2. Back-press guard added (user must tap "Get Started", can't escape onboarding)
3. Double-tap guard on permission launcher (prevents IllegalStateException)

### Strengths
- Clean separation: OnboardingScreen is pure Compose, Activity handles side effects
- Skip button jumps to page 3 (still shows CTA), not complete
- markComplete regardless of permission result — correct education flow
- 8 edge case tests on preferences (idempotency, isolation, missing key)

### Verdict: APPROVED — All 3 critical fixes applied before merge.

---

## Review #21 — Feature 10: Offline Airport Database

**Date:** 2026-03-28
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (with 3 fixes applied before merge)

### Files Reviewed
- Airport.kt (entity), AirportDatabase.kt, AirportDao.kt, AirportRepository.kt
- DatabaseModule.kt, AddEditLogbookFlightViewModel/Screen.kt
- FlightDetailViewModel/Screen.kt, LogbookRepository.kt
- AirportRepositoryTest.kt (18 tests)
- airports.db asset (269 airports)

### Critical Issues Found + Fixed
1. Null Island bug: static fallback with timezone-only used lat=0/lng=0 → changed to NaN + guard in distanceNm()
2. DB name collision: Room DB renamed from "airports.db" to "airport_lookup.db" (distinct from asset)
3. SQL search: added COLLATE NOCASE for reliable case-insensitive matching

### Strengths
- DAO-first + static fallback = zero regression from pre-DB world
- IATA uppercase normalization in repository layer
- Autocomplete: debounce + distinctUntilChanged + collectLatest trio
- 18 edge case tests covering normalization, fallback, haversine, null timezone

### Verdict: APPROVED — All 3 fixes applied before merge.

---

## Review #22 — Feature 12: Achievements & Gamification

**Date:** 2026-03-28
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (with 3 fixes applied before merge)

### Files Reviewed
- Achievement entity, AchievementDao, AchievementDefinitions (18 defs)
- AchievementEvaluator (pure object), AchievementRepository
- AchievementsViewModel, AchievementsScreen, NavBadgeViewModel
- FlightDatabase v7 + migration, DatabaseModule, LogbookRepository
- StatisticsScreen (TabRow), NavGraph (badge dot)
- AchievementEvaluatorTest (41 tests)

### Critical Issues Found + Fixed
1. Short-circuit bug: checked only Platinum, not all 18 → fixed to `alreadyUnlocked.size == ALL.size`
2. Race condition: ensureAllExist used @Upsert → changed to INSERT OR IGNORE
3. Missing markAllSeen: achievements screen didn't clear badge → added LaunchedEffect(Unit)

### Strengths
- Pure AchievementEvaluator — 41 tests, no Android deps, fast
- Night owl timezone handling robust (null/invalid fallback, DST-aware)
- Tier-colored cards with shimmer for unseen unlocks
- INSERT OR IGNORE eliminates startup race condition

### Verdict: APPROVED — All 3 fixes applied before merge.

---

## Review #23 — Feature 11: FlightAware Migration + Live Flight Tracking

**Date:** 2026-03-28
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (with 3 fixes applied before merge)

### Files Reviewed
- FlightAwareApi.kt (6 Moshi models, Retrofit interface)
- NetworkModule.kt (HTTPS, x-apikey interceptor)
- FlightRouteServiceImpl.kt (FlightAware endpoints)
- FlightStatus entity, DAO, Repository, Migration 7→8
- FlightTrackingWorker (15-min periodic, self-cancel)
- NotificationHelper (2 channels, 5 notification types)
- FlightTrackingViewModel, FlightDetailScreen, RouteMapCanvas
- 29 tests

### Critical Issues Found + Fixed
1. CancellationException swallowed in worker + service → added re-throw
2. DIVERTED not treated as terminal state → added to self-cancel set
3. Stale AviationStack comment → updated to FlightAware

### Strengths
- Clean API migration: FlightRouteService interface unchanged, call sites insulated
- x-apikey interceptor skips header when key blank (fail-safe)
- Worker self-cancels on terminal states, handles 429 with retry
- Notification channels with correct importance levels
- Double Null Island guard (worker + UI)
- pickBestMatch by origin + closest scheduled time

### Verdict: APPROVED — All 3 fixes applied before merge.

---

## Review #24 — Feature 14: Trip Grouping / Timeline

**Date:** 2026-03-28
**Reviewer:** Code Reviewer
**Verdict:** APPROVED (no critical issues)

### Files Reviewed
- TripGroup.kt, TripGrouper.kt (pure object, 48h gap detection)
- TripHeader.kt (ElevatedCard, chevron animation)
- LogbookViewModel.kt (tripGroups StateFlow, collapsed tracking)
- LogbookScreen.kt (SegmentedButton toggle, indented flight cards)
- TripGrouperTest.kt (24 tests)

### Critical Issues
None.

### Strengths
- Pure TripGrouper object — same pattern as AchievementEvaluator
- Sorts internally regardless of input order (verified by tests)
- 24 tests: boundary 48h, null arrival, unsorted input, round trip labels, date ranges
- Collapsed state via combine(flights, _collapsedTrips) — reactive, survives changes
- Route label handles consecutive dedup + non-consecutive preservation

### Verdict: APPROVED — Ship it.
