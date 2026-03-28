# Feature 15 — Cloud Backup via Google Drive

**Scope:** Large | **Sprint:** 3 | **Dependencies:** F6 (ExportService JSON — done)
**Drive scope:** `drive.appdata` only — hidden `appDataFolder`, invisible to user's Drive UI.
**No backend required.** Drive acts as user-owned remote storage.

---

## What Exists (reuse directly)

- `ExportService.exportToJson()` → `File` — serializes `List<LogbookFlight>` to `LogbookFlightExportWrapper` JSON
- `LogbookFlightExport` / `LogbookFlightExportWrapper` — Moshi `@JsonClass` models with all fields needed for lossless restore
- `LogbookRepository.getAllOnce()` — synchronous snapshot for backup
- `LogbookRepository.insert()` — for restore import; existing duplicate guard (`existsByRouteAndDate`) prevents double-import

---

## New Dependencies (`app/build.gradle.kts`)

```kotlin
implementation("com.google.android.gms:play-services-auth:21.2.0")
implementation("com.google.api-client:google-api-client-android:2.2.0")
implementation("com.google.apis:google-api-services-drive:v3-rev20240521-2.0.0")
```

Drive REST client handles OAuth token refresh automatically via `GoogleAccountCredential`.

---

## DriveBackupService (`@Singleton`)

Single responsibility: upload/download one file named `flight-log-backup.json` to `appDataFolder`.

```kotlin
@Singleton
class DriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportService: ExportService,
    private val repository: LogbookRepository,
    private val moshi: Moshi
) {
    suspend fun backup(account: GoogleSignInAccount): BackupResult
    suspend fun restore(account: GoogleSignInAccount): RestoreResult
    suspend fun getMetadata(account: GoogleSignInAccount): BackupMetadata?
}
```

### backup() logic
1. `repository.getAllOnce()` → `exportService.exportToJson(flights)` → `File`
2. Check if `flight-log-backup.json` already exists in `appDataFolder` (Drive Files.list)
3. If exists: update (Drive Files.update). If not: create (Drive Files.create)
4. Save `BackupMetadata` to SharedPreferences: `lastBackupAt`, `flightCount`, `fileSizeBytes`
5. Return `BackupResult.Success` or `BackupResult.Failure(reason)`

### restore() logic
1. Find `flight-log-backup.json` in `appDataFolder` (Drive Files.list)
2. Download file content as String
3. Parse with Moshi `LogbookFlightExportWrapper` adapter
4. For each `LogbookFlightExport`: convert to `LogbookFlight`, call `repository.insert()`
   - `insert()` returns `-1L` on duplicate (existing unique index) — skip silently
5. Return `RestoreResult(imported = N, skipped = M)`

### Conversion: `LogbookFlightExport` → `LogbookFlight`
All fields map directly. Set `id = 0` (let Room auto-assign new ID on restore). Preserve `sourceCalendarEventId` + `sourceLegIndex` so calendar re-sync duplicate guard still works.

---

## BackupMetadata (SharedPreferences)

```kotlin
data class BackupMetadata(
    val lastBackupAt: Long,     // epoch millis
    val flightCount: Int,
    val fileSizeBytes: Long
)
```

Keys: `backup_last_at`, `backup_flight_count`, `backup_file_size`. Read synchronously — no coroutine needed in UI.

---

## Auto-Backup (WorkManager)

`BackupWorker` — `OneTimeWorkRequest`, enqueued from `LogbookRepository` after every mutation.

```kotlin
// In LogbookRepository.insert() / update() / delete():
BackupWorker.enqueueIfSignedIn(context)   // no-op if user not signed in
```

Deduplication: `ExistingWorkPolicy.REPLACE` with a fixed work name `"auto_backup"` — multiple rapid mutations collapse into one worker run. Worker waits for `NetworkType.CONNECTED`. This provides the debounce + max-once-per-trigger behavior without a manual timer.

---

## Settings Screen (new: `SettingsScreen.kt`)

New bottom nav item (4th tab) or accessible via overflow menu — lean toward overflow menu to keep bottom nav at 3 items.

Layout:
```
[Google Account]
  Sign in with Google          [Sign In button]
  Signed in as: user@gmail.com [Sign Out]

[Backup]
  Last backup: Mar 27, 2026 at 14:35  (341 flights, 48 KB)
  [Back Up Now]   [Restore from Drive]

[About]
  Version 1.0.0
```

- "Back Up Now" → calls `DriveBackupService.backup()`, shows progress indicator, snackbar on result
- "Restore from Drive" → shows confirmation dialog ("This will import flights from your backup. Duplicates will be skipped."), then calls `DriveBackupService.restore()`, shows `"Imported N flights (M skipped)"`
- Sign-in uses `GoogleSignIn.getClient(context, gso).signInIntent` launched via `rememberLauncherForActivityResult`
- `GoogleSignInOptions` requests `Drive.SCOPE_APPDATA` scope only

---

## Files to Create / Edit

| Action | File |
|---|---|
| Create | `data/backup/DriveBackupService.kt` |
| Create | `data/backup/BackupWorker.kt` |
| Create | `data/backup/BackupMetadataStore.kt` — SharedPreferences wrapper |
| Create | `ui/settings/SettingsScreen.kt` |
| Create | `ui/settings/SettingsViewModel.kt` |
| Edit | `data/repository/LogbookRepository.kt` — call `BackupWorker.enqueueIfSignedIn()` after mutations |
| Edit | `di/DatabaseModule.kt` or new `BackupModule.kt` — provide `DriveBackupService` |
| Edit | `ui/navigation/NavGraph.kt` — add Settings route |
| Edit | `AndroidManifest.xml` — internet permission (already present?), `GET_ACCOUNTS` permission |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| User not signed in to Google | "Back Up Now" button disabled; `BackupWorker.enqueueIfSignedIn()` is a no-op |
| Drive `appDataFolder` is empty (first backup) | Files.create — no Files.update attempt; no crash |
| Backup file already exists | Files.update (patch) — no duplicate file created |
| Network unavailable during manual backup | `BackupWorker` queued with `CONNECTED` constraint, runs when network returns; "Backup queued" snackbar shown immediately |
| Restore on fresh install with 0 flights | All N flights imported, `skipped = 0` |
| Restore on existing install with overlapping flights | Duplicate flights skipped via `insert()` returning -1L; `imported + skipped = total` |
| Restore with `sourceCalendarEventId` set | Field preserved on import; calendar re-sync correctly detects existing logbook entry |
| Backup file contains unknown JSON fields (future version) | Moshi ignores unknown fields — no crash; known fields parsed correctly |
| `exportToJson()` throws (disk full) | `BackupResult.Failure` returned; error shown in UI; no partial upload |
| Google account token expired | `GoogleAccountCredential` auto-refreshes; if refresh fails, surface "Sign in again" prompt |
| User signs out mid-backup | `BackupWorker` completes or fails gracefully; no crash |
| 0 flights in logbook | Backup uploads valid JSON with `flight_count: 0`; restore imports 0 flights |
| Restore JSON malformed / corrupted | Moshi parse exception caught; `RestoreResult.Failure` returned; no partial import to DB |
| Auto-backup triggered 5 times in 10 seconds | `ExistingWorkPolicy.REPLACE` collapses to 1 worker run |
