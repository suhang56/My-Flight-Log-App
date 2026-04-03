# Architecture & Conventions

## Tech Stack
- **Pattern:** MVVM + Clean Architecture (UI → ViewModel → Repository → Room/API)
- **DI:** Hilt
- **Async:** Coroutines + StateFlow/SharedFlow
- **Navigation:** Navigation Compose (single-Activity)
- **DB:** Room v4 (CalendarFlight, LogbookFlight, Achievement, Airport, FlightStatus)
- **Network:** Retrofit + OkHttp + Moshi (KotlinJsonAdapterFactory for reflection)
- **Build:** compileSdk 35, minSdk 26, targetSdk 35

## Performance Guidelines
- Room: Use indices on columns in WHERE/ORDER BY (departureTimeMillis, flightNumber, departureCode, arrivalCode)
- Statistics: Pre-compute in DAO queries, not ViewModel
- Compose: Use `key()` in LazyColumn with flight ID. Don't pass ViewModel-capturing lambdas to Composables
- WorkManager: Periodic sync is 6h — don't go below 15min (Android minimum)
- Google Drive: All operations off-main-thread via coroutines

## Key Conventions
- Always push to GitHub immediately after every commit
- Always build APK after feature work
- Use git worktrees for feature branches (`D:/My-Flight-Log-Worktree/`)
- Edge testing is non-negotiable — every feature needs edge case tests
- No Co-Authored-By in commit messages
- Never commit `.claude/` directory to git
- Token-optimized: Developer writes files directly, Designer/Architect send brief bullets (max 30 lines)
- Save code reviews to `.claude/code-reviews.md`

## Agent Legibility
Code should be optimized for agent readability:
- Explicit imports (no star imports) — agents need to trace dependencies
- One class per file — agents scan file-at-a-time
- Descriptive function names — agents match by name, not by reading body
- Package-by-feature — agents navigate by feature, not by layer
