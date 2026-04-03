# Project State

## Current Phase
Phase 2 complete (2026-04-02). All features shipped.
See `docs/roadmap-v2.md` for Phase 3+ plans.

## Blockers
5 P0 bugs must be fixed before Phase 3 — see memory: `project_bugs_p0.md`

## Known Tech Debt
- Logbook search removed in refactor (commit 54fec59) — needs reimplementation
- 7 classes with zero test coverage
- Duplicate AirportNameMap files
- No E2E/instrumented tests

## Harness Self-Improvement
When an agent makes a mistake that gets corrected:
1. Save feedback memory with the correction
2. If the mistake could be caught automatically, create/update a hook
3. If it's a pattern violation, add to Golden Principles
4. If it's an architectural issue, add to `/arch-check`
5. Log the struggle in `.claude/struggle-log.md`

The harness gets stronger with every correction.
