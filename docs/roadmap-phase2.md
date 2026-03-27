# Phase 2 Roadmap — My Flight Log App

**Date:** 2026-03-27
**Status:** Beta-complete (9 features, 341 tests, Play Store prep done)
**Goal:** Build a Flighty competitor on Android — better, free, Android-first

---

## Current Baseline (Phase 1 Complete)

| # | Feature | Status |
|---|---|---|
| 1 | Calendar Sync | Shipped |
| 2 | Timezone Support | Shipped |
| 3 | Flight Logbook (CRUD, search, filter, sort) | Shipped |
| 4 | Statistics Dashboard (12+ metrics, charts) | Shipped |
| 5 | Manual Flight Search (AviationStack auto-fill) | Shipped |
| 6 | Data Export (CSV + JSON) | Shipped |
| 7 | Flight Detail Screen (timeline, share) | Shipped |
| 8 | Route Map Canvas (great-circle arc) | Shipped |
| 9 | Home Screen Widget (Glance API) | Shipped |
| — | Play Store Prep (Moshi codegen, ProGuard, signing) | Shipped |

**P1 blockers for Play Store upload (user action required):** keystore file + privacy policy URL.

---

## Phase 2 Feature Roadmap

### Release Target: v1.0 Public Launch

These features, combined with Play Store upload, constitute a compelling v1.0 that can compete with Flighty's core offering.

---

### Feature 10 — Offline Airport Database (MEDIUM)

**Why first:** This is foundational infrastructure. Features 11 (live tracking), 12 (achievements), and 14 (trip grouping) all benefit from reliable airport names, IATA codes, country/city data, and coordinates for 10,000+ airports. Currently we have ~200 airports hardcoded in `AirportCoordinatesMap` and `AirportNameMap`. Expanding to a bundled SQLite/Room airport table eliminates lookup gaps and enables city/country-level statistics.

**Scope:** Medium
- Bundle a prepopulated airport database (OurAirports CSV → Room prepopulated DB asset, ~3MB)
- Replace `AirportCoordinatesMap`, `AirportNameMap`, `AirportTimezoneMap` with DAO queries
- New `Airport` entity: iata, icao, name, city, country, lat, lon, timezone
- Migration: Room DB version 7 → 8 (or 6 → 7 if achievements not yet merged)
- No new API dependencies — data is bundled

**Dependencies:** None (standalone improvement)
**Parallelizable with:** Feature 12 (Achievements) — fully independent
**Quick win / Big lift:** Medium lift, high leverage for all downstream features

---

### Feature 11 — Live Flight Tracking + Push Notifications (LARGE)

**Why:** This is the unanimous #1 feature. It transforms the app from a logbook into a live flight companion — the defining difference between a log app and a Flighty competitor. AviationStack is already integrated for search; extending it for live polling is a natural step.

**Scope:** Large
- "Track This Flight" button on Flight Detail screen (calendar + logbook flights)
- Live status screen: flight status badge (Scheduled / Boarding / Departed / In Air / Landed / Cancelled / Diverted), actual vs scheduled departure/arrival, delay duration, gate info
- Background polling via WorkManager (poll every 10 min when flight is active, stop after landing)
- Push notifications via Android NotificationManager (no Firebase required):
  - "Flight NH847 is now boarding at Gate 22"
  - "Your flight is delayed 45 minutes — new departure 16:20"
  - "Flight landed at NRT — welcome to Tokyo"
- Notification channels: Boarding, Delay, Landing (user can toggle per-channel in system settings)
- AviationStack free tier constraint: 100 req/month — implement request budget manager, warn user when budget is low, skip polling for past flights
- Graceful degradation: if API unavailable or budget exhausted, show last-known status with timestamp

**Dependencies:** Feature 10 (airport DB) recommended but not required — can use existing maps as fallback
**Parallelizable with:** Feature 12 (Achievements) — fully independent
**Quick win / Big lift:** Big lift — but the single highest-impact feature for v1.0

---

### Feature 12 — Achievements / Gamification (MEDIUM)

**Why:** Zero new API dependencies. Pure computation on existing logbook data. Creates retention, shareability, and a strong differentiator — Flighty has nothing like this.

**Scope:** Medium (plan already written by Planner)
- 18 achievements across 4 tiers (Bronze / Silver / Gold / Platinum)
- New `Achievement` Room entity, Room migration
- `AchievementEvaluator.kt` — pure function, fully unit-testable
- UI: TabRow added to Statistics screen ("Stats" tab + "Achievements" tab)
- Nav badge dot on Statistics icon for unseen unlocked achievements
- Unlock shimmer animation on newly seen badges
- Triggered on every logbook add/edit/delete

**Dependencies:** None (builds on existing Room + Stats infrastructure)
**Parallelizable with:** Feature 10, 11 — fully independent
**Quick win / Big lift:** Quick win relative to impact — recommend building this in parallel while Feature 11 is in development

---

### Feature 13 — Onboarding Polish (SMALL)

**Why:** Beta testers' first impression matters for Play Store reviews. Currently the app opens cold to the Calendar tab with no guidance. New users with no flights see empty states and no explanation of what the app does or how to start.

**Scope:** Small
- First-launch welcome screen: app logo, tagline, 3-step pager ("Sync your flights", "Track live status", "See your stats")
- Permission request flow integrated into onboarding (calendar permission request shown in context, not as a bare system dialog)
- Empty state improvements: Calendar tab empty state gets an illustration + "Grant calendar access" CTA; Logbook empty state gets "Add your first flight" CTA
- "What's New" bottom sheet on first launch after update (version-gated, stored in SharedPreferences)
- DataStore (or SharedPreferences) flag: `onboarding_complete`

**Dependencies:** None
**Parallelizable with:** Feature 10, 12 — fully independent
**Quick win / Big lift:** Quick win — small scope, high first-impression impact

---

## Release Target: v1.1

Features that add depth after the initial public launch. Can be developed in the background during v1.0 beta period.

---

### Feature 14 — Trip Grouping / Journey Timeline (MEDIUM)

**Why:** Designer's unique differentiator. No other free Android app groups multi-leg itineraries into a single "trip" view. A SYD→SIN→LHR two-leg journey should appear as one trip card with total duration, layover times, and a combined route arc. This is a polish feature that makes power users' logbooks dramatically more readable.

**Scope:** Medium
- Trip detection algorithm: flights within 24h of each other sharing airports (arrival of leg N = departure of leg N+1) are auto-grouped into a `Trip`
- New `Trip` entity in Room: tripId, name (auto-generated: "SYD → LHR via SIN"), startDate, totalDuration
- `LogbookFlight.tripId` FK (nullable — ungrouped flights remain standalone)
- Logbook screen: expandable trip cards (collapsed = trip summary, expanded = individual legs)
- Timeline view: vertical timeline showing layover gaps between legs
- Manual override: user can rename a trip or break a leg out of a trip
- Combined route map arc for multi-leg trips on Flight Detail

**Dependencies:** Feature 10 (airport DB) strongly recommended for reliable airport matching
**Parallelizable with:** Feature 15 (Cloud Backup) — independent
**Quick win / Big lift:** Medium lift

---

### Feature 15 — Cloud Backup / Multi-Device Sync (LARGE)

**Why:** 4 votes in brainstorm. Users fear losing their logbook. Multi-device is a premium expectation. However this is architecturally the most complex feature — requires a backend or a reliable third-party sync service.

**Scope:** Large
- Recommended approach: Google Drive Backup (no backend required, free for user)
  - Export full logbook JSON to user's Google Drive app folder (not visible to other apps)
  - Manual backup + automatic backup on every logbook change (debounced, 30s delay)
  - Restore from Drive backup on new device / after reinstall
  - Conflict resolution: last-write-wins with timestamp, warn user on conflict
- Alternative: Firebase Firestore (requires Google account, more setup, enables true real-time sync)
- Drive approach avoids backend cost and is simpler to implement + audit

**Dependencies:** Feature 6 (Data Export, already ships JSON) — the export format can be reused as the backup format
**Parallelizable with:** Feature 14 — independent
**Quick win / Big lift:** Big lift — prioritize Drive approach to keep scope manageable

---

## Release Target: v1.2

Long-horizon features. High value but require significant new infrastructure or design investment.

---

### Feature 16 — Departure Reminders (SMALL-MEDIUM)

**Why:** 1 vote but high utility — users want a notification "You have a flight tomorrow at 08:30 from HND". Builds on calendar sync data and WorkManager (already in use).

**Scope:** Small-Medium
- Scheduled WorkManager job: daily scan of upcoming calendar flights, schedule AlarmManager notification for N hours before departure (user-configurable: 12h / 24h / 48h)
- Notification: "Flight NH206 departs tomorrow at 09:15 from HND. Check in now."
- Deep link from notification → Flight Detail screen
- Edge cases: cancelled flights, timezone-correct departure time, flights already departed

**Dependencies:** Feature 11 (Live Tracking) — can optionally chain to live status if tracking is active
**Parallelizable with:** Feature 16 is small enough to bundle with any other feature

---

## Summary Table

| # | Feature | Release | Scope | Depends On | Parallelizable |
|---|---|---|---|---|---|
| 10 | Offline Airport Database | v1.0 | Medium | — | Feature 12, 13 |
| 11 | Live Flight Tracking + Push | v1.0 | Large | 10 (soft) | Feature 12, 13 |
| 12 | Achievements / Gamification | v1.0 | Medium | — | Feature 10, 11, 13 |
| 13 | Onboarding Polish | v1.0 | Small | — | Feature 10, 11, 12 |
| 14 | Trip Grouping / Timeline | v1.1 | Medium | 10 (strong) | Feature 15 |
| 15 | Cloud Backup / Multi-Device Sync | v1.1 | Large | 6 (soft) | Feature 14 |
| 16 | Departure Reminders | v1.2 | Small | 11 (soft) | Any |

---

## Strategic Build Order

### Immediate (before Play Store upload)
- Feature 13 — Onboarding Polish (small, high first-impression value, can be done in 1 session)

### Sprint 1 (v1.0 core)
- Feature 10 — Offline Airport DB (build first — unlocks Features 11 and 14)
- Feature 12 — Achievements (parallel with Feature 10 — zero dependency)

### Sprint 2 (v1.0 wow feature)
- Feature 11 — Live Flight Tracking + Push Notifications (big lift, start after Feature 10)

### Sprint 3 (v1.1 depth)
- Feature 14 — Trip Grouping (after Feature 10)
- Feature 15 — Cloud Backup (parallel with Feature 14)

### Sprint 4 (v1.2 polish)
- Feature 16 — Departure Reminders (small, bundle with any sprint)

---

## Quick Wins vs Big Lifts

**Quick wins (high ROI, low effort):**
- Feature 13: Onboarding (1-2 days, high Play Store review impact)
- Feature 12: Achievements (3-4 days, zero new dependencies, viral/shareable)

**Big lifts (high ROI, high effort — worth it):**
- Feature 11: Live Tracking + Push (week+, but the single feature that defines the app vs competitors)
- Feature 15: Cloud Backup (week+, expected by power users, prevents churn after reinstall)

**Foundation hardening (medium effort, high downstream leverage):**
- Feature 10: Offline Airport DB (3-4 days, unlocks accurate data for all features that follow)

---

## What Makes a Compelling v1.0 vs Later

### v1.0 Public Launch Must-Haves
- Onboarding (Feature 13) — first impression
- Live Flight Tracking + Push Notifications (Feature 11) — the "wow" / competitive differentiator
- Achievements (Feature 12) — retention + shareability
- Offline Airport Database (Feature 10) — eliminates lookup gaps in production data

### v1.1 Depth
- Trip Grouping (Feature 14) — power user feature, elevates logbook from list to journey view
- Cloud Backup (Feature 15) — trust + retention for users with large logbooks

### v1.2 Polish
- Departure Reminders (Feature 16) — daily utility touchpoint

---

## AviationStack Free Tier Notes

The free tier (100 req/month) constrains Feature 11 significantly. Mitigation strategies:

1. **Request budget manager**: Track monthly usage in SharedPreferences, show warning at 80 req, disable polling at 100.
2. **Smart polling**: Only poll flights within 24h of departure. Stop after confirmed landing. Skip historical flights.
3. **User opt-in**: Tracking is opt-in per flight, not automatic — user taps "Track this flight".
4. **Upgrade prompt**: If user exhausts budget, show upgrade path (AviationStack paid tier, or note that free tier resets monthly).
5. **Long-term**: Evaluate OpenSky Network API (free, open-source) as a fallback or replacement for live position data.

---

*Roadmap authored by Planner agent, 2026-03-27. Subject to team review and user approval.*
