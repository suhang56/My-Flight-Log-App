# Architecture Review: Feature 3 — Manual Flight Search/Add

**Reviewer:** Architect
**Date:** 2026-03-27
**Spec reviewed:** `.claude/specs/feature-3-manual-flight-search.md`
**Verdict:** Approved with recommendations

---

## 1. Architecture Health

The spec builds on existing patterns in the codebase. The `AddEditLogbookFlightViewModel` and `AddEditFormState` are clean and well-scoped. Extending them with search state is the right approach — there is no justification for creating a separate ViewModel or screen for this feature.

The data layer (`FlightRouteService` -> `AviationStackApi`) is already well-abstracted behind an interface, which keeps the ViewModel testable regardless of which approach we take for injection.

Overall health: **Good.** The codebase follows MVVM + Clean Architecture consistently. This feature slots in without structural changes.

---

## 2. Key Architecture Questions

### Q1: FlightRouteService directly in ViewModel vs. through Repository?

**Recommendation: Inject directly into the ViewModel. Do NOT route through LogbookRepository.**

Rationale:

- `LogbookRepository` is a persistence-layer abstraction over `LogbookFlightDao`. Its responsibility is CRUD operations on `LogbookFlight` entities. Adding a network lookup method to it would violate single responsibility — it would become both a local data manager and a remote data fetcher.

- The existing precedent is `CalendarRepository`, which injects `FlightRouteService` because calendar sync genuinely orchestrates network + persistence (it looks up routes AND persists results in `CalendarFlight`). That is a valid use case for a repository to hold a network service.

- In Feature 3, the ViewModel calls `lookupRoute()` for UI auto-fill only. The result is never persisted as a `FlightRoute` — it just populates form fields. The save path already goes through `LogbookRepository.insert()`. There is no persistence coordination that would justify a repository intermediary.

- `FlightRouteService` is already an interface. The ViewModel depends on the abstraction, not the implementation. This is sufficient for testability — tests can provide a fake `FlightRouteService` without touching the repository at all.

- If we later need caching of route lookups (e.g., to avoid redundant API calls), that belongs in a dedicated `FlightRouteRepository` or a caching layer inside `FlightRouteServiceImpl`, not bolted onto `LogbookRepository`.

**Verdict: The spec's approach (direct injection) is correct.**

### Q2: Search state in AddEditFormState vs. separate StateFlow?

**Recommendation: Keep search state inside AddEditFormState as the spec proposes.**

Rationale:

- The search fields (`flightSearchQuery`, `flightSearchDate`, `isSearching`, `searchError`, `autoFillApplied`) are tightly coupled to the form lifecycle. A successful search mutates form fields (`departureCode`, `arrivalCode`, `flightNumber`). Splitting them into a separate StateFlow would require coordinating two state objects for what is fundamentally one user flow — this adds complexity without benefit.

- The UI observes a single `form` StateFlow. Adding a second flow means the composable must `collectAsState()` twice and reason about partial updates across two objects. Given that the search section is part of the same screen, a single state object is simpler and less error-prone.

- The `AddEditFormState` data class is currently 14 fields. Adding 5 more brings it to 19. This is within acceptable limits for a form-backing state class. If the class grows beyond ~25 fields in the future, consider splitting — but not yet.

**Verdict: Single state object is the right call for this feature.**

### Q3: Double-tap prevention — cancel-and-replace vs. guard flag?

**Recommendation: Cancel-and-replace (store the `Job` and cancel on re-entry).**

Rationale:

- A guard flag (`if (isSearching) return`) is simpler but has a subtle flaw: if the coroutine completes but the state update races with a second tap, the guard can either block a legitimate retry or allow a stale result to overwrite a fresh request. It is safe enough for this use case, but cancel-and-replace is strictly better.

- Cancel-and-replace pattern:
  ```kotlin
  private var searchJob: Job? = null

  fun searchFlight() {
      val query = _form.value.flightSearchQuery.ifBlank { return }
      searchJob?.cancel()
      _form.update { it.copy(isSearching = true, searchError = null) }
      searchJob = viewModelScope.launch {
          // ... lookup logic
      }
  }
  ```
  This ensures:
  - Only one search is ever in flight
  - If the user taps Search again (e.g., after changing the date), the previous request is cancelled immediately and the new one starts
  - No stale result can overwrite a newer request
  - The `Job` is automatically cancelled when the ViewModel is cleared (via `viewModelScope`)

- The guard flag approach would need extra logic to handle the "user changes query and re-searches" case — the cancel-and-replace handles it naturally.

**Verdict: Use cancel-and-replace. Store the `Job` reference as a private `var` in the ViewModel.**

### Q4: Dependency / Hilt scoping issues?

**No issues found.**

- `FlightRouteService` is bound as `@Singleton` in `NetworkBindingsModule`. This is correct — the service is stateless (just wraps an API call), so a single instance is fine.
- `AddEditLogbookFlightViewModel` is `@HiltViewModel`, which means Hilt creates it in the `ViewModelComponent` scope. It can inject any `@Singleton`-scoped dependency.
- `LogbookRepository` is `@Singleton` — no conflict.
- `SavedStateHandle` is automatically provided by Hilt for `@HiltViewModel` classes.
- No circular dependency risk: ViewModel depends on Repository and FlightRouteService; neither depends on the ViewModel.

**Verdict: No scoping issues. The proposed constructor is clean.**

---

## 3. Maintainability Assessment

### Good

- The spec correctly identifies that no schema migration is needed. `LogbookFlight` already supports `sourceCalendarEventId = null` for manual flights.
- The `updateDepartureCode` and `updateArrivalCode` methods already reset `duplicateCheckPassed`. The spec correctly notes they should also clear `autoFillApplied`.
- Edge cases E1-E18 are thorough and well-specified.

### Concerns

1. **No existing ViewModel tests.** There are no tests in `app/src/test` for `AddEditLogbookFlightViewModel`. The spec calls for creating `AddEditFlightViewModelTest` — this is mandatory. The 18 edge cases listed in the spec must all have corresponding unit tests. This is a **blocker** for approval.

2. **`updateFlightSearchQuery` applies `.uppercase().trim()` eagerly.** This means the text field will snap the user's cursor to the end on every keystroke (since the value changes). The `trim()` is the problem — it should only be applied on search submission, not on each keystroke. `.uppercase()` is fine (the keyboard hint handles this, but uppercasing in the ViewModel is a good safety net). **Fix: remove `.trim()` from `updateFlightSearchQuery`, apply it in `searchFlight()` instead.**

3. **`searchError` string is hardcoded in the ViewModel.** This works but is not localizable. For a personal project this is acceptable. Flag for future if localization is ever needed.

---

## 4. Dependency Review

No new dependencies are introduced by this feature. All required libraries are already in the project:

- Retrofit + Moshi for API calls
- Hilt for DI
- Compose for UI
- Coroutines for async

No outdated or risky dependencies flagged for this feature.

---

## 5. Edge Testing Verification

**Status: BLOCKER — No ViewModel tests exist yet.**

The spec defines 18 edge cases (E1-E18). No test file for `AddEditLogbookFlightViewModel` currently exists in the test source set. The Code Reviewer and Developer must ensure:

- A new test file `AddEditFlightViewModelTest.kt` is created
- All 18 edge cases have at least one corresponding test method
- Tests use a fake `FlightRouteService` (not mocking the network layer)
- Tests use a fake or in-memory `LogbookRepository` (or mock it — the DAO is the persistence boundary)
- Tests verify state transitions, not just final state (e.g., `isSearching` goes `true` then `false`)

Until these tests exist and pass, the feature cannot be considered complete.

---

## 6. Recommendations (prioritized by impact)

| Priority | Recommendation | Impact |
|----------|----------------|--------|
| **P0** | Create `AddEditFlightViewModelTest.kt` with all 18 edge case tests | Testability blocker |
| **P1** | Use cancel-and-replace (`Job`) for double-tap prevention instead of guard flag | Correctness under race conditions |
| **P1** | Remove `.trim()` from `updateFlightSearchQuery`, apply only in `searchFlight()` | UX bug prevention (cursor jump) |
| **P2** | Clear `autoFillApplied` in `updateDepartureCode` and `updateArrivalCode` | Spec already calls for this; just confirming |
| **P3** | Consider extracting `FlightSearchUseCase` if search logic grows (e.g., caching, validation) | Future maintainability — not needed now |

---

## 7. Summary

The spec is well-written and architecturally sound. The decision to inject `FlightRouteService` directly into the ViewModel is correct for this use case. Search state belongs in `AddEditFormState`. Use cancel-and-replace for double-tap prevention. The only blocker is the absence of ViewModel unit tests — these must be written alongside the feature code.

No structural changes to the project are needed. The feature fits cleanly into the existing architecture.
