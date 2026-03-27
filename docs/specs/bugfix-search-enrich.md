# Bugfix/Enhancement: Enrich Flight Search Auto-Fill with Times + Aircraft

## Problem

`FlightRouteServiceImpl` ignores `departure.scheduled`, `arrival.scheduled`, and `aircraft` fields
from the AviationStack response. Auto-fill only populates route codes — users must enter times
and aircraft type manually even when the API returns them.

## Files to Change (4)

| File | Change |
|---|---|
| `AviationStackApi.kt` | Add `scheduled` to `AviationStackEndpoint`; add `AviationStackAircraft` + `aircraft` field to `AviationStackFlight` |
| `FlightRouteService.kt` | Add `departureScheduledUtc: Long?`, `arrivalScheduledUtc: Long?`, `aircraftType: String?` to `FlightRoute` |
| `FlightRouteServiceImpl.kt` | Parse scheduled times to UTC epoch millis; extract aircraft ICAO/IATA |
| `AddEditLogbookFlightViewModel.kt` | Auto-fill `departureTime`, `arrivalTime`, `aircraftType` from `FlightRoute` in `searchFlight()` |

## Model Changes

```kotlin
// AviationStackApi.kt — add to AviationStackEndpoint
@Json(name = "scheduled") val scheduled: String?   // ISO-8601 offset: "2026-03-27T18:20:00+00:00"

// AviationStackApi.kt — add to AviationStackFlight
@Json(name = "aircraft") val aircraft: AviationStackAircraft?

// New class — must have @JsonClass(generateAdapter = true)
@JsonClass(generateAdapter = true)
data class AviationStackAircraft(
    @Json(name = "iata") val iata: String?,
    @Json(name = "icao") val icao: String?
)

// FlightRouteService.kt — extend FlightRoute
data class FlightRoute(
    val flightNumber: String,
    val departureIata: String,
    val arrivalIata: String,
    val departureTimezone: String? = null,
    val arrivalTimezone: String? = null,
    val departureScheduledUtc: Long? = null,   // NEW
    val arrivalScheduledUtc: Long? = null,     // NEW
    val aircraftType: String? = null           // NEW — prefer ICAO if IATA is null
)
```

## Service Implementation

Parse in `FlightRouteServiceImpl.lookupRoute()` before constructing `FlightRoute`:

```kotlin
fun parseScheduledToUtc(iso: String?): Long? =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

val depUtc = parseScheduledToUtc(flight.departure?.scheduled)
val arrUtc = parseScheduledToUtc(flight.arrival?.scheduled)
val aircraft = flight.aircraft?.iata?.takeIf { it.isNotBlank() }
    ?: flight.aircraft?.icao?.takeIf { it.isNotBlank() }
```

Aircraft preference: IATA first (user-friendly e.g. "B77W"), fall back to ICAO, null if both absent.

## ViewModel Auto-Fill

In `searchFlight()`, after `route != null`, convert UTC epoch millis to `LocalTime` using the
route's timezone (or `ZoneId.systemDefault()` as fallback), then update form state:

```kotlin
val depZone = route.departureTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    ?: ZoneId.systemDefault()
val arrZone = route.arrivalTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    ?: ZoneId.systemDefault()

_form.update {
    it.copy(
        // existing fields...
        departureTime = route.departureScheduledUtc
            ?.let { ms -> Instant.ofEpochMilli(ms).atZone(depZone).toLocalTime() }
            ?: it.departureTime,                          // keep user value if null
        arrivalTime = route.arrivalScheduledUtc
            ?.let { ms -> Instant.ofEpochMilli(ms).atZone(arrZone).toLocalTime() },
        aircraftType = route.aircraftType ?: it.aircraftType  // keep user value if null
    )
}
```

Do NOT overwrite a field if the API returns null — preserve whatever the user already typed.

## Edge Cases to Test

| # | Scenario | Expected |
|---|---|---|
| 1 | `departure.scheduled` is null in response | `departureTime` stays at form default, no crash |
| 2 | `arrival.scheduled` is null | `arrivalTime` stays null, no crash |
| 3 | `scheduled` is malformed (not ISO-8601) | `runCatching` returns null, field not overwritten |
| 4 | Aircraft IATA null, ICAO present ("B77W") | `aircraftType` = "B77W" |
| 5 | Both aircraft fields null | `aircraftType` not overwritten, stays "" |
| 6 | Arrival time converts to earlier local time than departure (timezone difference) | Form shows correct local times; `save()` next-day logic handles cross-midnight correctly |
| 7 | `departureTimezone` null but `departureScheduledUtc` present | Converts using `ZoneId.systemDefault()` — time shown in device timezone |
| 8 | User pre-filled `aircraftType` before searching | API null → user's value preserved; API non-null → overwritten with API value |
| 9 | `AviationStackAircraft` missing `@JsonClass` annotation | R8 strips adapter → JSON parse silently returns null aircraft. **Must not forget annotation.** |
| 10 | `arrivalScheduledUtc` < `departureScheduledUtc` (bad API data) | Auto-fills times as-is; existing `save()` next-day logic already handles this |
