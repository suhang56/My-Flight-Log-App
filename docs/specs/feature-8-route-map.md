# Feature 8 — Route Map on Flight Detail Screen

## Overview

Replace the `MapPlaceholder()` composable in `FlightDetailScreen.kt` with a real great-circle route map. The map is rendered entirely via Compose `Canvas` — no external maps SDK, no API key, fully offline. All airport coordinate data already exists in `AirportCoordinatesMap`.

## Goals

- Show a visual great-circle route arc between departure and arrival airports on the Flight Detail screen.
- Work offline with zero new dependencies or APK size increase.
- Degrade gracefully when one or both airports are not in `AirportCoordinatesMap`.
- Match the existing 160dp height slot currently occupied by `MapPlaceholder()`.

## Non-Goals

- Interactive/zoomable/pannable map.
- Satellite or street-level imagery.
- Adding airports to `AirportCoordinatesMap` (out of scope — ~200 airports already sufficient for MVP).
- Displaying anything other than the single route arc.

---

## Architecture

### No External SDK

`AirportCoordinatesMap.coordinatesFor(iataCode: String): LatLng?` already provides all needed lat/lon data. The great-circle (Haversine) math is already implemented in `AirportCoordinatesMap.haversineNm()`. No Google Maps, OSMDroid, or Mapbox dependency is needed or desired.

### Projection

Use **equirectangular projection** (longitude → x, latitude → y linearly). This is sufficient for a small decorative map. The viewport is auto-fitted to keep both airports comfortably within frame with padding.

### Components

| Component | Location | Role |
|---|---|---|
| `RouteMapCanvas` | `ui/logbook/RouteMapCanvas.kt` | Pure composable — takes two nullable `LatLng`s, draws the map |
| `FlightDetailScreen.kt` | existing file | Replace `MapPlaceholder()` call at line 223 with `RouteMapCanvas(...)` |
| No ViewModel changes | — | Coordinates are looked up inline via `AirportCoordinatesMap` |

---

## Data Flow

```
FlightDetailScreen
  └─ uiState (FlightDetailUiState.Success)
       ├─ flight.departureCode  →  AirportCoordinatesMap.coordinatesFor()  →  LatLng?
       └─ flight.arrivalCode    →  AirportCoordinatesMap.coordinatesFor()  →  LatLng?
            └─ RouteMapCanvas(departure: LatLng?, arrival: LatLng?, ...)
```

The coordinate lookup happens inside `FlightDetailScreen` (composable scope), not in the ViewModel. These are pure in-memory lookups — no coroutine needed.

---

## Feature Spec

### RouteMapCanvas Composable

**Signature:**

```kotlin
@Composable
fun RouteMapCanvas(
    departure: LatLng?,
    arrival: LatLng?,
    departureIata: String,
    arrivalIata: String,
    modifier: Modifier = Modifier
)
```

**Behaviour:**

1. **Both coordinates known** — render full route map (see Drawing section below).
2. **One or both coordinates unknown** — render fallback state: same `surfaceVariant` background as the old `MapPlaceholder`, with a `Map` icon and the text "Route map unavailable for [IATA] / [IATA]" (whichever is missing).
3. **Same airport** (departure == arrival, or identical IATA codes) — render a single dot at the airport location with the IATA label. No arc.

### Drawing — Full Route Map

All drawing uses Compose `Canvas` (androidx.compose.foundation.Canvas).

**Step 1 — Compute viewport bounds**

Given the two `LatLng` points:
- Expand bounds by a padding factor of 20% on each axis (so airports are not right on the edge).
- Minimum span: 10 degrees lat and 15 degrees lon (prevents extreme zoom-in for very short routes).
- Clamp lat to [-85, 85] and lon to [-180, 180].

**Step 2 — Projection function**

```
x = (lon - minLon) / (maxLon - minLon) * canvasWidth
y = (maxLat - lat) / (maxLat - minLat) * canvasHeight   // y inverted: north = top
```

**Step 3 — Great-circle arc**

Interpolate N=60 points along the great-circle path between departure and arrival using spherical linear interpolation:

```
for i in 0..N:
    fraction = i / N.toFloat()
    lat_i, lon_i = greatCircleInterpolate(dep, arr, fraction)
    project to canvas coords
```

Connect with `drawPath` using a `Path` (moveTo first point, lineTo subsequent points).

**Step 4 — Draw layers (bottom to top)**

| Layer | Description |
|---|---|
| Background | Fill canvas rect with `MaterialTheme.colorScheme.surfaceVariant` |
| Arc | Stroke path — color `MaterialTheme.colorScheme.primary`, strokeWidth 2dp, `StrokeCap.Round` |
| Airport dots | Filled circle radius 5dp at each endpoint — color `MaterialTheme.colorScheme.primary` |
| IATA labels | Text at each endpoint — 10sp, `MaterialTheme.colorScheme.onSurface`, offset 8dp above the dot |

**Step 5 — Canvas size**

`modifier` is passed through. In `FlightDetailScreen`, the call site passes:
```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(160.dp)
    .clip(MaterialTheme.shapes.medium)
```

This exactly matches the `MapPlaceholder` dimensions so no layout shifts occur.

### Integration in FlightDetailScreen.kt

Replace the call at line 223 (approximately):

```kotlin
// Before:
MapPlaceholder()

// After:
val departureCoords = remember(flight.departureCode) {
    AirportCoordinatesMap.coordinatesFor(flight.departureCode)
}
val arrivalCoords = remember(flight.arrivalCode) {
    AirportCoordinatesMap.coordinatesFor(flight.arrivalCode)
}
RouteMapCanvas(
    departure = departureCoords,
    arrival = arrivalCoords,
    departureIata = flight.departureCode,
    arrivalIata = flight.arrivalCode,
    modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clip(MaterialTheme.shapes.medium)
)
```

The `MapPlaceholder()` private function is deleted entirely.

---

## Great-Circle Interpolation Math

Standard spherical interpolation between two lat/lon points:

```kotlin
fun greatCircleInterpolate(from: LatLng, to: LatLng, fraction: Float): LatLng {
    val lat1 = Math.toRadians(from.lat)
    val lon1 = Math.toRadians(from.lng)
    val lat2 = Math.toRadians(to.lat)
    val lon2 = Math.toRadians(to.lng)

    val d = 2 * asin(sqrt(
        sin((lat2 - lat1) / 2).pow(2) +
        cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2)
    ))

    if (d < 1e-10) return from  // same point guard

    val a = sin((1 - fraction) * d) / sin(d)
    val b = sin(fraction * d) / sin(d)

    val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
    val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
    val z = a * sin(lat1) + b * sin(lat2)

    val lat = atan2(z, sqrt(x * x + y * y))
    val lon = atan2(y, x)

    return LatLng(Math.toDegrees(lat), Math.toDegrees(lon))
}
```

This is a **private top-level function** in `RouteMapCanvas.kt` (not added to `AirportCoordinatesMap` which is data-layer).

---

## Antimeridian Handling

Routes crossing the 180th meridian (e.g., LAX→SYD, SFO→NRT) produce arc points that jump from ~+170 to ~-170 longitude. The equirectangular projection will draw a horizontal line across the full canvas instead of the correct short arc.

**Detection:** If `abs(lon2 - lon1) > 180`, the route crosses the antimeridian.

**Fix:** Normalize the arrival longitude relative to departure:
```kotlin
var lon2Normalized = lon2
if (lon2Normalized - lon1 > Math.PI) lon2Normalized -= 2 * Math.PI
if (lon2Normalized - lon1 < -Math.PI) lon2Normalized += 2 * Math.PI
```
Apply normalization before interpolation. Viewport bounds calculation uses the normalized coordinates. IATA label coordinates use the un-projected real lat/lon for display only.

---

## Files to Create / Modify

| File | Action |
|---|---|
| `app/src/main/java/com/flightlog/app/ui/logbook/RouteMapCanvas.kt` | Create — contains `RouteMapCanvas` composable + `greatCircleInterpolate` private function |
| `app/src/main/java/com/flightlog/app/ui/logbook/FlightDetailScreen.kt` | Modify — replace `MapPlaceholder()` call with `RouteMapCanvas(...)`, delete `MapPlaceholder` function |

No ViewModel changes. No new dependencies. No `build.gradle.kts` changes. No `libs.versions.toml` changes.

---

## Dependencies

**No new dependencies.** All required APIs are already available:
- `androidx.compose.foundation.Canvas` — already on classpath via `implementation(platform(libs.androidx.compose.bom))`
- `AirportCoordinatesMap` — already in the project
- `kotlin.math.*` — standard library

---

## Implementation Steps

1. Create `RouteMapCanvas.kt` with the composable and math helpers.
2. Modify `FlightDetailScreen.kt`: add `remember` blocks for coordinate lookups, replace `MapPlaceholder()` call with `RouteMapCanvas(...)`, delete the `MapPlaceholder` private function.
3. Build and run. Verify on a flight with known coordinates (e.g., PEK→SHA) and a flight with unknown airport codes.
4. Test antimeridian route (e.g., LAX→NRT if both in map, otherwise check manually).

---

## Edge Cases to Test

| # | Scenario | Input | Expected Output |
|---|---|---|---|
| 1 | Both airports in map | departure=PEK, arrival=SHA | Great-circle arc drawn, both IATA labels shown |
| 2 | Departure not in map | departure=XYZ, arrival=PEK | Fallback state: "Route map unavailable for XYZ" |
| 3 | Arrival not in map | departure=PEK, arrival=ZZZ | Fallback state: "Route map unavailable for ZZZ" |
| 4 | Both airports not in map | departure=AAA, arrival=BBB | Fallback state showing both missing codes |
| 5 | Empty departure code | departure="", arrival=PEK | `coordinatesFor("")` returns null → fallback state |
| 6 | Empty arrival code | departure=PEK, arrival="" | `coordinatesFor("")` returns null → fallback state |
| 7 | Same airport both ends | departure=PEK, arrival=PEK | Single dot + label, no arc drawn |
| 8 | Very short route (<100nm) | departure=PEK, arrival=TIJ (hypothetical ~50nm) | Minimum span enforced (10° lat, 15° lon), no extreme zoom |
| 9 | Very long route (>8000nm) | departure=JFK, arrival=SYD | Full arc displayed without clipping, viewport fits both |
| 10 | Antimeridian crossing | departure=LAX, arrival=NRT | Arc curves correctly across Pacific, no horizontal line artifact |
| 11 | Antimeridian opposite direction | departure=NRT, arrival=LAX | Same arc, no artifact (direction-independent) |
| 12 | Polar route | departure=LHR, arrival=ANC | Arc bows toward pole correctly in equirectangular projection |
| 13 | Route near equator | departure=SIN, arrival=CGK | Minimal lat curvature, arc appears nearly straight |
| 14 | IATA code lowercase | departure="pek", arrival="sha" | `coordinatesFor()` uses `.uppercase()` — map lookup succeeds |
| 15 | Flight with no departure or arrival codes | departure="", arrival="" | Both null → fallback state, no crash |
| 16 | Canvas renders in dark theme | Dark theme active | `surfaceVariant`, `primary`, `onSurface` tokens resolve correctly |
| 17 | Canvas renders at narrow width | Device with 320dp screen width | No text or dot clipping; IATA labels may truncate but no crash |
| 18 | Rapid recomposition | User navigates quickly to/from detail screen | `remember(flight.departureCode)` prevents redundant lookups |
| 19 | N interpolation points with d≈0 | Same lat/lon for dep and arr (distinct IATA codes mapping to same coords) | `d < 1e-10` guard returns `from` — single dot rendered |
| 20 | Arc path with >180° angular distance | Antipodal airports (~180° apart) | `d ≈ π` — interpolation still works; arc bows through any great circle |
