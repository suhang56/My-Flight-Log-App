package com.flightlog.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.flightlog.app.MainActivity
import com.flightlog.app.data.preferences.OnboardingPreferences
import com.flightlog.app.ui.theme.FlightLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private var launched = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Mark onboarding complete regardless of permission result
        OnboardingPreferences.markComplete(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Disable back navigation — user must tap "Get Started"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* consumed */ }
        })

        setContent {
            FlightLogTheme {
                OnboardingScreen(
                    onGetStarted = {
                        // Guard against double-tap
                        if (!launched) {
                            launched = true
                            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    }
                )
            }
        }
    }
}
