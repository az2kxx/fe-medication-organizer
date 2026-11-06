package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario

data class AppLaunchConfig(
    val settings: ApiSettings = ApiSettings.Mock(),
    val invalidConfiguration: Boolean = false,
)

fun resolveAppLaunchConfig(
    mode: String?,
    baseUrl: String?,
    mockScenario: String?,
): AppLaunchConfig = try {
    val settings = when (mode?.trim()?.uppercase()) {
        null -> {
            require(baseUrl == null && mockScenario == null)
            ApiSettings.Mock()
        }
        "MOCK" -> {
            require(baseUrl == null)
            val scenario = mockScenario?.trim()?.uppercase()?.let(MockScenario::valueOf)
                ?: MockScenario.NORMAL
            ApiSettings.Mock(scenario)
        }
        "LIVE" -> {
            require(mockScenario == null)
            ApiSettings.Live(requireNotNull(baseUrl).trim())
        }
        else -> throw IllegalArgumentException("Unsupported API mode")
    }
    AppLaunchConfig(settings)
} catch (_: IllegalArgumentException) {
    AppLaunchConfig(invalidConfiguration = true)
}

@Composable
fun App(launchConfig: AppLaunchConfig = AppLaunchConfig()) {
    val services = remember(launchConfig.settings) { AppServices.create(launchConfig.settings) }
    DisposableEffect(services) {
        onDispose(services::close)
    }
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        AppNavigation(settings = services.settings, invalidConfiguration = launchConfig.invalidConfiguration)
    }
}
