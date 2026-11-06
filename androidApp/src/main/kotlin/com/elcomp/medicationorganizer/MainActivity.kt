package com.elcomp.medicationorganizer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.app.AppLaunchConfig
import com.elcomp.medicationorganizer.app.resolveAppLaunchConfig
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.session.AndroidSessionTokenStore
import com.elcomp.medicationorganizer.features.profile.data.AndroidDemoPreferencesStore

class MainActivity : ComponentActivity() {
    private val appServices by viewModels<AppServicesViewModel> {
        viewModelFactory { initializer { AppServicesViewModel() } }
    }

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
        val services = appServices.forSettings(launchConfig.settings, applicationContext)
        setContent { App(services, launchConfig.invalidConfiguration) }
    }
}

private class AppServicesViewModel : ViewModel() {
    private var current: AppServices? = null

    fun forSettings(settings: ApiSettings, applicationContext: Context): AppServices {
        current?.takeIf { it.settings == settings }?.let { return it }
        val services = when (settings) {
            is ApiSettings.Mock -> AppServices.create(
                settings,
                demoPreferencesStore = AndroidDemoPreferencesStore(applicationContext),
            )
            is ApiSettings.Live -> AppServices.create(
                settings,
                sessionStore = AndroidSessionTokenStore(applicationContext),
            )
        }
        current?.close()
        current = services
        return services
    }

    override fun onCleared() {
        current?.close()
        current = null
    }
}

private fun Intent.stringOption(name: String): String? =
    if (hasExtra(name)) getStringExtra(name).orEmpty() else null
