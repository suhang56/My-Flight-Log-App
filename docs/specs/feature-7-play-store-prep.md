# Feature Spec: Feature 7 — Play Store Beta Prep

**Date:** 2026-03-27
**Author:** Planner
**Status:** Ready for Developer

---

## Overview

This is a quality gate, not a feature. The goal is to produce a correctly-built, signed release APK suitable for Play Store internal testing, with all known pre-release bugs fixed. No new user-visible functionality is added.

**The single most critical issue:** `LogbookFlightExport` and `AviationStackApi`'s model classes all use Moshi's reflection adapter (`KotlinJsonAdapterFactory`) but lack `@JsonClass(generateAdapter = true)`. The build has `isMinifyEnabled = true` for release. Under R8, reflection on Kotlin data classes is broken by default — the JSON export feature will silently produce `{}` or crash at runtime in any signed release APK. This must be fixed before any external testing.

---

## Current State (from reading the build files)

| Item | Current state | Problem |
|---|---|---|
| `moshi-kotlin-codegen` KSP processor | Not in `libs.versions.toml` or `build.gradle.kts` | All Moshi classes use slow reflection adapter, breaks under R8 |
| `@JsonClass(generateAdapter = true)` | Absent from all 5 Moshi data classes | Codegen adapters not generated |
| `KotlinJsonAdapterFactory` in `NetworkModule` | Present, registered with `.addLast()` | Needed as fallback until all classes annotated; stays |
| `proguard-rules.pro` | File does not exist | No custom keep rules; R8 uses only AOSP defaults |
| `signingConfigs` in `build.gradle.kts` | Absent | Release build cannot be signed without it |
| `versionCode` | `1` | Fine for first beta |
| `versionName` | `"1.0.0"` | Fine for first beta |
| `AVIATION_STACK_KEY` in `gradle.properties` | Not present (read via `findProperty`, returns `""`) | API calls will return 401 in release if key not set |
| HTTP cleartext for `api.aviationstack.com` | Allowed via `network_security_config.xml` | AviationStack free tier uses HTTP — required; documented |
| `applicationId` | `com.flightlog.app` | Must be unique on Play Store — verify not already taken |

---

## Item 1: Add `moshi-kotlin-codegen` KSP Processor

### Root cause
`moshi-kotlin` (reflection) is present; `moshi-kotlin-codegen` (KSP) is not. The codegen processor generates type-safe adapters at compile time. Without it, `@JsonClass(generateAdapter = true)` has no effect and Moshi falls back to reflection, which R8 strips.

### Change 1a: `gradle/libs.versions.toml`

Add to `[libraries]`:
```toml
moshi-kotlin-codegen = { group = "com.squareup.moshi", name = "moshi-kotlin-codegen", version.ref = "moshi" }
```

The `moshi` version is already `"1.15.1"` — codegen uses the same version, no new `[versions]` entry needed.

### Change 1b: `app/build.gradle.kts`

Add under the existing Moshi implementation lines:
```kotlin
ksp(libs.moshi.kotlin.codegen)
```

KSP is already configured in the project (used by Room and Hilt), so no plugin change is needed.

### Change 1c: Annotate all Moshi data classes

Five files need `@JsonClass(generateAdapter = true)` added:

**`data/export/LogbookFlightExport.kt`** — both classes:
```kotlin
@JsonClass(generateAdapter = true)
data class LogbookFlightExport(...)

@JsonClass(generateAdapter = true)
data class LogbookFlightExportWrapper(...)
```

**`data/network/AviationStackApi.kt`** — three classes:
```kotlin
@JsonClass(generateAdapter = true)
data class AviationStackResponse(...)

@JsonClass(generateAdapter = true)
data class AviationStackFlight(...)

@JsonClass(generateAdapter = true)
data class AviationStackEndpoint(...)
```

### Change 1d: Remove `KotlinJsonAdapterFactory` from `NetworkModule`

Once all Moshi data classes have `@JsonClass(generateAdapter = true)`, the reflection adapter is no longer needed. Remove:
```kotlin
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
// and
.addLast(KotlinJsonAdapterFactory())
```

Also remove `moshi-kotlin` from `build.gradle.kts` dependencies and `libs.versions.toml`, since it is only needed for the reflection adapter:
```kotlin
// Remove:
implementation(libs.moshi.kotlin)
```

**Important:** Only remove `moshi-kotlin` AFTER all classes are annotated and the build succeeds. If any unannotated Moshi class is added in a future feature without `@JsonClass`, it will fail silently. Keeping `KotlinJsonAdapterFactory` as a registered last-resort fallback is also an acceptable approach — the spec recommends full removal to keep the build clean, but either is correct.

---

## Item 2: Create `app/proguard-rules.pro`

The file does not exist. Create it with rules for all libraries that need them.

**Full content of `app/proguard-rules.pro`:**

```proguard
# ── Moshi ──────────────────────────────────────────────────────────────────────
# Keep generated JsonAdapter classes (named <ClassName>JsonAdapter)
-keep class **JsonAdapter { *; }
-keepnames class * { @com.squareup.moshi.Json *; }
-keepnames class * { @com.squareup.moshi.JsonClass *; }

# If KotlinJsonAdapterFactory is kept (fallback), keep Kotlin metadata
# Remove this block if KotlinJsonAdapterFactory is removed (Item 1d)
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}

# ── Retrofit ───────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp ─────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Room ───────────────────────────────────────────────────────────────────────
# Room generates code at compile time via KSP — no runtime keep rules needed.
# Entities are kept via @Entity annotation processing.

# ── Hilt / Dagger ──────────────────────────────────────────────────────────────
# Hilt generates code at compile time. No additional keep rules needed
# beyond what hilt-android's consumer ProGuard rules provide.

# ── WorkManager ────────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ── General ────────────────────────────────────────────────────────────────────
# Keep line number info for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

**Note on Kotlin metadata:** The `-keep class kotlin.Metadata` rule ensures R8 preserves Kotlin reflection metadata for classes that genuinely need it (e.g. `SavedStateHandle` key access). With `@JsonClass(generateAdapter = true)` on all Moshi classes, Kotlin metadata is not needed for Moshi serialization.

---

## Item 3: Cleartext HTTP Configuration

**Current state:** `network_security_config.xml` permits HTTP cleartext traffic specifically to `api.aviationstack.com`. This is correctly scoped — only that domain is exempted, not all traffic.

**Assessment:** This configuration is intentional and correct for the AviationStack free tier, which uses HTTP. Play Store does not reject apps for having scoped cleartext configs; it only warns about `android:usesCleartextTraffic="true"` on the `<application>` element (which we do not have).

**Action required:** None. Document the decision in a comment in the XML file for future maintainers.

**Change:** Add a comment to `network_security_config.xml`:
```xml
<!-- AviationStack free tier API (http://api.aviationstack.com) uses HTTP.
     Cleartext is explicitly permitted for this domain only.
     If upgrading to a paid AviationStack plan (which uses HTTPS), remove this block. -->
```

---

## Item 4: Version Code and Name

**Current:** `versionCode = 1`, `versionName = "1.0.0"`

**For internal testing beta:** No change needed. `versionCode = 1` is correct for the first ever upload. `versionName = "1.0.0"` is shown to users in the Play Store listing and is appropriate for a beta.

**Recommended naming convention for future releases:**
- `versionCode`: increment by 1 for every upload (Play Store requires strict monotonic increase)
- `versionName`: semantic versioning (`MAJOR.MINOR.PATCH`) — `"1.0.1"` for bug fixes, `"1.1.0"` for new features

**No code change needed for this item.** Document the convention.

---

## Item 5: Release Signing Configuration

The `build.gradle.kts` has no `signingConfigs` block. A Play Store upload requires a signed APK or AAB. This is the only item that requires user action (creating a keystore) that cannot be automated.

### Step 5a: Create a keystore

The user must run this command once (outside the project):
```
keytool -genkeypair -v -keystore flight-log-release.jks \
  -alias flight-log \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Store the resulting `.jks` file in a safe location **outside** the project repository. Never commit it to Git.

### Step 5b: Store signing credentials in `~/.gradle/gradle.properties` (user-level, never committed)

```properties
FLIGHT_LOG_STORE_FILE=/path/to/flight-log-release.jks
FLIGHT_LOG_STORE_PASSWORD=yourStorePassword
FLIGHT_LOG_KEY_ALIAS=flight-log
FLIGHT_LOG_KEY_PASSWORD=yourKeyPassword
```

### Step 5c: Add `signingConfigs` to `app/build.gradle.kts`

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(project.findProperty("FLIGHT_LOG_STORE_FILE") as String? ?: "")
        storePassword = project.findProperty("FLIGHT_LOG_STORE_PASSWORD") as String? ?: ""
        keyAlias = project.findProperty("FLIGHT_LOG_KEY_ALIAS") as String? ?: ""
        keyPassword = project.findProperty("FLIGHT_LOG_KEY_PASSWORD") as String? ?: ""
    }
}

buildTypes {
    release {
        isMinifyEnabled = true
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

This pattern reads credentials from `gradle.properties` at the user level — credentials never enter the project files or version control.

### Step 5d: Add `.jks` to `.gitignore`

Ensure `*.jks` and `*.keystore` are in the project's `.gitignore`. Verify:
```
*.jks
*.keystore
```

---

## Item 6: API Key for Release Builds

**Current state:** `AVIATION_STACK_KEY` is read from `project.findProperty(...)` which returns `""` if not set. An empty key produces 401 errors from AviationStack — the flight search feature silently fails.

**For the beta:** The key must be present in the build environment.

**Local developer builds:** Add to `~/.gradle/gradle.properties`:
```properties
AVIATION_STACK_KEY=your_actual_key_here
```

**CI/CD (if used later):** Inject as an environment variable or secret.

**No code change needed.** The `buildConfigField` mechanism is already correct. Document that the key must be set before building a release.

---

## Item 7: Build Output Format — AAB vs APK

**Recommendation:** Upload an Android App Bundle (`.aab`) to the Play Store, not a `.apk`. The Play Store has required AAB for new apps since August 2021.

To build a release AAB:
```
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

No `build.gradle.kts` changes needed — AAB is built by the `bundle*` tasks by default.

---

## Item 8: `applicationId` Uniqueness Check

The current `applicationId = "com.flightlog.app"` is generic and may already be registered on the Play Store by another developer. Before creating the Play Store listing, search the Store for this package name. If taken, change it now — changing `applicationId` after publishing is impossible without creating a new app entry.

**Recommended alternative if taken:** `com.suhang56.myflightlog` or similar (developer-specific prefix).

**This is a user decision, not a code decision.** No spec can guarantee uniqueness — the developer must verify on the Play Console.

---

## Item 9: `@Suppress` Cleanup — `HttpLoggingInterceptor` in Release

**Current state:** `NetworkModule` sets `HttpLoggingInterceptor.Level.BODY` in debug and `Level.NONE` in release. This is already correctly gated — no sensitive data leaks in release builds. No change needed.

---

## Implementation Steps (ordered for a single developer)

1. **Add `moshi-kotlin-codegen` to `libs.versions.toml` and `build.gradle.kts`** — sync Gradle
2. **Add `@JsonClass(generateAdapter = true)` to all 5 Moshi data classes** — build must succeed with generated adapters
3. **Remove `KotlinJsonAdapterFactory` and `moshi-kotlin`** — verify build still succeeds and JSON export still works in a debug build
4. **Create `app/proguard-rules.pro`** with the content from Item 2
5. **Add signing comment to `network_security_config.xml`** (Item 3)
6. **Add `signingConfigs` block to `build.gradle.kts`** (Item 5c)
7. **Add `*.jks` to `.gitignore`** (Item 5d)
8. **Create release keystore** (user action, Item 5a/5b)
9. **Set `AVIATION_STACK_KEY` in user-level `gradle.properties`** (Item 6)
10. **Build release AAB:** `./gradlew bundleRelease`
11. **Verify release AAB manually:** install on a physical device via `adb` or internal test track; test JSON export, flight search, calendar sync
12. **Create Play Store listing** (non-engineering: screenshots, description, privacy policy URL, content rating)
13. **Upload AAB to Play Console internal testing track**

---

## Edge Cases to Test (Release Build Specific)

### E1: JSON export in release build produces valid JSON
- Build a signed release APK
- Export logbook as JSON
- Open the file and verify it parses correctly (not `{}` or `null` fields where values are expected)
- This is the regression test for the `@JsonClass` fix

### E2: Flight search (AviationStack) works in release build
- With a valid `AVIATION_STACK_KEY` set, search for a real flight number in release build
- Expected: route auto-fills correctly; no 401 or parse error

### E3: Calendar sync works in release build
- WorkManager and Hilt wiring not broken by R8
- Expected: CalendarSyncWorker runs and populates the calendar flights screen

### E4: ProGuard keep rule for WorkManager
- `CalendarSyncWorker` extends `CoroutineWorker` (which extends `ListenableWorker`)
- Expected: `CalendarSyncWorker` is not stripped by R8; the keep rule in `proguard-rules.pro` covers it

### E5: Room entities and DAOs not stripped
- Room generates code at KSP time; entities are referenced by generated code
- Expected: all DAO queries function correctly in release build; no `ClassNotFoundException`

### E6: Hilt injection not broken by R8
- Hilt's consumer ProGuard rules are included via the `hilt-android` AAR
- Expected: all `@HiltViewModel` and `@Inject` constructors survive minification

### E7: Navigation deep links not broken
- All `Routes.*` string constants are compile-time values; not affected by R8
- Expected: all screen navigation works correctly in release build

### E8: `AVIATION_STACK_KEY` not in build output
- Verify `BuildConfig.AVIATION_STACK_KEY` is obfuscated/not readable as a plain string in the release APK
- Note: `buildConfigField` embeds the key as a string literal — it CAN be found via APK decompilation. This is a known limitation of the current approach. For production, consider server-side proxying. For the beta, document this risk.

### E9: App does not crash on first launch (fresh install, release build)
- No existing database, no calendar permission granted yet
- Expected: Calendar screen shows permission prompt; Logbook shows empty state; Statistics shows empty state; no crash

### E10: versionCode is accepted by Play Console
- `versionCode = 1` is valid for the first upload
- Expected: Play Console accepts the AAB; no "version code already used" error

---

## Dependencies

| Item | Change type | Files touched |
|---|---|---|
| `moshi-kotlin-codegen` KSP | New dependency | `libs.versions.toml`, `build.gradle.kts` |
| `@JsonClass` annotations | Code change | `LogbookFlightExport.kt`, `AviationStackApi.kt` |
| Remove `moshi-kotlin` + `KotlinJsonAdapterFactory` | Dependency removal | `build.gradle.kts`, `libs.versions.toml`, `NetworkModule.kt` |
| `proguard-rules.pro` | New file | `app/proguard-rules.pro` |
| `signingConfigs` | Build config | `app/build.gradle.kts` |
| `network_security_config.xml` | Comment only | `app/src/main/res/xml/network_security_config.xml` |
| `.gitignore` | Add `*.jks` | `.gitignore` (root or app-level) |
| Keystore creation | User action | Outside project |
| `gradle.properties` (user-level) | User action | `~/.gradle/gradle.properties` |
| Play Store listing | User action | Play Console |

---

## Risks

1. **`applicationId` already taken on Play Store.** `com.flightlog.app` is generic. Check Play Console before creating the listing. Changing `applicationId` post-publish is impossible without losing all installs and reviews. Verify before doing any other Play Store work.

2. **Keystore loss = app update impossible.** If the release keystore is lost, updates to the published app cannot be signed — a new app entry must be created. Back up `flight-log-release.jks` to at least two separate locations (e.g. password manager + encrypted cloud storage).

3. **`AVIATION_STACK_KEY` in APK.** The API key is embedded as a `BuildConfig` string literal. It is technically extractable from a decompiled APK. For a beta with trusted testers this is acceptable. For public release, consider a server-side proxy that holds the key — the app calls our server, which calls AviationStack.

4. **R8 and Hilt interaction.** Hilt includes its own consumer ProGuard rules in the AAR, but Hilt + KSP can occasionally have edge cases under aggressive R8 optimizations. Run E3/E5/E6 on a physical device (not just emulator) with a release build to catch any issues before Play Store submission.

5. **Privacy Policy requirement.** Play Store requires a privacy policy URL for any app with INTERNET permission. This must be a publicly accessible URL. The policy must disclose that the app accesses calendar data and makes network requests. This is a user/legal task, not an engineering task, but it will block submission if missing.
