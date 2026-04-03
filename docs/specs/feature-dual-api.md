# Feature Spec: Dual-API Flight Lookup

**Date:** 2026-04-03
**Author:** Planner
**Status:** Ready for Architect + Developer

---

## Overview

Extend flight lookup to support schedules 6+ months in the future via AviationStack, while
keeping FlightAware for recent/near-future flights that need richer data (registration, status,
gates). The existing `FlightRouteService` interface is unchanged — only the implementation
and DI wiring change.

---

## Routing Logic

| Date range (relative to today UTC) | API used | Reason |
|------------------------------------|----------|--------|
| today − ∞ through today + 7 days | FlightAware AeroAPI | Real-time data, registration, gates |
| today + 8 days through future | AviationStack | Schedule data, 6+ months ahead |

Threshold constant: `FLIGHTAWARE_WINDOW_DAYS = 7` in `FlightRouteServiceImpl`.

Fallback chain: if the primary API returns empty/error, try the other API before returning null.
If both fail, return null so the ViewModel shows the manual-entry fallback.

---

## 1. API Interface + Response Models

### New file: `AviationStackApi.kt`

```kotlin
// package com.flightlog.app.data.network
interface AviationStackApi {
    @GET("flightsFuture")
    suspend fun getScheduledFlights(
        @Query("access_key") accessKey: String,
        @Query("iataCode") iataCode: String,
        @Query("type") type: String,           // "departure" | "arrival"
        @Query("date") date: String,           // "YYYY-MM-DD"
        @Query("flight_iata") flightIata: String? = null
    ): Response<AviationStackResponse>
}
```

### New file: `AviationStackModels.kt`

```kotlin
data class AviationStackResponse(val data: List<AviationStackFlight>?)

data class AviationStackFlight(
    val flight: AviationStackFlightCode?,
    val departure: AviationStackAirport?,
    val arrival: AviationStackAirport?,
    val airline: AviationStackAirline?,
    val aircraft: AviationStackAircraft?
)

data class AviationStackFlightCode(val iata: String?, val icao: String?)
data class AviationStackAirport(val iata: String?, val timezone: String?, val scheduled: String?)
data class AviationStackAirline(val name: String?, val iata: String?)
data class AviationStackAircraft(val modelCode: String?, val modelText: String?)
```

### Data Mapping: AviationStack → FlightRoute

```
flight.flight.iata          → flightNumber
departure.iata              → departureIata  (null → skip flight)
arrival.iata                → arrivalIata    (null → skip flight)
departure.timezone          → departureTimezone
arrival.timezone            → arrivalTimezone
departure.scheduled (ISO)   → departureScheduledUtc (parse via parseIsoToUtc)
arrival.scheduled (ISO)     → arrivalScheduledUtc
aircraft.modelCode          → aircraftType
registration                → null (AviationStack does not provide it)
```

---

## 2. FlightRouteServiceImpl Orchestration

Inject both APIs. Dispatch by date; fall back to the other API on failure.

```kotlin
class FlightRouteServiceImpl @Inject constructor(
    private val flightAwareApi: FlightAwareApi,
    private val aviationStackApi: AviationStackApi
) : FlightRouteService
```

Key internal method (pseudocode):

```
fun selectApi(date): Api =
    if date <= today + FLIGHTAWARE_WINDOW_DAYS → FlightAwareApi
    else → AviationStackApi

suspend fun lookupAllRoutes(flightNumber, date):
    primary = selectApi(date)
    results = fetchFrom(primary, flightNumber, date)
    if results.isEmpty → results = fetchFrom(otherApi, flightNumber, date)
    return results
```

AviationStack requires `iataCode` (departure airport). When calling AviationStack, the
departure airport is passed in from the ViewModel (see UI changes). If it is absent, skip the
AviationStack call and return empty immediately (callers should surface the field requirement
to the user before calling).

---

## 3. NetworkModule.kt DI Changes

Add a second Retrofit instance for AviationStack:
- Base URL: `http://api.aviationstack.com/v1/` (HTTP, not HTTPS — free tier restriction)
- Auth: `access_key` query param per-request (NOT a header interceptor)
- Separate `OkHttpClient` without the FlightAware `x-apikey` interceptor

```kotlin
@Provides @Singleton @Named("aviationStack")
fun provideAviationStackOkHttpClient(): OkHttpClient  // logging only, no auth header

@Provides @Singleton @AviationStackRetrofit
fun provideAviationStackRetrofit(@Named("aviationStack") client: OkHttpClient, moshi: Moshi): Retrofit
    // baseUrl = "http://api.aviationstack.com/v1/"

@Provides @Singleton
fun provideAviationStackApi(@AviationStackRetrofit retrofit: Retrofit): AviationStackApi
```

New qualifier annotation: `@AviationStackRetrofit` (mirrors existing `@PlanespottersRetrofit`).

Network security: add `res/xml/network_security_config.xml` domain rule to permit cleartext
for `api.aviationstack.com` only. All other traffic remains HTTPS-only.

---

## 4. Add Flight Form UI Changes

When the selected date is > today + 7 days, show an optional "Departure airport" field
in `AddEditLogbookFlightScreen`. The field is pre-filled if `departureCode` is already set.

State additions in `AddEditFormState`:

```kotlin
val departurAirportForLookup: String = "",  // separate from saved departureCode
val showDepartureAirportLookupField: Boolean = false
```

ViewModel logic: recompute `showDepartureAirportLookupField` whenever `flightSearchDate`
changes. Pass `departurAirportForLookup` into `lookupRoute(flightNumber, date, depAirport?)`.

Update `FlightRouteService` interface:

```kotlin
suspend fun lookupRoute(flightNumber: String, date: LocalDate, departureAirport: String? = null): FlightRoute?
suspend fun lookupAllRoutes(flightNumber: String, date: LocalDate, departureAirport: String? = null): List<FlightRoute>
```

---

## 5. Edge Cases to Test

| # | Scenario | Input | Expected |
|---|----------|-------|----------|
| E1 | Date exactly at boundary (today + 7) | date = today+7 | FlightAware used |
| E2 | Date at boundary + 1 (today + 8) | date = today+8 | AviationStack used |
| E3 | AviationStack 429 rate limit | API returns 429 | Return empty; ViewModel shows "Too many requests, wait 60s" via searchError |
| E4 | HTTP cleartext blocked | Network security config missing domain rule | App fails at build/startup; caught by security config test |
| E5 | Empty AviationStack results | data = [] | Fallback to FlightAware; if also empty → null |
| E6 | Both APIs fail | Both return empty | lookupRoute returns null; UI shows manual-entry fallback |
| E7 | Missing departure airport for future lookup | depAirport = null, date > +7d | Skip AviationStack call; return empty; UI must show field prompt |
| E8 | AviationStack missing iata codes | departure.iata = null | Skip that flight in mapping; if all null → empty list |
| E9 | 100 req/month exhausted (402/403) | API returns 4xx != 429 | Treat as empty; log warning; do not crash |
| E10 | CancellationException during API call | Coroutine cancelled mid-flight | Rethrow — never swallow |
| E11 | Date in the past (>7 days ago) | date = today-30 | FlightAware used (historical); existing strategies apply |
| E12 | Timezone straddles midnight | Flight departs 23:50 local = next UTC day | filterByDate ±1 day tolerance handles this |

---

## 6. Task Breakdown

### Task 1 — Models + API Interface
Files:
- `app/.../data/network/AviationStackApi.kt` (new)
- `app/.../data/network/AviationStackModels.kt` (new)
- `app/.../di/AviationStackRetrofit.kt` (new qualifier)

### Task 2 — NetworkModule wiring + network security config
Files:
- `app/.../di/NetworkModule.kt` (add AviationStack Retrofit + client)
- `app/src/main/res/xml/network_security_config.xml` (add cleartext domain allowlist)
- `app/src/main/AndroidManifest.xml` (verify networkSecurityConfig attribute present)

### Task 3 — FlightRouteServiceImpl orchestration
Files:
- `app/.../data/network/FlightRouteServiceImpl.kt` (add AviationStack dispatch + mapping)
- `app/.../data/network/FlightRouteService.kt` (add optional departureAirport param)

### Task 4 — Add Flight form UI
Files:
- `app/.../ui/logbook/AddEditLogbookFlightViewModel.kt` (showDepartureAirportLookupField, pass param)
- `app/.../ui/logbook/AddEditLogbookFlightScreen.kt` (conditional departure airport field)

### Task 5 — Tests
Files:
- `app/src/test/.../network/FlightRouteServiceImplTest.kt` (dispatch logic, fallback, mapping)
- `app/src/test/.../ui/logbook/AddEditFlightViewModelTest.kt` (future-date field visibility, 429 error)

---

## 7. Dependencies

| Item | Status |
|------|--------|
| `AVIATION_STACK_KEY` in `local.properties` + `BuildConfig` | Already present |
| `FlightAwareApi` | Exists, no changes |
| `FlightRouteService` interface | Minor signature change (optional param) |
| `FlightRouteServiceImpl` | Major change — inject both APIs |
| `NetworkModule` | Add AviationStack Retrofit instance |
| Network security config | New or extend existing |
| Retrofit + Moshi | Already in project |
| `AddEditLogbookFlightViewModel` | Minor — pass departure airport to lookup |
| `AddEditLogbookFlightScreen` | Minor — conditional field |
