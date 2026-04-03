# Golden Principles

Mechanical rules enforced by all agents. Violations block shipping.

1. **Every public function needs a unit test** — no exceptions for "simple" functions
2. **Dependency flow: Entity → DAO → Repository → ViewModel → UI** — never reverse. Entity must not import ViewModel, Repository, or UI packages
3. **No business logic in Composables** — hoist all logic to ViewModel. Composables only render state
4. **All DB queries must use indexed columns in WHERE/ORDER BY** — indexed: departureTimeMillis, flightNumber, departureCode, arrivalCode, departureDateEpochDay
5. **All network calls wrapped in try/catch with CancellationException rethrow** — never swallow CancellationException
6. **No hardcoded strings in UI** — use string resources (`R.string.*`)
7. **StateFlow for UI state, SharedFlow for one-shot events** — no LiveData anywhere
8. **Nullable types from external sources (API, DB), non-null in domain layer** — validate at boundary, trust internally

## Enforcement
- `/arch-check` validates principles 2, 3, 5, 6, 7 automatically
- Code reviewer checks all 8 in every review
- Developer's pre-completion checklist verifies compliance before reporting done
