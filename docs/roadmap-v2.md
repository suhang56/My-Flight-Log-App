# Flight Log App — Roadmap v2

**Date:** 2026-04-02
**Author:** Planner agent
**Status:** Post-Phase-2 planning (DB v4, all Phase 2 features shipped)
**App version at time of writing:** 1.0.0

---

## Baseline Assessment

### What Is Shipped

The app is a feature-complete **passenger flight tracker** for airline travelers:

| Layer | Shipped |
|---|---|
| Data | Room DB v4 — CalendarFlight, LogbookFlight, Achievement, Airport, FlightStatus |
| Sync | Calendar sync (WorkManager, 6h periodic), Google Drive backup (JSON) |
| Tracking | FlightAware AeroAPI — live status, position polling, push notifications |
| UI | Home, Logbook (CRUD + search + filter + trip grouping), Statistics (12+ metrics), Achievements (18 badges), Calendar Flights + Google Maps, Settings, Onboarding, Widget |
| Export | CSV + JSON via ExportService |
| Auth | Firebase Auth + Google Sign-In |
| Infrastructure | Hilt DI, Retrofit + OkHttp, Moshi, Glance widget, Jetpack Compose Material 3 |

### Critical Gap Identified

The `LogbookFlight` entity is designed for **passenger tracking**, not **professional pilot logging**.

Fields present but passenger-only: `seatClass`, `seatNumber`, `flightNumber` (airline IATA).

Fields absent but legally required for FAA/EASA pilot certificates:
- PIC / SIC / dual / solo time
- Night time (actual)
- Instrument time (actual IMC + simulated IMC)
- Cross-country designation
- Day and night landings / full-stop landings
- Instrument approaches with type and location
- Aircraft tail number (registration), not just type
- Engine category (single, multi, turbine, jet)

Without these, the app **cannot be used by pilots for regulatory currency tracking**, which is the entire professional use case. This is the single largest gap versus specialized competitors.

---

## Competitive Landscape

### Flighty (iOS-first, passenger tracker)
- Strengths: best-in-class live tracking UI, share cards, seat maps, beautiful animations
- Weaknesses: iOS only, no pilot logbook, subscription-gated, no offline logbook export
- Our edge: Android-native, free tier, pilot logbook mode (missing from Flighty entirely)

### ForeFlight (pilot-only, iOS)
- Strengths: FAA-compliant logbook, weather briefings, charts, regulatory gold standard
- Weaknesses: expensive ($99-$249/year), iOS only, bloated for passenger use
- Our edge: free tier, Android, dual passenger/pilot mode, modern Compose UI

### myFlightradar24 (Android/iOS, passenger tracker)
- Strengths: large airport/airline database, AR view, good map
- Weaknesses: no logbook, no pilot fields, basic statistics
- Our edge: logbook depth, pilot compliance, achievements/gamification

### LogTen Pro (pilot logbook, iOS/Mac)
- Strengths: comprehensive FAA/EASA fields, CSV import from other apps
- Weaknesses: iOS/Mac only, expensive, complex UI for non-pilots
- Our edge: Android, combined passenger+pilot in one app, free

### FlightLogger (web, pilot logbook)
- Strengths: multi-platform, EASA compliant, crew tracking
- Weaknesses: web-only, no native Android app, subscription
- Our edge: native Android, offline-first, free tier

### Key Differentiators We Can Own
1. **Only free Android app with FAA/EASA pilot logbook fields** — nobody else has this on Android
2. **Dual mode** (passenger tracker + pilot logbook) in one app
3. **Offline-first** — pilots fly in areas with no connectivity
4. **Achievement/gamification layer** — unique to us, not in any competitor
5. **Calendar sync** — automatic import from airline booking emails (unique)

---

## Technical Debt (Must Fix Before Major Feature Work)

### HIGH — Fix These First

**1. CalendarSyncWorker requires network (bug)**
`CalendarSyncWorker` sets `NetworkType.CONNECTED` as a constraint — but calendar sync reads the device's local `ContentProvider`, not the network. This means calendar sync silently skips on airplane mode. Remove the network constraint.

**2. Backup restore creates duplicate flights (data integrity)**
`DriveBackupService.restore()` calls `insertAllForRestore()` which unconditionally inserts all flights from backup. There is no deduplication check against existing rows (no unique constraint other than `id`, which is reset to 0). Restoring twice will create duplicate entries. Need a `sourceCalendarEventId`-based or content-hash deduplication guard.

**3. Monthly stats use UTC epoch for month bucketing (timezone bug)**
`LogbookFlightDao.getMonthlyFlightCounts()` uses `strftime('%Y-%m', departureTimeMillis / 1000, 'unixepoch')` — this computes the month in UTC. A flight departing at 23:00 local time in UTC+9 (Japan) will be bucketed to the previous calendar month. Should use `departureDateEpochDay` converted to year-month instead.

**4. Top airlines extracted by `substr(flightNumber, 1, 2)` (brittle)**
This assumes all flight numbers start with a 2-character IATA code. ICAO codes are 3 characters (e.g. "AAL" for American). Single-char airline codes (e.g. "B6" JetBlue = correct, but "Q" codes) break this. Should use a proper airline prefix lookup table.

**5. `distanceKm` field stores nautical miles in some paths (naming inconsistency)**
`DriveBackupService.restore()` maps `exportFlight.distanceNm` → `distanceKm`. The export field is named `distanceNm` (correct units) but the entity column is `distanceKm`. The actual value stored depends on which path inserted the flight — some paths use km (from `AirportCoordinatesMap.greatCircleKm`), others may store nm. Unit inconsistency will corrupt distance statistics. Needs audit and migration.

### MEDIUM — Fix in Next Sprint

**6. No pagination in `LogbookFlightDao.getAll()`**
Loading all flights into memory as a single `Flow<List<LogbookFlight>>` will cause OOM for users with 1,000+ flights. Should use `PagingSource` / Jetpack Paging 3.

**7. Drive backup single-file — no versioning or backup history**
Only one file (`flight-log-backup.json`) is kept. A corrupted backup overwrites the only copy. Should keep last N backups with timestamps.

**8. No retry or offline queue for FlightAware API calls**
`FlightTrackingWorker` fails hard when FlightAware returns an error. No exponential backoff beyond WorkManager default. No offline queue for status updates requested while offline.

---

## Phase 3 Roadmap — Prioritized

### Priority 0 — Technical Debt Sprint (Prerequisite for All)

Fix items 1–5 from the debt list above before shipping new features. These are correctness bugs, not polish.

**Deliverables:**
- Remove network constraint from `CalendarSyncWorker`
- Add duplicate-guard on restore (hash or composite key check)
- Fix monthly stats timezone bucketing (use `departureDateEpochDay`)
- Replace `substr` airline extraction with IATA prefix lookup
- Audit `distanceKm` vs `distanceNm` field and add DB migration to normalize

**Edge Cases to Test:**
- Calendar sync runs on airplane mode — flights should still import
- Restore on a device that already has all flights — 0 imported, 0 skipped (not 0 imported, N skipped counting every flight as a "skip-but-not-duplicate")
- Flight at 23:30 local UTC+12 — must appear in correct calendar month in stats
- Flight number "Q400" (Horizon Air) — airline extraction should return "Q4" or unknown, not crash
- `distanceKm` field after migration: existing rows with nm values must be converted

**DB migration:** v4 → v5

---

### Priority 1 — Pilot Logbook Mode (Biggest Differentiator)

**Why first:** This is the feature no Android competitor offers. It converts the app from a hobby tracker into a professional tool pilots pay for. It also defines the monetization boundary (free = passenger, premium = pilot logbook).

#### Feature: Dual App Mode (Passenger / Pilot)

- Settings toggle: "App Mode — Passenger Tracker / Pilot Logbook"
- Passenger mode: current behavior unchanged
- Pilot mode: unlocks all pilot fields, hides passenger fields (seatClass, seatNumber), changes UI labels

#### Feature: Pilot Logbook Fields

**Data model additions to `LogbookFlight` (all nullable, non-breaking migration):**

```kotlin
// Pilot role
val pilotRole: String? = null          // "PIC", "SIC", "DUAL", "SOLO", "OBSERVER"

// Time columns (minutes, to match existing durationMinutes)
val picTimeMinutes: Int? = null        // Pilot-in-command time
val sicTimeMinutes: Int? = null        // Second-in-command time
val dualTimeMinutes: Int? = null       // Dual received (student)
val soloTimeMinutes: Int? = null       // Solo time
val nightTimeMinutes: Int? = null      // Night time (civil twilight definition)
val actualImcMinutes: Int? = null      // Actual instrument conditions
val simulatedImcMinutes: Int? = null   // Under foggles/hood

// Landings
val dayLandings: Int? = null
val nightLandings: Int? = null
val fullStopLandings: Int? = null      // Required for currency (vs touch-and-go)

// Instrument currency
val instrumentApproaches: Int? = null
val approachTypes: String? = null      // JSON: ["ILS","VOR","RNAV"] — stored as text

// Cross-country
val isCrossCountry: Boolean = false    // > 50nm straight-line in FAA definition

// Aircraft
val aircraftRegistration: String? = null  // N-number, G-XXXX, JA-XXXX, etc.
val engineCategory: String? = null    // "SEL", "MEL", "SES", "MES", "Turbine", "Jet"
val isSimulator: Boolean = false       // AATD/BATD/FTD/FFS entries
val simulatorType: String? = null      // "AATD", "BATD", "FTD Level D", etc.

// Crew
val crewNames: String? = null          // Comma-separated, stored as text
val passengerCount: Int? = null
```

**DB migration:** v5 → v6

#### Feature: Pilot Currency Tracker

- 90-day currency: counts day/night takeoffs and landings in last 90 days
- IFR currency: 6 instrument approaches + holds in last 6 calendar months
- Night currency: 3 night takeoffs + 3 night full-stop landings in last 90 days
- Visual dashboard card: green/yellow/red currency status per category
- Alert notification when currency expires in < 30 days

#### Feature: Pilot Logbook Totals View

- Running totals table (like a physical logbook's "Totals Brought Forward"): PIC, SIC, dual, night, IMC, simulated, XC, day landings, night landings, approaches
- Filterable by aircraft type, aircraft registration, date range

#### Feature: Regulatory Export Formats

- FAA ATP logbook CSV (compatible with LogTen Pro import)
- EASA FCL.050 format PDF
- ICAO Annex 1 standard fields

**Edge Cases to Test:**
- Night time entered > total flight duration — validation error with clear message
- PIC + SIC + dual + solo time > total time — warn user (valid in some scenarios, e.g. formation flight)
- Currency check across DST boundary: 90-day window ending on day clocks change
- Simulator entry: durationMinutes valid but distanceKm must be 0 — enforce
- `isCrossCountry = true` but `distanceKm` < 80 (50nm) — warn but allow (pilot override)
- IFR currency: 6 approaches spanning exactly 6 calendar months boundary (e.g. Oct 1 – Mar 31 vs Sep 30 – Mar 30)
- Flight with `isSimulator = true` — must not appear in map view or affect distance stats
- Aircraft registration with international characters (HS-TGH Thai Airways) — UTF-8 handling
- `approachTypes` JSON parse failure on DB read — graceful null fallback
- Crew names field with comma in name (e.g. "Smith, John") — must survive CSV export quoting

---

### Priority 2 — Profile Page + Social Sharing

Addresses pending Task #1. Differentiates from dry logbook competitors.

#### Feature: Pilot Profile Page

- Sections: total hours (by category in pilot mode), certificates held (PPL/CPL/IR/ATP — user-entered), ratings (SEL/MEL/SES), medical expiry date, bio/callsign
- Stored in `DataStore` (user preferences, not Room — not flight data)
- Stats summary: total flights, total hours, airports visited, countries visited, longest flight
- Aircraft collection: gallery of every tail number flown, grouped by type

#### Feature: Flight Share Card

- Generate a shareable image (Bitmap via Compose Canvas): route arc on dark map, departure→arrival, date, aircraft, duration
- Share via Android share sheet (Intent)
- Optional: "Year in Review" card — top stats of the year as a shareable graphic

#### Feature: Medical Expiry Reminder

- User enters medical class and expiry date in profile
- App shows banner when < 60 days to expiry
- Notification reminder at 90 days and 30 days

**Edge Cases to Test:**
- Share card with an unknown airport code (no coordinates) — fall back to text-only card, no crash
- Medical expiry set in the past — immediately show expired banner, no silent staleness
- Profile with 0 flights — empty states with clear onboarding CTAs (not blank screens)
- Share card for a simulator entry (`isSimulator = true`) — suppress map arc, show "SIM" badge

---

### Priority 3 — Advanced Analytics

Extends the existing Statistics screen.

#### Feature: Date Range Filtering

- Filter all stats by: Last 30 days / 90 days / 365 days / Custom range / All time
- Affects all stat cards, charts, top routes/airports/airlines
- Persist selection across navigation in ViewModel state

#### Feature: Country/Continent Heatmap

- World map with countries colored by flight count (light → dark gradient)
- Tap a country to see flights to/from airports in that country
- Requires country lookup from Airport entity (already has `country` field)

#### Feature: Year-over-Year Comparison

- Bar chart: current year vs prior year monthly flight counts side-by-side
- Highlight months where flying increased/decreased significantly

#### Feature: Cost Tracking (Optional per Flight)

- New optional fields on `LogbookFlight`: `costAmount: Double?`, `costCurrency: String?`
- Per-flight cost entry in Add/Edit screen
- Stats: monthly spend chart, cost per nautical mile, cost per hour
- No currency conversion — display with ISO currency label

#### Feature: Personal Records Timeline

- First flight ever, first international flight, first business class, first new aircraft type
- Rendered as a vertical timeline in Statistics screen

**Edge Cases to Test:**
- Date range filter crossing year boundary (Dec → Jan) in monthly chart
- Country heatmap: flights to Puerto Rico (US territory vs country — use airport's country field, not derived)
- Cost field: mixed currencies (USD + EUR) in same stats view — group by currency, no blending
- Year-over-year chart with < 1 year of data — show only available months, no phantom prior-year bars
- Personal records: user deletes the flight that held a record — must recompute, not persist stale record

---

### Priority 4 — Loyalty Program Integration

**Why:** Airline loyalty tracking (miles/points balance) is a feature myFlightradar24 lacks. It creates daily engagement beyond just logging flights.

#### Feature: Loyalty Program Cards

- User manually adds their frequent flyer programs: airline, member number, tier, points/miles balance (all user-entered — no API access to actual balances)
- Displayed as card stack on Profile page
- Optional: log miles earned per flight (based on distance × RDM multiplier, user-configurable)

#### Feature: Mileage Estimator

- On flight add/edit: estimate redeemable miles based on distance, fare class, selected loyalty program's earning rate
- User can override with actual miles earned
- Running lifetime miles total per program

**Edge Cases to Test:**
- Mileage estimate for a simulator entry — must return 0 (no distance)
- Loyalty program deleted while flights still reference it — cascade to null, not delete flights
- Miles balance entry of 0 — valid (user just joined)
- Fare class not selected — show estimated range (min–max) instead of exact figure

---

### Priority 5 — Platform Expansion + Monetization

#### Monetization Model

**Free tier:**
- Passenger tracking (all current features)
- Up to 500 flights in logbook
- 1 Drive backup slot
- CSV/JSON export
- Achievements and statistics

**Premium (one-time purchase, ~$9.99):**
- Pilot logbook mode (Phase 3, Priority 1) — this is the revenue hook
- Unlimited flights
- 10 backup slots with version history
- PDF logbook export (FAA/EASA format)
- Priority currency tracking alerts
- Loyalty mileage tracker (Priority 4)

Implementation: Google Play Billing Library 6, `BillingRepository` with Room-persisted entitlement, verified server-side via Play Developer API (optional, adds backend requirement).

**Edge Cases to Test:**
- Purchase on device A, restore on device B — `queryPurchasesAsync` on launch must re-verify
- User exceeds 500-flight free limit — graceful paywall, not silent data loss (flights still visible, add blocked)
- Purchase cancelled mid-flow — no partial unlock
- Free trial expiry — in-app message, not silent feature disable

#### Wear OS Companion

- Wear OS tile: next upcoming flight (route, departure time countdown)
- Complication: flights this month count
- Triggers live tracking check if flight is active

#### Offline-First Hardening

- Full audit of all network calls: annotate each as "required" vs "optional/enhancement"
- Required calls (FlightAware tracking) must queue offline and retry on reconnect via WorkManager
- Optional calls (route enrichment, aircraft type lookup) must degrade gracefully with cached data
- Add a connectivity-aware banner ("Tracking unavailable offline") — not a blocking error

---

## Summary Roadmap Table

| Priority | Phase | Feature Set | DB Migration | Scope |
|---|---|---|---|---|
| P0 | 3.0 — Tech Debt | Bug fixes: calendar sync, restore dedup, timezone stats, distance units | v4→v5 | Small |
| P1 | 3.1 — Pilot Logbook | Dual mode, pilot fields, currency tracker, regulatory export | v5→v6 | Large |
| P2 | 3.2 — Profile + Social | Profile page, share cards, medical alerts, aircraft collection | DataStore only | Medium |
| P3 | 3.3 — Analytics | Date range filter, country heatmap, YoY comparison, cost tracking | v6→v7 | Medium |
| P4 | 3.4 — Loyalty | Frequent flyer cards, mileage estimator | v7→v8 | Small |
| P5 | 3.5 — Platform | Monetization (Play Billing), Wear OS, offline hardening | N/A | Large |

---

## Immediate Next Actions

The following tasks are already queued (Tasks 1–5). After those complete:

1. **P0 tech debt sprint** — fix the 5 bugs listed above before new features land on a broken foundation
2. **Pilot logbook schema design** — design the full v5→v6 migration spec, all new fields nullable, before any UI work begins
3. **App mode toggle architecture** — single `DataStore` preference key `app_mode: "passenger" | "pilot"`, flows into all ViewModels via a `AppModeRepository`
4. **Currency engine as pure Kotlin module** — no Android dependencies, fully unit-testable before UI is built

---

*Roadmap v2 authored by Planner agent, 2026-04-02.*
