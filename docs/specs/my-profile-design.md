# My Profile Page — Design Spec

## Overview
- New `ProfileScreen` consolidates account info, backup controls, settings, and app info
- Replaces the floating Settings gear on HomeScreen
- Accessible via a profile avatar `IconButton` (top-right on map) or a bottom nav tab

## Home Screen Changes
- Remove: `Settings` `IconButton` (Surface + CircleShape, top-right, lines 131–145 of `HomeScreen.kt`)
- Add: `Person` avatar `IconButton` in the same position (same CircleShape/dark surface styling)
- Callback renamed: `onNavigateToSettings` → `onNavigateToProfile`
- No other home screen changes

## Navigation
- Add `Routes.PROFILE = "profile"` to `NavGraph.kt` `Routes` object
- Add composable entry: `ProfileScreen(onNavigateBack, onNavigateToLogin)`
- Update `CalendarFlightsScreen` entry in `FlightNavGraph` to pass `onNavigateToProfile`
- Optionally add `Profile` as a third bottom nav item (icon: `Icons.Default.Person`, label: "Profile")

## ProfileScreen Layout

### TopAppBar
- Title: "My Profile"
- Navigation icon: back arrow (`Icons.AutoMirrored.Filled.ArrowBack`)

### Header Card (`surfaceVariant` background, 16dp horizontal margin)
- 56dp circular avatar: initials on `primaryContainer` fill, `onPrimaryContainer` text
  - Populated from Google displayName; fallback to `Icons.Default.Person`
- Display name: `titleLarge`, `onSurface`
- Email: `bodyMedium`, `onSurfaceVariant`
- Provider chip: `SuggestionChip` — "Google" / "GitHub" / "Email"
- If signed out: show "Guest", hide email and chip

### Section: Account
- `ListItem`: "Signed in as" / email (when authenticated)
- `ListItem`: `OutlinedButton("Sign Out")` (when authenticated)
- `ListItem`: "Sign in to enable backup" + `Button("Sign In")` (when guest)

### Section: Backup (migrated from SettingsScreen verbatim)
- `BackupInfoItem` if metadata present, else "No backup yet" list item
- Drive-requires-Google warning row when non-Google user is signed in
- Button row: `Button("Back Up Now")` + `OutlinedButton("Restore")`, both `weight(1f)`
- Progress indicator (`CircularProgressIndicator`, 18dp) replaces icon while in-flight
- `RestoreConfirmDialog` — identical to current SettingsScreen implementation

### Section: About
- `ListItem`: "Version" / "1.0.0"
- Spacer 24dp at bottom

## ViewModel
- Reuse `SettingsViewModel` (rename to `ProfileViewModel` optional)
- No new state needed — same `authUser`, `backupMetadata`, `isBackingUp`, `isRestoring`

## SettingsScreen
- Remove Account + Backup sections (now in ProfileScreen)
- Keep route alive or retire; if kept, reduce to Preferences + About only

## Component Specs
| Component | Spec |
|-----------|------|
| Avatar circle | 56dp, `CircleShape`, `primaryContainer` fill, initials `titleMedium` |
| Profile button on Home | 48dp, `CircleShape` surface, `scrim.copy(alpha=0.6f)` bg, `Person` icon white 80% |
| Section header style | `titleSmall`, `primary`, `SemiBold`, `padding(h=16dp, v=12dp)` |
| Backup button row | `Arrangement.spacedBy(12dp)`, `weight(1f)` each |
