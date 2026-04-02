# Android 16 Material You — Style Guide

## Build Config (`app/build.gradle.kts`)
- `compileSdk = 36`
- `targetSdk = 36`

## Theme XML (`res/values/themes.xml`)
- Change parent: `android:Theme.Material.Light.NoActionBar` → `Theme.Material3.DayNight.NoActionBar`
- Add: `<item name="android:windowOptOutEdgeToEdgeEnforcement">false</item>`
- Create `res/values-night/themes.xml` with identical style for dark fallback

## Predictive Back (`AndroidManifest.xml`)
- Add `android:enableOnBackInvokedCallback="true"` to `MainActivity` and `OnboardingActivity`
- No NavGraph code changes needed — Compose Navigation handles it automatically

## Edge-to-Edge Insets (`MainActivity.kt`)
- `enableEdgeToEdge()` already called — keep as-is
- Remove blanket `Modifier.padding(innerPadding)` from `FlightNavGraph`
- `HomeScreen` (map + sheet): apply `WindowInsets.safeDrawing` only to the avatar overlay button
- All other screens: each `Scaffold` handles its own `innerPadding` normally
- `NavigationBar`: add `windowInsets = NavigationBarDefaults.windowInsets` to prevent double padding

## Dynamic Color Tokens (`Theme.kt`)
Add `surfaceContainer` family to both color schemes (enables hardcoded colors to be removed):

**Dark scheme additions:**
- `surfaceContainerLowest = Color(0xFF0F1113)`
- `surfaceContainerLow    = Color(0xFF1C1F23)` — replaces hardcoded SheetBackground
- `surfaceContainer       = Color(0xFF20242A)`
- `surfaceContainerHigh   = Color(0xFF272B30)` — replaces hardcoded CardBackground
- `surfaceContainerHighest= Color(0xFF2E3238)`

**Light scheme additions:**
- `surfaceContainerLowest = Color(0xFFFFFFFF)`
- `surfaceContainerLow    = Color(0xFFF4F6FB)`
- `surfaceContainer       = Color(0xFFEEF0F5)`
- `surfaceContainerHigh   = Color(0xFFE8EAF0)`
- `surfaceContainerHighest= Color(0xFFE2E4EA)`

## Typography Scale (`Theme.kt`)
Add `FlightLogTypography` val; pass to `MaterialTheme(typography = FlightLogTypography)`:
- `titleLarge` → 22sp / Medium weight (flight number labels)
- `titleMedium` → 16sp / +0.15sp tracking / Medium (route codes)
- `labelSmall` → 11sp / +0.5sp tracking / Medium (source badges)
- All other roles: MD3 defaults are acceptable

## Hardcoded Color Removals (`HomeScreen.kt`)
| Remove | Replace with |
|--------|-------------|
| `Color(0xFF1C1F23)` SheetBackground | `MaterialTheme.colorScheme.surfaceContainerLow` |
| `Color(0xFF272B30)` CardBackground | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `Color(0xFF9ECAFF)` AccentBlue | `MaterialTheme.colorScheme.primary` |
| `Color(0x99000000)` circle bg | `MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)` |

Delete the three `private val` color constants at top of file.

## Shape Scale (standardize)
| Component | Old | New |
|-----------|-----|-----|
| Bottom sheet corners | `RoundedCornerShape(24dp)` | `RoundedCornerShape(28dp)` (ExtraLarge token) |
| Flight cards | 12dp default | unchanged |
| Large FAB | — | `ShapeDefaults.ExtraLarge` (28dp, automatic) |
| Dialogs | MD3 default | `ShapeDefaults.ExtraLarge` (28dp, automatic) |

## Component Upgrades
- **LogbookScreen FAB**: `FloatingActionButton` → `LargeFloatingActionButton`, icon size 36dp
- **StatisticsScreen cards**: `ElevatedCard` → `Card(containerColor = surfaceContainerHigh)` (tonal over shadow)
- **StatisticsScreen + SettingsScreen TopAppBar**: add `TopAppBarDefaults.enterAlwaysScrollBehavior()` + `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on `Scaffold`

## Priority Order
1. themes.xml parent + manifest flags (zero-risk, unlocks everything)
2. Token additions to Theme.kt + hardcoded color removal in HomeScreen.kt
3. SDK bump to 36
4. Inset plumbing in MainActivity + HomeScreen
5. Component upgrades (FAB, cards, TopAppBar scroll behavior)
