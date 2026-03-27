# Architecture Review: Feature 4 — Logbook Search & Filter

**Reviewer:** Architect
**Verdict:** Approved with recommendations

Key decisions:
- Q1: In-memory filtering via combine() is correct (not dynamic SQL)
- Q2: Single debounce(300ms) on filterState is acceptable (no need to split)
- Q3: Filtered counts in StatsRow correct; keep DAO-level queries for other consumers
- Q4: LogbookFilterState design is clean, isActive computed property correct

Recommendations:
- P0: Create LogbookViewModelSearchTest.kt with all 25 edge case tests
- P1: Keep existing repository.getCount() and repository.getTotalDistanceNm() DAO methods
- P2: Consider also searching flight.aircraftType in matchesSearch (optional)
