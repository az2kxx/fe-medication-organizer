package com.elcomp.medicationorganizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.app.AppLaunchConfig
import com.elcomp.medicationorganizer.app.resolveAppLaunchConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchConfig = if (BuildConfig.DEBUG) {
            resolveAppLaunchConfig(
                mode = intent.stringOption("api_mode"),
                baseUrl = intent.stringOption("api_base_url"),
                mockScenario = intent.stringOption("mock_scenario"),
            )
        } else {
            AppLaunchConfig()
        }
        setContent { App(launchConfig) }
    }
}

private fun Intent.stringOption(name: String): String? =
    if (hasExtra(name)) getStringExtra(name).orEmpty() else null
