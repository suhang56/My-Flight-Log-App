package com.flightlog.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `isComplete returns false when no prior SharedPreferences exist`() {
        assertFalse(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `markComplete sets flag to true`() {
        OnboardingPreferences.markComplete(context)
        assertTrue(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `isComplete returns true after markComplete is called`() {
        assertFalse(OnboardingPreferences.isComplete(context))
        OnboardingPreferences.markComplete(context)
        assertTrue(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `markComplete is idempotent - calling twice does not break state`() {
        OnboardingPreferences.markComplete(context)
        OnboardingPreferences.markComplete(context)
        assertTrue(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `isComplete survives context recreation`() {
        OnboardingPreferences.markComplete(context)
        // Simulate reading with a fresh context reference (same app process)
        val freshContext: Context = ApplicationProvider.getApplicationContext()
        assertTrue(OnboardingPreferences.isComplete(freshContext))
    }

    @Test
    fun `onboarding prefs are isolated from other SharedPreferences`() {
        // Write to a different prefs file
        context.getSharedPreferences("other_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("complete", true).commit()
        // Onboarding should still be incomplete
        assertFalse(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `isComplete returns false if prefs file exists but key is missing`() {
        // Create the prefs file with a different key
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            .edit().putString("other_key", "value").commit()
        assertFalse(OnboardingPreferences.isComplete(context))
    }

    @Test
    fun `isComplete returns false if key was explicitly set to false`() {
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("complete", false).commit()
        assertFalse(OnboardingPreferences.isComplete(context))
    }
}
