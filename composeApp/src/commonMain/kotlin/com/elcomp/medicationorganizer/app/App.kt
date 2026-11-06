package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.session.SessionState
import com.elcomp.medicationorganizer.core.session.SessionTokenStore
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.features.auth.presentation.AuthFlow

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
fun App(
    launchConfig: AppLaunchConfig = AppLaunchConfig(),
    sessionStore: SessionTokenStore? = null,
) {
    val services = remember(launchConfig.settings, sessionStore) {
        AppServices.create(launchConfig.settings, sessionStore)
    }
    DisposableEffect(services) {
        onDispose(services::close)
    }
    LaunchedEffect(services) {
        services.session.restore()
        services.session.monitorExpiry()
    }

    val session by services.session.state.collectAsState()
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        when (val current = session) {
            SessionState.Restoring -> Surface {
                Box(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingState()
                }
            }
            is SessionState.SignedOut -> AuthFlow(services, current.reason) {
                AppModeStatus(services.settings, launchConfig.invalidConfiguration)
            }
            is SessionState.SignedIn -> key(services, current.generation) {
                AppNavigation(
                    settings = services.settings,
                    invalidConfiguration = launchConfig.invalidConfiguration,
                    onSignOut = services.session::signOut,
                )
            }
        }
    }
}
