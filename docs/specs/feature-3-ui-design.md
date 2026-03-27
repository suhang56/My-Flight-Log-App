# UI Design Spec: Feature 3 — Manual Flight Search/Add

**Date:** 2026-03-27
**Author:** UI/UX Designer
**Status:** Ready for Developer

---

## Design Overview

This feature adds a **Flight Search** section to the top of the existing `AddEditLogbookFlightScreen` in **add mode only**. The search section allows the user to enter a flight number and date, tap Search, and auto-fill route fields. The rest of the form remains unchanged.

Design priorities:
- Minimal disruption to the existing form layout
- Fast data entry: keyboard opens with Characters capitalization, IME Search action triggers lookup
- Clear state transitions: idle, loading, error, success
- High contrast, glanceable feedback

---

## Screen Layout: Add Flight (with Search)

### ASCII Wireframe — Idle State

```
+------------------------------------------+
| [<]  Add Flight                  [Save]  |
+------------------------------------------+
|                                          |
|  FLIGHT SEARCH                           |
|  +------------------+ +--------+ +----+  |
|  | Flight Number    | | Date   | | Q  |  |
|  | JL5              | | Mar 27 | |    |  |
|  +------------------+ +--------+ +----+  |
|                                          |
|  ----------------------------------------|
|                                          |
|  ROUTE                                   |
|  +-----------+  +-----------+            |
|  | From      |  | To        |            |
|  | ORD       |  | CMH       |            |
|  +-----------+  +-----------+            |
|  +----------------------------------+    |
|  | Flight Number                    |    |
|  | AA11                             |    |
|  +----------------------------------+    |
|                                          |
|  DATE & TIME                             |
|  +----------------------------------+    |
|  | Date              [calendar]     |    |
|  +----------------------------------+    |
|  +-----------+  +-----------+            |
|  | Departure |  | Arrival   |            |
|  | 10:30     |  | 14:15     |            |
|  +-----------+  +-----------+            |
|                                          |
|  DETAILS                                 |
|  +----------------------------------+    |
|  | Aircraft Type                    |    |
|  +----------------------------------+    |
|  +----------------------------------+    |
|  | Seat Class       [v]            |    |
|  +----------------------------------+    |
|  +----------------------------------+    |
|  | Seat Number                      |    |
|  +----------------------------------+    |
|                                          |
|  NOTES                                   |
|  +----------------------------------+    |
|  | Notes                            |    |
|  |                                  |    |
|  |                                  |    |
|  +----------------------------------+    |
+------------------------------------------+
```

### ASCII Wireframe — Loading State

```
|  FLIGHT SEARCH                           |
|  +------------------+ +--------+ +----+  |
|  | JL5              | | Mar 27 | | () |  |
|  +------------------+ +--------+ +----+  |
|                                          |
|  (form fields below are disabled/dimmed) |
```

`()` represents CircularProgressIndicator replacing the Search icon button.

### ASCII Wireframe — Error State

```
|  FLIGHT SEARCH                           |
|  +------------------+ +--------+ +----+  |
|  | ZZ999            | | Mar 27 | | Q  |  |
|  +------------------+ +--------+ +----+  |
|  ! Flight not found. Check the flight    |
|    number and date, or enter details     |
|    manually.                             |
|                                          |
```

Error text in `MaterialTheme.colorScheme.error` color, positioned directly below the search row.

### ASCII Wireframe — Success State (Auto-fill Banner)

```
|  FLIGHT SEARCH                           |
|  +------------------+ +--------+ +----+  |
|  | JL5              | | Mar 27 | | Q  |  |
|  +------------------+ +--------+ +----+  |
|  [x] Auto-filled from flight data       |
|                                          |
|  ROUTE                                   |
|  +-----------+  +-----------+            |
|  | From      |  | To        |            |
|  | NRT       |  | HND       |  <-- filled|
|  +-----------+  +-----------+            |
```

`[x]` is a dismissible `AssistChip` with a close trailing icon.

### Edit Mode (no search section)

```
+------------------------------------------+
| [<]  Edit Flight                 [Save]  |
+------------------------------------------+
|                                          |
|  ROUTE                                   |
|  (form starts directly, no search)       |
```

The search section is completely absent. The screen renders identically to the current edit form.

---

## Component Specifications

### 1. Search Section Container

- **Visibility:** Only when `!form.isEditMode`
- **Layout:** `Column` with `Arrangement.spacedBy(8.dp)`
- **Bottom border:** `HorizontalDivider` with `MaterialTheme.colorScheme.outlineVariant` separating search from the Route section
- **Padding:** Inherits parent 16.dp horizontal padding

### 2. Section Header: "Flight Search"

- **Composable:** `Text("Flight Search")`
- **Style:** `MaterialTheme.typography.titleSmall`
- **Color:** `MaterialTheme.colorScheme.primary`
- Matches existing section headers ("Route", "Date & Time", "Details", "Notes")

### 3. Search Row

A `Row` with `Arrangement.spacedBy(8.dp)`:

| Element | Weight/Width | Details |
|---------|-------------|---------|
| Flight Number field | `weight(1f)` | `OutlinedTextField`, single line |
| Date field | `weight(1f)` | Reuses `DatePickerField` composable (compact variant, see below) |
| Search button | `width(48.dp)` | `FilledIconButton` or `IconButton` with search icon |

#### 3a. Flight Number TextField

```kotlin
OutlinedTextField(
    value = form.flightSearchQuery,
    onValueChange = { viewModel.updateFlightSearchQuery(it) },
    label = { Text("Flight No.") },
    placeholder = { Text("JL5") },
    singleLine = true,
    enabled = !form.isSearching,
    keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Search
    ),
    keyboardActions = KeyboardActions(
        onSearch = { viewModel.searchFlight() }
    ),
    modifier = Modifier.weight(1f)
)
```

- Label: "Flight No." (abbreviated to fit the narrower width)
- Max characters: no hard limit (API handles validation)
- Input is auto-uppercased via `KeyboardCapitalization.Characters`
- IME Search action triggers `searchFlight()` directly from the keyboard

#### 3b. Search Date Picker

Reuses the existing `DatePickerField` composable with these adjustments:
- Bound to `form.flightSearchDate` (separate from the form's main `date` field)
- Label: "Date"
- Pre-filled with today's date
- `enabled = !form.isSearching`

Since the existing `DatePickerField` uses `Modifier.fillMaxWidth()` internally, the developer should either:
- Pass a `modifier` parameter with `Modifier.weight(1f)`, or
- Create a lightweight overload that accepts `modifier`

Recommendation: Add a `modifier` parameter to `DatePickerField` (currently hardcoded to `fillMaxWidth`).

#### 3c. Search Button / Loading Indicator

Two states, same position:

**Idle / Error state:**
```kotlin
FilledIconButton(
    onClick = { viewModel.searchFlight() },
    enabled = form.flightSearchQuery.isNotBlank() && !form.isSearching,
    modifier = Modifier.size(48.dp)
) {
    Icon(Icons.Default.Search, contentDescription = "Search flight")
}
```

- Uses `FilledIconButton` (MD3) for visual prominence
- Container color: `MaterialTheme.colorScheme.primary`
- Icon color: `MaterialTheme.colorScheme.onPrimary`
- Disabled when query is blank or search is in progress
- Min touch target: 48dp (meets accessibility guidelines)

**Loading state:**
```kotlin
Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
}
```

- Same 48dp footprint prevents layout shift
- 24dp indicator with 2dp stroke for a clean look

### 4. Error Message

```kotlin
if (form.searchError != null) {
    Text(
        text = form.searchError,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth()
    )
}
```

- Appears below the search row
- `bodySmall` (12sp) keeps it compact
- Error color from the theme for immediate recognition
- Dismissed automatically when user types a new query (via `updateFlightSearchQuery` clearing `searchError`)

### 5. Auto-fill Success Banner

```kotlin
if (form.autoFillApplied) {
    AssistChip(
        onClick = { viewModel.dismissAutoFillBanner() },
        label = { Text("Auto-filled from flight data") },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
```

- `AssistChip` (MD3) with close icon -- single tap dismisses
- `secondaryContainer` color provides a subtle, non-intrusive highlight
- Auto-dismissed when user edits any auto-filled field (departure/arrival code)
- Full width so it does not feel cramped in the narrow column

### 6. Divider (Search / Form Separator)

```kotlin
HorizontalDivider(
    modifier = Modifier.padding(vertical = 4.dp),
    color = MaterialTheme.colorScheme.outlineVariant
)
```

- Visually separates the search utility area from the main form
- Thin, subtle -- does not compete with section headers

---

## State Machine

```
                  +--------+
                  |  IDLE  |  (initial state in add mode)
                  +--------+
                    |    ^
        searchFlight()  |  error dismissed / new query typed
                    v    |
                 +----------+
                 | LOADING  |  isSearching = true
                 +----------+
                   /      \
          success /        \ failure
                 v          v
        +-----------+  +---------+
        | SUCCESS   |  | ERROR   |
        | autofill  |  | message |
        +-----------+  +---------+
              |              |
    dismiss / edit field     | new search / type query
              v              v
          +--------+     +--------+
          |  IDLE  |     |  IDLE  |
          +--------+     +--------+
```

### State-to-UI Mapping

| State | flightSearchQuery | isSearching | searchError | autoFillApplied | UI Effect |
|-------|------------------|-------------|-------------|-----------------|-----------|
| Idle (empty) | "" | false | null | false | Search button disabled, form editable |
| Idle (with query) | "JL5" | false | null | false | Search button enabled, form editable |
| Loading | "JL5" | true | null | false | Spinner replaces search button, form fields disabled |
| Error | "ZZ999" | false | "Flight not found..." | false | Error text shown, search button enabled, form editable |
| Success | "JL5" | false | null | true | Auto-fill chip shown, route fields populated, form editable |

---

## Form Field Disabling During Search

When `isSearching == true`:
- Flight search query field: `enabled = false`
- Search date picker: `enabled = false`
- Search button: replaced by spinner (not clickable)
- All form fields below (route, date, times, details, notes): `enabled = false`
- Save button in TopAppBar: `enabled = false` (already controlled by `!form.isSaving`, extend to also check `!form.isSearching`)

This prevents the user from editing fields while a lookup is in flight, avoiding race conditions between auto-fill and manual input.

---

## Navigation Flow

```
LogbookScreen                AddEditLogbookFlightScreen
+------------------+         +------------------------+
|                  |         |                        |
|  [Flight list]   |         |  [Search section]      |
|                  |         |  [Form fields]         |
|           [FAB+] --------> |                        |
|                  |  add    |            [Save] ---+  |
|                  |         |                     |  |
|                  | <------ |  [<] Back           |  |
|                  |  back   |                     |  |
|  [Flight card] ----------> |  (edit mode,        |  |
|                  |  edit   |   no search section) |  |
+------------------+         +------------------------+
                                        |
                                   save success
                                        |
                                        v
                              navigateBack() called
```

No new screens or navigation routes. The FAB on `LogbookScreen` already navigates to the add screen. The only UI change is the search section appearing at the top in add mode.

---

## Typography and Color Tokens

All tokens follow Material Design 3 / Material You. No custom colors.

| Element | Typography | Color Token |
|---------|-----------|-------------|
| Section headers | `titleSmall` | `colorScheme.primary` |
| Text field labels | Default `OutlinedTextField` | `colorScheme.onSurfaceVariant` |
| Text field values | Default `OutlinedTextField` | `colorScheme.onSurface` |
| Error text | `bodySmall` | `colorScheme.error` |
| Auto-fill chip label | `labelLarge` (chip default) | `colorScheme.onSecondaryContainer` |
| Auto-fill chip container | -- | `colorScheme.secondaryContainer` |
| Search button container | -- | `colorScheme.primary` |
| Search button icon | -- | `colorScheme.onPrimary` |
| Divider | -- | `colorScheme.outlineVariant` |
| Disabled fields | Default disabled opacity | MD3 default (38% alpha) |

---

## Accessibility

| Requirement | Implementation |
|-------------|---------------|
| Touch targets | Search button is 48x48dp minimum |
| Content descriptions | Search icon: "Search flight", Close chip icon: "Dismiss", Back arrow: "Back" (existing) |
| Screen reader | Error text announced via `semantics { error(searchError) }` on the search field, or `liveRegion = LiveRegionMode.Polite` on the error Text |
| Keyboard nav | IME Search action triggers lookup; Tab order follows visual top-to-bottom |
| Color contrast | All text uses MD3 theme tokens which meet WCAG AA by default in both light and dark themes |
| Loading state | CircularProgressIndicator has implicit "Loading" semantics |

---

## Interaction Details

### Keyboard Behavior
- Flight number field: `KeyboardCapitalization.Characters` ensures all-caps input without user effort
- `ImeAction.Search` shows a search icon on the soft keyboard's action key
- Tapping the keyboard search key calls `viewModel.searchFlight()` -- same as tapping the search button
- After search completes (success or error), keyboard remains open so the user can immediately retry or move to the next field

### Auto-fill Field Highlighting
On successful lookup, the departure and arrival code fields receive their new values. No special visual highlight is applied beyond the auto-fill chip banner -- the filled values themselves are the visual indicator. This keeps the UI clean and avoids custom styling that would diverge from MD3 defaults.

### Double-tap Prevention
The search button is disabled while `isSearching == true` (spinner is shown). The ViewModel should also guard against concurrent coroutine launches by cancelling any existing search job before starting a new one.

---

## Developer Notes

1. **DatePickerField modifier:** The existing `DatePickerField` composable hardcodes `Modifier.fillMaxWidth()`. Add an optional `modifier: Modifier = Modifier.fillMaxWidth()` parameter so it can accept `Modifier.weight(1f)` in the search row context.

2. **New imports needed:** `Icons.Default.Search`, `Icons.Default.Close`, `FilledIconButton`, `AssistChip`, `AssistChipDefaults`, `HorizontalDivider`, `CircularProgressIndicator`, `Box`, `Alignment`, `KeyboardActions`, `LiveRegionMode`.

3. **Save button guard:** Extend the Save button's `enabled` condition from `!form.isSaving` to `!form.isSaving && !form.isSearching`.

4. **Search date syncing:** When auto-fill succeeds, the form's main `date` field is set to `flightSearchDate`. This ensures the date picker in the "Date & Time" section reflects the searched date. The user can still change it afterward.

5. **Form field ordering:** The search section sits between the top `Spacer(4.dp)` and the "Route" section header. The divider sits between the search section and "Route".
