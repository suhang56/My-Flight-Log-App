# UI Design: Calendar-Synced Flight Display

## Overview

Pilots see flights pulled from their device calendar, displayed with flight number, route, and relative time badges. The screen uses a segmented tab control to switch between Upcoming and Past flights, supports pull-to-refresh, and handles all calendar permission states gracefully.

---

## 1. Navigation Entry Point

The feature is a **"Flights"** tab in the bottom navigation bar -- the app's primary landing screen.

```
+----------------------------------------------+
|                                               |
|                  (screen)                     |
|                                               |
+----------------------------------------------+
| [*Flights*]  [ Logbook ]  [ Stats ]  [ More ]|
+----------------------------------------------+
       ^active (filled icon + label)
```

- **Icon**: `Icons.Outlined.FlightTakeoff` (inactive), `Icons.Filled.FlightTakeoff` (active)
- **Label**: "Flights"
- Uses Material 3 `NavigationBar` component

---

## 2. Screen: Calendar Flights (Main View)

### 2.1 Wireframe -- Upcoming Tab Active

```
+----------------------------------------------+
|  Flights                          [Sync icon] |
|  Last synced: 2 min ago                       |
|----------------------------------------------+
|  [ Upcoming ]  |  [  Past  ]                 |
|  ^^^^^^^^^^^      (segmented button)         |
|----------------------------------------------+
|                                          (PTR)|
|  +------------------------------------------+|
|  | AA 0011                     [In 2 days]  ||
|  |                              (green)     ||
|  | ORD  -------->--------  CMH              ||
|  | Mar 29, 2026   08:30 - 10:45             ||
|  +------------------------------------------+|
|                                               |
|  +------------------------------------------+|
|  | UA 472                      [Tomorrow]   ||
|  |                              (green)     ||
|  | SFO  -------->--------  NRT              ||
|  | Mar 28, 2026   11:00 - 15:20 (+1)        ||
|  +------------------------------------------+|
|                                               |
|  +------------------------------------------+|
|  | DL 88                       [Today]      ||
|  |                              (orange)    ||
|  | LAX  -------->--------  HND              ||
|  | Mar 27, 2026   09:00 - 14:30 (+1)        ||
|  +------------------------------------------+|
|                                               |
+----------------------------------------------+
|  [ Flights ]  [ Logbook ]  [ Stats ]  [ More]|
+----------------------------------------------+
```

### 2.2 Wireframe -- Past Tab Active

```
+----------------------------------------------+
|  Flights                          [Sync icon] |
|  Last synced: 2 min ago                       |
|----------------------------------------------+
|  [ Upcoming ]  |  [* Past *]                 |
|                    ^^^^^^^^^                  |
|----------------------------------------------+
|                                               |
|  +------------------------------------------+|
|  | DL 1893                    [Yesterday]   ||
|  |                              (grey)      ||
|  | JFK  -------->--------  LAX              ||
|  | Mar 26, 2026   06:15 - 09:30             ||
|  +------------------------------------------+|
|                                               |
|  +------------------------------------------+|
|  | SW 204                     [5 days ago]  ||
|  |                              (grey)      ||
|  | DAL  -------->--------  HOU              ||
|  | Mar 22, 2026   14:00 - 15:10             ||
|  +------------------------------------------+|
|                                               |
+----------------------------------------------+
```

---

## 3. Component Specs

### 3.1 Top App Bar

- **Component**: Material 3 `MediumTopAppBar` (collapsing on scroll)
- **Title**: "Flights" -- `headlineSmall` (24sp), `onSurface`
- **Subtitle**: "Last synced: 2 min ago" -- `bodySmall` (12sp), `onSurfaceVariant`
  - Updates in real time relative to last sync timestamp
  - Shows "Never synced" if no sync has occurred
  - Shows "Syncing..." with animated dots during active sync
- **Trailing action**: Sync icon button (`Icons.Default.Sync`)
  - Animates with infinite clockwise rotation (800ms/revolution) while syncing
  - Touch target: 48dp

### 3.2 Segmented Button (Tabs)

- **Component**: Material 3 `SegmentedButton` (two segments)
- **Segments**: "Upcoming" | "Past"
- **Position**: Pinned below the top app bar, does not scroll
- **Horizontal padding**: 16dp from screen edges
- **Vertical spacing**: 8dp above, 12dp below
- **Behavior**: Switching tabs cross-fades the list content (200ms)
- "Upcoming" shows flights from today onward, sorted chronologically (soonest first)
- "Past" shows flights before today, sorted reverse-chronologically (most recent first)
- "Today" flights appear in BOTH tabs (at the top of Upcoming, at the top of Past)

### 3.3 Flight Card

- **Component**: Material 3 `ElevatedCard`
- **Corner radius**: 12dp
- **Elevation**: 1dp resting, 3dp pressed
- **Padding**: 16dp internal on all sides
- **Margin**: 8dp vertical between cards, 16dp horizontal from screen edges
- **Ripple**: Standard Material 3 ripple on tap

#### Card Layout

```
+----------------------------------------------+
|  AA 0011                       [In 2 days]   |
|                                 ^^badge^^     |
|  ORD  -------->>--------  CMH                |
|                                               |
|  Mar 29, 2026   08:30 - 10:45                |
+----------------------------------------------+
```

| Row | Left | Right | Spec |
|-----|------|-------|------|
| **Row 1** | Flight number | Relative time badge | See below |
| **Row 2** | Route visualization | | See below |
| **Row 3** | Date + time range | | See below |

**Row 1 -- Flight Number**
- Text: e.g. "AA 0011"
- Style: `titleMedium` (16sp), fontWeight 500, `onSurface`
- Vertically centered with the badge

**Row 1 -- Relative Time Badge**
- Alignment: End-aligned, vertically centered with flight number
- Shape: Rounded pill, 20dp height, 8dp horizontal padding
- Font: `labelSmall` (11sp), fontWeight 500
- Color coding (see Section 6 for exact tokens):

| Condition | Background | Text | Label examples |
|-----------|------------|------|----------------|
| **Today** | `#FFF3E0` (orange50) | `#E65100` (orange900) | "Today" |
| **Tomorrow** | `#E8F5E9` (green50) | `#1B5E20` (green900) | "Tomorrow" |
| **Future (2+ days)** | `#E8F5E9` (green50) | `#1B5E20` (green900) | "In 2 days", "In 1 week" |
| **Yesterday** | `#F5F5F5` (grey100) | `#616161` (grey700) | "Yesterday" |
| **Past (2+ days)** | `#F5F5F5` (grey100) | `#616161` (grey700) | "2 days ago", "1 week ago" |

**Row 2 -- Route Visualization**
- Layout: `Row` with airport codes flanking a route line
- Departure code: `titleLarge` (22sp), fontWeight 600, `onSurface`
- Arrival code: `titleLarge` (22sp), fontWeight 600, `onSurface`
- Between codes: dashed line (1.5dp, `outlineVariant`) with a small filled airplane icon (16dp, `primary`) at center pointing right
- Spacing: 8dp between code and line on each side
- Vertical spacing: 8dp above this row, 4dp below

**Row 3 -- Date & Time**
- Text: e.g. "Mar 29, 2026   08:30 - 10:45"
- Style: `bodyMedium` (14sp), `onSurfaceVariant`
- If flight crosses midnight: append "(+1)" after arrival time
- Vertical spacing: 4dp above this row

### 3.4 Pull-to-Refresh

- **Component**: Material 3 `PullToRefreshBox` wrapping the `LazyColumn`
- **Indicator**: Standard Material 3 circular progress indicator
- **Behavior**: Triggers calendar sync. On completion, updates list + "Last synced" subtitle.
- The pull-to-refresh is an alternative to tapping the sync icon -- same underlying action.

### 3.5 Sync Status Indicator

Lives in the top app bar subtitle area. States:

| Sync State | Subtitle Text | Style |
|------------|---------------|-------|
| **Never synced** | "Never synced" | `bodySmall`, `onSurfaceVariant` |
| **Syncing** | "Syncing..." | `bodySmall`, `primary`, animated ellipsis |
| **Just synced** | "Last synced: just now" | `bodySmall`, `onSurfaceVariant` |
| **Minutes ago** | "Last synced: 5 min ago" | `bodySmall`, `onSurfaceVariant` |
| **Hours ago** | "Last synced: 2 hours ago" | `bodySmall`, `onSurfaceVariant` |
| **Sync failed** | "Sync failed -- tap to retry" | `bodySmall`, `error` |

After sync completes, a `Snackbar` briefly appears:
- Success: "Synced -- 3 flights found" (with count)
- No results: "No flight events found in calendar"
- Error: "Could not read calendar" with "Retry" action

---

## 4. Permission States

The app must handle four permission states for `READ_CALENDAR`. Each state has a distinct UI treatment.

### 4.1 State: NOT_REQUESTED

Shown on first app launch before any sync attempt. Displayed as an **inline card** at the top of the Flights screen (above the segmented tabs area, or replacing the list content).

```
+----------------------------------------------+
|  Flights                          [Sync icon] |
|  Never synced                                 |
|----------------------------------------------+
|  [ Upcoming ]  |  [  Past  ]                 |
|----------------------------------------------+
|                                               |
|  +------------------------------------------+|
|  |  (calendar icon)                         ||
|  |                                          ||
|  |  Enable Calendar Access                  ||
|  |                                          ||
|  |  Flight Log can read your calendar to    ||
|  |  find and display your flights.          ||
|  |  No data is shared externally.           ||
|  |                                          ||
|  |       [ Enable Access ]                  ||
|  +------------------------------------------+|
|                                               |
+----------------------------------------------+
```

- **Container**: `ElevatedCard`, same styling as flight cards
- **Icon**: `Icons.Outlined.CalendarMonth`, 48dp, `primary`
- **Title**: "Enable Calendar Access" -- `titleMedium` (16sp), `onSurface`
- **Body**: `bodyMedium` (14sp), `onSurfaceVariant`, max-width 280dp
- **Button**: `FilledButton`, "Enable Access" -- triggers Android runtime permission dialog
- The card replaces the flight list. Tabs are still visible but show this card in both tabs.

### 4.2 State: GRANTED

Normal operating state. Flight list is shown. No permission UI visible.

### 4.3 State: DENIED (Soft Deny)

User tapped "Deny" on the Android permission dialog, but has NOT checked "Don't ask again." The app can still request permission again.

```
+------------------------------------------+
|  (warning icon, orange)                  |
|                                          |
|  Calendar Access Denied                  |
|                                          |
|  Without calendar access, Flight Log     |
|  cannot find your flights. Tap below     |
|  to grant access.                        |
|                                          |
|       [ Grant Access ]                   |
+------------------------------------------+
```

- Same card layout as NOT_REQUESTED
- **Icon**: `Icons.Outlined.Warning`, 48dp, `#E65100` (orange)
- **Title**: "Calendar Access Denied" -- `titleMedium`, `onSurface`
- **Button**: `FilledTonalButton`, "Grant Access" -- re-triggers runtime permission dialog
- Also displayed as the list content, replacing flight cards

### 4.4 State: PERMANENTLY_DENIED

User checked "Don't ask again" or denied twice (Android behavior). The app cannot trigger the permission dialog anymore.

```
+------------------------------------------+
|  (settings icon, error color)            |
|                                          |
|  Calendar Access Blocked                 |
|                                          |
|  Calendar permission was permanently     |
|  denied. Please enable it manually       |
|  in your device settings.                |
|                                          |
|       [ Go to Settings ]                 |
+------------------------------------------+
```

- **Icon**: `Icons.Outlined.Settings`, 48dp, `error`
- **Title**: "Calendar Access Blocked" -- `titleMedium`, `onSurface`
- **Body**: `bodyMedium`, `onSurfaceVariant`
- **Button**: `FilledButton`, "Go to Settings" -- opens Android app settings via `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`
- When user returns from settings, check permission state and update UI accordingly

### 4.5 Permission State Summary Table

| State | Icon | Title | Button | Button Action |
|-------|------|-------|--------|---------------|
| NOT_REQUESTED | CalendarMonth (primary) | Enable Calendar Access | "Enable Access" (Filled) | `requestPermission()` |
| DENIED | Warning (orange) | Calendar Access Denied | "Grant Access" (FilledTonal) | `requestPermission()` |
| PERMANENTLY_DENIED | Settings (error) | Calendar Access Blocked | "Go to Settings" (Filled) | Open app settings intent |
| GRANTED | -- | -- | -- | Show flight list |

---

## 5. Empty State (Permission Granted, No Flights Found)

When the calendar has been synced but no flight events were detected.

```
+----------------------------------------------+
|  Flights                          [Sync icon] |
|  Last synced: just now                        |
|----------------------------------------------+
|  [ Upcoming ]  |  [  Past  ]                 |
|----------------------------------------------+
|                                               |
|            (airplane illustration)            |
|                                               |
|         No flights found                      |
|                                               |
|   We looked through your calendar but         |
|   couldn't find any flight events.            |
|                                               |
|   Flight events usually look like:            |
|   "Flight AA0011 ORD-CMH"                    |
|                                               |
|         [ Sync Again ]                        |
|                                               |
+----------------------------------------------+
```

- **Illustration**: Outlined airplane icon, 64dp, `onSurfaceVariant` at 40% alpha
- **Heading**: "No flights found" -- `titleMedium` (16sp), `onSurface`
- **Body**: `bodyMedium` (14sp), `onSurfaceVariant`, centered, max 300dp width
- **Hint**: Shows an example of what a calendar flight event should look like to help users
- **Button**: `OutlinedButton`, "Sync Again"
- This state is per-tab: "Upcoming" tab can be empty while "Past" has flights, and vice versa
- Per-tab empty message: "No upcoming flights" / "No past flights"

---

## 6. Color Tokens & Typography

### 6.1 Color Palette

**Base theme**: Material 3 dynamic color (Material You) on Android 12+.
**Fallback seed color**: `#1565C0` (aviation blue).

#### Custom Semantic Colors (Badge System)

These are custom color tokens layered on top of the Material 3 theme:

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `badgeUpcomingBg` | `#E8F5E9` (green50) | `#1B5E20` at 30% | Upcoming badge background |
| `badgeUpcomingText` | `#1B5E20` (green900) | `#A5D6A7` (green200) | Upcoming badge text |
| `badgeTodayBg` | `#FFF3E0` (orange50) | `#E65100` at 30% | Today badge background |
| `badgeTodayText` | `#E65100` (orange900) | `#FFCC80` (orange200) | Today badge text |
| `badgePastBg` | `#F5F5F5` (grey100) | `#424242` (grey800) | Past badge background |
| `badgePastText` | `#616161` (grey700) | `#BDBDBD` (grey400) | Past badge text |

#### Standard Material 3 Roles Used

| Role | Usage |
|------|-------|
| `primary` | Sync icon, route airplane icon, CTA buttons |
| `onSurface` | Flight number, airport codes, card titles |
| `onSurfaceVariant` | Dates, times, subtitles, secondary text |
| `surface` | Card backgrounds |
| `surfaceContainerLow` | Screen background |
| `outlineVariant` | Route dashed line, dividers |
| `error` | Permission blocked icon, sync error text |

### 6.2 Typography Scale

| Element | M3 Style | Size | Weight | Color |
|---------|----------|------|--------|-------|
| Screen title | `headlineSmall` | 24sp | 400 | `onSurface` |
| Sync status subtitle | `bodySmall` | 12sp | 400 | `onSurfaceVariant` |
| Segmented button labels | `labelLarge` | 14sp | 500 | per M3 spec |
| Flight number (card) | `titleMedium` | 16sp | 500 | `onSurface` |
| Airport codes | `titleLarge` | 22sp | 600 | `onSurface` |
| Date & time | `bodyMedium` | 14sp | 400 | `onSurfaceVariant` |
| Badge text | `labelSmall` | 11sp | 500 | per badge color |
| Flight number (detail sheet) | `headlineMedium` | 28sp | 400 | `onSurface` |
| Permission card title | `titleMedium` | 16sp | 500 | `onSurface` |
| Permission card body | `bodyMedium` | 14sp | 400 | `onSurfaceVariant` |
| Empty state heading | `titleMedium` | 16sp | 500 | `onSurface` |
| Empty state body | `bodyMedium` | 14sp | 400 | `onSurfaceVariant` |

### 6.3 Cockpit Usability

- **Minimum touch target**: 48dp for ALL interactive elements (buttons, icons, cards)
- **Card padding**: 16dp -- comfortable tapping with flight gloves
- **Font weights**: Minimum 400 (regular). No thin/light weights anywhere.
- **Contrast**: WCAG AA minimum (4.5:1 for body text, 3:1 for large text). Badge colors chosen to meet this.
- **Badge fills**: Solid background, never outline-only -- must be glanceable at arm's length
- **Airport codes**: Extra-large (22sp) and semi-bold for instant recognition

---

## 7. Flight Detail Bottom Sheet

Tapping a flight card opens a `ModalBottomSheet` with full details.

### 7.1 Wireframe

```
+----------------------------------------------+
|  -----------  (drag handle)                   |
|                                               |
|  AA 0011                        [In 2 days]  |
|  American Airlines               (green)     |
|                                               |
|  ORD  ============>>===========  CMH         |
|  Chicago O'Hare                  Columbus    |
|  08:30                           10:45       |
|                                               |
|  ------------------------------------------- |
|                                               |
|  Date           Mar 29, 2026                  |
|  Duration       2h 15m                        |
|  Status         In 2 days                     |
|  Calendar       Google Calendar               |
|                                               |
|  +------------------+  +------------------+  |
|  | Add to Logbook   |  |    Dismiss       |  |
|  +------------------+  +------------------+  |
|                                               |
+----------------------------------------------+
```

### 7.2 Component Specs

- **Container**: Material 3 `ModalBottomSheet`, default drag handle
- **Flight number**: `headlineMedium` (28sp), `onSurface`
- **Airline name**: `bodyLarge` (16sp), `onSurfaceVariant` -- derived from IATA prefix if recognizable, otherwise omitted
- **Badge**: Same relative time badge as the card, positioned end-aligned on the first row
- **Route visual**: Departure and arrival codes in `titleLarge` (22sp), city names in `bodySmall` (12sp) below each code, dashed line with airplane icon between
- **Times**: Below each city name, `bodyMedium` (14sp), `onSurfaceVariant`
- **Divider**: 1dp `outlineVariant` horizontal line, 16dp vertical margin
- **Detail rows**: Two-column layout -- label in `bodyMedium` `onSurfaceVariant`, value in `bodyLarge` `onSurface`
- **"Add to Logbook"**: `FilledButton` (primary) -- creates a logbook entry pre-filled with flight data
- **"Dismiss"**: `OutlinedButton` -- hides this flight from the synced list (soft delete)
- **Button row**: Horizontal arrangement, equal weight, 8dp gap, 16dp horizontal padding

---

## 8. Sync Trigger Flow

```
User triggers sync (pull-to-refresh OR sync icon OR "Sync" button)
           |
           v
   Check calendar permission state
           |
   +-------+-------+----------------+
   |       |       |                |
   v       v       v                v
 NOT_REQ  DENIED  PERM_DENIED     GRANTED
   |       |       |                |
   v       v       v                v
 Show     Show    Show "Go to     Start sync
 Enable   Grant   Settings"         |
 card     card    card               v
   |       |       |            Read calendar
 (user   (user   (user opens    (30 days back,
  taps)   taps)   settings)      30 days forward)
   |       |       |                |
   v       v       v                v
 Android  Android  Return to    Parse events
 dialog   dialog   app: re-     for flight
   |       |       check perm   patterns
   |       |       |                |
   v       v       v                v
 Granted? Granted? Granted?     Upsert to
 Y -> sync Y -> sync Y -> sync  Room DB
 N -> show N -> show N -> show      |
 Denied   Denied   Perm_Denied     v
 card     card     card         Refresh UI +
                                show snackbar
```

### Sync Behavior Details
- **Trigger**: Manual only for v1 -- pull-to-refresh, sync icon, or sync button
- **Scan window**: 30 days past + 30 days future from current date
- **Event pattern matching**: Regex for patterns like "Flight AA0011 ORD-CMH", "AA 0011 ORD to CMH", etc.
- **Duplicate handling**: Match on flight number + date. Upsert (update if exists, insert if new).
- **Sync indicator**: Icon rotates, subtitle shows "Syncing...", pull-to-refresh indicator active
- **Completion**: Subtitle updates to "Last synced: just now", snackbar with flight count

---

## 9. Animations & Transitions

| Trigger | Animation | Duration |
|---------|-----------|----------|
| Sync in progress | Sync icon infinite clockwise rotation | 800ms per revolution |
| Tab switch | Cross-fade between Upcoming and Past lists | 200ms |
| New cards appear after sync | Staggered fade-in + slide-up from 16dp below | 200ms per card, 50ms stagger |
| Card tap | Standard ripple, then bottom sheet slide-up | M3 default |
| Pull-to-refresh | M3 circular progress indicator | System default |
| Permission card appear | Fade-in + scale from 0.95 to 1.0 | 300ms, easeOut |
| Snackbar | Slide-up from bottom | M3 default |
| Navigation (bottom nav) | Shared axis (horizontal) | 300ms |

---

## 10. Accessibility

- **Card content descriptions**: "Flight AA 0011, Chicago O'Hare to Columbus, March 29, in 2 days"
- **Badge**: Included in card's content description, NOT a separate focusable element
- **Sync button**: Announces state changes -- "Sync Calendar" / "Syncing" / "Sync complete, 3 flights found"
- **Segmented button**: Standard M3 accessibility -- "Upcoming, selected" / "Past"
- **Permission cards**: Title + body read as a single unit, button separately focusable
- **Empty state**: Heading + body read together, button separately focusable
- **Bottom sheet**: Focus trap while open, Escape/back to dismiss
- **Section semantics**: Tab content regions use `semantics { contentDescription = "..." }`
