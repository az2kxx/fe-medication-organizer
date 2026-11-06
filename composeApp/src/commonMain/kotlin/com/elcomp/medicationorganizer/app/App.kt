package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val modeLabel = when (services.settings) {
        is ApiSettings.Mock -> "Tryb demo (MOCK)"
        is ApiSettings.Live -> "Tryb LIVE"
    }
    val mockScenario = (services.settings as? ApiSettings.Mock)?.scenario

    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("EL COMP", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Cyfrowa kasetka",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (mockScenario != null && mockScenario != MockScenario.NORMAL) {
                    Text(
                        text = "Scenariusz demo ${mockScenario.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (launchConfig.invalidConfiguration) {
                    Text(
                        text = "Nieprawidłowa konfiguracja. Uruchomiono tryb demo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
