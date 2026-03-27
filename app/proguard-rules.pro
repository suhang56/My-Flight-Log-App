# ── Moshi ──────────────────────────────────────────────────────────────────────
# Keep generated JsonAdapter classes (named <ClassName>JsonAdapter)
-keep class **JsonAdapter { *; }
-keepnames class * { @com.squareup.moshi.Json *; }
-keepnames class * { @com.squareup.moshi.JsonClass *; }

# Keep @Json-annotated fields for correct serialization
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

# ── Glance (Home Screen Widget) ───────────────────────────────────────────
-keep class androidx.glance.** { *; }
