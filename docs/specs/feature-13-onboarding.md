# Feature 13 — Onboarding Polish

**Scope:** Small | **Target:** Before Play Store upload (v1.0)

---

## What Exists (do not duplicate)

- `PermissionFullScreen` composable handles 4 states: NotRequested, Denied, PermanentlyDenied, Granted
- `PermanentlyDenied` already deep-links to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
- `EmptyState` composable shows icon + hint copy + Sync Now button
- Permission rationale copy exists but is generic ("reads your calendar to automatically detect...")

---

## What to Build

### 1. OnboardingActivity / OnboardingScreen (new)

A `HorizontalPager` (3 pages) shown only on first launch, before `MainActivity`.

| Page | Icon | Headline | Body |
|---|---|---|---|
| 1 | Airplane | "Your flights, automatically" | "Flight Log reads your calendar to detect flights — no manual entry needed." |
| 2 | CalendarMonth | "Why we need calendar access" | "We read event titles (e.g. 'NH847 HND-LHR') to find your flights. We never modify your calendar or share your data." |
| 3 | BarChart | "Track every journey" | "Log flights, view statistics, and export your logbook — all free, forever." |

- "Next" button advances pages. Page 3 shows "Get Started" button.
- "Get Started" triggers calendar permission request inline (same `rememberLauncherForActivityResult` pattern as `CalendarFlightsScreen`), then marks onboarding complete and launches `MainActivity`.
- Skip link (top-right TextButton) on pages 1–2: skips to page 3.
- No back stack: pressing system back on page 1 exits the app (standard onboarding behavior).

### 2. OnboardingPreferences (new, ~20 lines)

```kotlin
object OnboardingPreferences {
    private const val PREFS_NAME = "onboarding"
    private const val KEY_COMPLETE = "complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)

    fun markComplete(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_COMPLETE, true).apply()
}
```

Use SharedPreferences (not DataStore) — simpler, no coroutine needed at app start.

### 3. MainActivity launch gate

In `MainActivity.onCreate()`, before `setContent`:

```kotlin
if (!OnboardingPreferences.isComplete(this)) {
    startActivity(Intent(this, OnboardingActivity::class.java))
    finish()
    return
}
```

### 4. Empty State Polish (edit existing `EmptyState` in `CalendarFlightsScreen.kt`)

- UPCOMING tab, no flights: keep existing copy, add subtitle "Make sure your calendar events include flight numbers like NH847 or AA123."
- PAST tab, no flights: change to "No past flights logged" + subtitle "Flights from your calendar will appear here after they depart."
- Logbook empty state (find in `LogbookScreen.kt`): add "Add your first flight" FilledButton that navigates to AddEditFlightScreen.

### 5. PermissionPrompt copy polish (edit `CalendarFlightsScreen.kt` lines 112–113)

Change `NotRequested` message to:
> "Flight Log reads event titles like 'NH847 HND-LHR' to automatically find your flights. Your calendar data never leaves your device."

---

## Files to Create / Edit

| Action | File |
|---|---|
| Create | `ui/onboarding/OnboardingActivity.kt` |
| Create | `ui/onboarding/OnboardingScreen.kt` |
| Create | `data/preferences/OnboardingPreferences.kt` |
| Edit | `MainActivity.kt` — add launch gate in `onCreate` |
| Edit | `CalendarFlightsScreen.kt` — polish `EmptyState` + `NotRequested` copy |
| Edit | `LogbookScreen.kt` — polish empty state |
| Edit | `AndroidManifest.xml` — declare `OnboardingActivity` |

---

## Edge Cases to Test

| Scenario | Expected |
|---|---|
| First launch, no prior SharedPreferences | OnboardingActivity shown, `isComplete` = false |
| User taps Skip on page 1 | Jumps to page 3, does not mark complete yet |
| User grants calendar permission on page 3 | `markComplete` called, MainActivity launched, CalendarFlightsScreen shows Granted state |
| User denies calendar permission on page 3 | `markComplete` still called (onboarding done), MainActivity launched, CalendarFlightsScreen shows Denied state |
| User force-kills app mid-onboarding (page 2) | OnboardingActivity shown again on next launch (`isComplete` still false) |
| User reopens app after completing onboarding | MainActivity launched directly, onboarding never shown again |
| OnboardingActivity back-stack: system back on page 1 | App exits (do not re-show onboarding — `isComplete` check happens in MainActivity) |
| Rotation during onboarding | Pager state survives (`rememberSaveable` page index) |
| Logbook empty state: user taps "Add your first flight" | Navigates to AddEditFlightScreen with blank form |
