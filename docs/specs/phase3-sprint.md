# Phase 3 Sprint Spec

## 1. Backup "Unknown Error" — Root Cause + Fix

**Root cause:** `DriveBackupService.backup()` line 87 catches all `Exception` and returns
`BackupResult.Failure(e.message ?: "Unknown error")`. `GoogleAccountCredential` throws a
`UserRecoverableAuthIOException` (or plain `IOException`) when the OAuth token is stale or
the Drive app scope was never granted. These exceptions have a null or generic message, so
the catch-all returns `"Unknown error"`.

**Secondary cause:** `buildDriveService()` calls `GoogleSignIn.getLastSignedInAccount()` which
can return a stale/null account even when Firebase reports a signed-in Google user — the
`DriveScopes.DRIVE_APPDATA` scope may not have been requested during sign-in.

**Fix approach:**
- In `buildDriveService()`: verify scope is granted via `GoogleSignIn.hasPermissions()` before
  building the Drive client. If not, return `null` with a specific message.
- Catch `UserRecoverableAuthIOException` separately; surface a "Re-authorise Google Drive" action.
- Catch `IOException` separately with message "Network error — check connection".
- Ensure `GoogleSignInOptions` in `AuthModule`/sign-in flow includes `requestScopes(Scope(DriveScopes.DRIVE_APPDATA))`.

**Files:**
- `data/backup/DriveBackupService.kt` — exception handling + scope check
- `di/AuthModule.kt` or `data/auth/AuthRepositoryImpl.kt` — GoogleSignInOptions scope
- `ui/settings/SettingsViewModel.kt` — surface user-recoverable action via SharedFlow event

---

## 2. Auto-Sync on Sign-In

**Design:** After a successful Google sign-in, fire a one-time WorkManager job.
`AutoBackupWorker` already exists with `enqueueIfSignedIn()` — it just needs to be called
from the sign-in success path.

**Trigger:**
- In `AuthRepositoryImpl.signInWithGoogle()`: on `Result.success`, call
  `AutoBackupWorker.enqueueIfSignedIn(context)`.
- Keep `ExistingWorkPolicy.KEEP` so tapping sign-in twice does not double-enqueue.

**Frequency:** one-shot on sign-in only (not periodic — periodic was already removed).

**Error handling:** Worker returns `Result.retry()` on failure (already implemented).
Failures are silent — no UI notification. If a backup fails at sign-in time the user
can trigger manually from Profile.

**Files:**
- `data/auth/AuthRepositoryImpl.kt` — call `AutoBackupWorker.enqueueIfSignedIn()` after sign-in
- `data/backup/AutoBackupWorker.kt` — no changes needed
- `ui/auth/` (sign-in screen/VM) — no UI changes needed

---

## 3. Flight Detail — Aircraft Info

**Data sources:**
- Aircraft type: already in `LogbookFlight.aircraftType` and `FlightAwareFlight.aircraft_type`
- Registration + age: `https://api.planespotters.net/pub/photos/reg/{registration}` returns
  photos + metadata. No auth required. Rate limit: 100 req/day free tier.
- AeroAPI `/flights/{ident}` already returns `aircraft_type` (ICAO type code, e.g. `B738`).
  Does NOT return registration number. Registration requires AeroAPI `/flights/{fa_flight_id}`
  extended fields (`registration`) — needs AeroAPI paid tier, or a separate lookup.
- **Fallback:** Use `aircraftType` from DB only if no network available. Show photo only if
  registration is available; hide photo section otherwise.

**Alternative for registration:** `https://www.aviationstack.com/` free tier returns
`aircraft.registration` in flight response — check if already integrated; if not, add
as optional secondary lookup.

**UI layout (below existing FlightInfoSection):**
```
[AircraftCard]
  ┌──────────────────────────────────────┐
  │ [Photo 16:9 AsyncImage + shimmer]    │
  │ Boeing 737-800    ·  Registration    │
  │ First flight: 2015 · Age: ~10 yrs   │
  └──────────────────────────────────────┘
```
Show card only when `aircraftType` is non-null. Show photo only when registration resolves.

**New files:**
- `data/network/PlanespottersApi.kt` — Retrofit interface + response models
- `data/network/AircraftPhotoRepository.kt` — wraps Planespotters, caches URL in-memory
- `ui/logbook/AircraftCard.kt` — Composable, accepts `AircraftCardState`
- `ui/logbook/FlightDetailViewModel.kt` — add `aircraftCardState: StateFlow<AircraftCardState>`

**Modified files:**
- `ui/logbook/FlightDetailScreen.kt` — insert `AircraftCard` after `FlightInfoSection`
- `di/NetworkModule.kt` — add Planespotters Retrofit instance (no auth, base URL only)

---

## 4. Review / Rating Function

**Schema change — new column on `LogbookFlight`:**
```kotlin
val rating: Int? = null   // 1–5 stars, nullable = not yet rated
```
Room migration: `ALTER TABLE logbook_flights ADD COLUMN rating INTEGER`
Migration version: DB version 4 → 5.

**UI design:**
- In `FlightDetailScreen`: add `RatingSection` below `NotesSection`.
  Five outlined stars → tap fills. Shows "Not rated" if null.
- In logbook list item: show small star + number if rated (e.g. `★ 4`).
- In Stats screen (if exists): average rating per airline/route.

**No separate "review" text** — `notes` field already exists. Rating is the only new
persisted field. Notes remain the free-text review mechanism.

**New files:**
- `ui/logbook/RatingSection.kt` — star row Composable, emits `Int?` via callback
- `ui/logbook/RatingViewModel.kt` — or extend `FlightDetailViewModel` with `setRating(id, stars)`

**Modified files:**
- `data/local/entity/LogbookFlight.kt` — add `rating: Int?`
- `data/local/AppDatabase.kt` — version bump + Migration(4, 5)
- `data/local/dao/LogbookFlightDao.kt` — add `updateRating(id, rating)` query
- `data/repository/LogbookRepository.kt` — add `setRating(id, rating)`
- `ui/logbook/FlightDetailViewModel.kt` — expose rating state, handle `setRating`
- `ui/logbook/FlightDetailScreen.kt` — add `RatingSection`
- `ui/logbook/LogbookFlightItem.kt` (or equivalent list item) — show star badge

---

## 5. Task Breakdown

| Task | Owner | Files |
|------|-------|-------|
| T1: Backup bug fix | developer | `DriveBackupService.kt`, `AuthRepositoryImpl.kt`, `SettingsViewModel.kt` |
| T2: Auto-sync on sign-in | developer | `AuthRepositoryImpl.kt` |
| T3: Aircraft info UI | developer | `PlanespottersApi.kt`, `AircraftPhotoRepository.kt`, `AircraftCard.kt`, `FlightDetailViewModel.kt`, `FlightDetailScreen.kt`, `NetworkModule.kt` |
| T4: Rating feature | developer | `LogbookFlight.kt`, `AppDatabase.kt`, `LogbookFlightDao.kt`, `LogbookRepository.kt`, `FlightDetailViewModel.kt`, `FlightDetailScreen.kt`, `RatingSection.kt` |

---

## 6. Edge Cases

**Backup bug:**
- User signs in with Google but dismisses the Drive scope consent → `UserRecoverableAuthIOException`; must surface re-auth prompt, not silent "unknown error"
- User revokes Drive access in Google account settings mid-session → same exception path
- `e.message` is null (some Google auth exceptions have null message) → always provide fallback strings via `R.string`

**Auto-sync:**
- Sign-in succeeds but no flights exist → backup runs, creates empty file (OK — harmless)
- Device offline at sign-in → WorkManager queues job with `NetworkType.CONNECTED` constraint; fires when online
- User signs out and back in rapidly → `ExistingWorkPolicy.KEEP` prevents double-enqueue

**Aircraft info:**
- `aircraftType` is null (manually-entered flight with no type) → hide `AircraftCard` entirely
- Planespotters returns 0 photos for a registration → show aircraft type text only, no photo
- Planespotters rate limit hit (100/day) → catch HTTP 429, return null gracefully; no crash
- Registration lookup fails for non-IATA flights (private/charter) → degrade to type-only card
- Image URL broken/404 after download → `AsyncImage` error placeholder

**Review/Rating:**
- User rates 0 stars (deselects) → store null, not 0 (0 would look like unrated in queries)
- Migration on device with existing flights → all existing rows get `rating = NULL` (Room default)
- Concurrent rating update from 2 sessions (sign-in on 2 devices) → last-write wins via `updatedAt`
- Rating on a flight that has been deleted between tap and save → DAO update is a no-op; UI shows NotFound state
