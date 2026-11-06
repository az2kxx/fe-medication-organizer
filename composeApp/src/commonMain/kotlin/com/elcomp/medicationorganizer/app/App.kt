package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.session.SessionState
import com.elcomp.medicationorganizer.core.session.SessionTokenStore
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.features.auth.presentation.AuthFlow
import com.elcomp.medicationorganizer.features.profile.data.AccessibilityPreferences
import com.elcomp.medicationorganizer.features.profile.data.DemoPreferencesStore
import com.elcomp.medicationorganizer.features.profile.presentation.AccountUiState

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
    demoPreferencesStore: DemoPreferencesStore? = null,
) {
    val services = remember(launchConfig.settings, sessionStore, demoPreferencesStore) {
        AppServices.create(launchConfig.settings, sessionStore, demoPreferencesStore)
    }
    DisposableEffect(services) {
        onDispose(services::close)
    }
    LaunchedEffect(services) {
        services.session.restore()
        services.session.monitorExpiry()
    }

    val session by services.session.state.collectAsState()
    when (val current = session) {
        SessionState.Restoring -> AppTheme {
            Surface {
                Box(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingState()
                }
            }
        }
        is SessionState.SignedOut -> AppTheme {
            AuthFlow(services, current.reason) {
                AppModeStatus(services.settings, launchConfig.invalidConfiguration)
            }
        }
        is SessionState.SignedIn -> key(services, current.generation) {
            SignedInApp(services, launchConfig.invalidConfiguration)
        }
    }
}

@Composable
private fun SignedInApp(services: AppServices, invalidConfiguration: Boolean) {
    val repository = services.profile
    var retry by remember { mutableIntStateOf(0) }
    var account by remember {
        mutableStateOf<AccountUiState>(
            if (repository == null) AccountUiState.Unavailable else AccountUiState.Loading,
        )
    }
    LaunchedEffect(repository, retry) {
        if (repository != null) {
            account = AccountUiState.Loading
            account = try {
                AccountUiState.Ready(repository.profile(), repository.preferences())
            } catch (_: ApiException) {
                AccountUiState.Failed
            }
        }
    }

    val preferences = (account as? AccountUiState.Ready)?.preferences ?: AccessibilityPreferences.Default
    AppTheme(preferences) {
        AppNavigation(
            settings = services.settings,
            invalidConfiguration = invalidConfiguration,
            account = account,
            demo = services.demo,
            organizers = services.organizers,
            onRetry = { retry++ },
            onSaveDisplayName = { name ->
                val current = account as AccountUiState.Ready
                val profile = requireNotNull(repository).updateDisplayName(name)
                account = current.copy(profile = profile)
            },
            onSavePreferences = { draft ->
                val current = account as AccountUiState.Ready
                val saved = requireNotNull(repository).savePreferences(draft)
                account = current.copy(preferences = saved)
            },
            onSignOut = services.session::signOut,
        )
    }
}
