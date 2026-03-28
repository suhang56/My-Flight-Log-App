package com.flightlog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.flightlog.app.data.preferences.OnboardingPreferences
import com.flightlog.app.ui.navigation.FlightNavGraph
import com.flightlog.app.ui.onboarding.OnboardingActivity
import com.flightlog.app.ui.theme.FlightLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OnboardingPreferences.isComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            FlightLogTheme {
                val navController = rememberNavController()
                FlightNavGraph(navController = navController)
            }
        }
    }
}
