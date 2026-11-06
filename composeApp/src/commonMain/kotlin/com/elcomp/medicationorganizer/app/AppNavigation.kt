package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.session.SessionStorageException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppEmptyState
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerCatalogRepository
import com.elcomp.medicationorganizer.features.organizers.presentation.OrganizerCatalogFlow
import com.elcomp.medicationorganizer.features.profile.data.AccessibilityPreferences
import com.elcomp.medicationorganizer.features.profile.presentation.AccessibilitySettings
import com.elcomp.medicationorganizer.features.profile.presentation.AccountUiState
import com.elcomp.medicationorganizer.features.profile.presentation.ProfileForm
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Destination(val tag: String, val title: StringResource, val tab: MainTab? = null) {
    Today("screen-today", Res.string.navigation_today, MainTab.Today),
    Organizer("screen-organizer", Res.string.navigation_organizer, MainTab.Organizer),
    OrganizerCatalog("screen-organizer-catalog", Res.string.organizer_catalog_title, MainTab.Organizer),
    Medications("screen-medications", Res.string.navigation_medications, MainTab.Medications),
    Preparation("screen-preparation", Res.string.navigation_preparation, MainTab.Organizer),
    History("screen-history", Res.string.navigation_history, MainTab.History),
    Settings("screen-settings", Res.string.navigation_settings, MainTab.Today),
    Profile("profile-screen", Res.string.profile_title),
}

private fun MainTab.destination(): Destination = when (this) {
    MainTab.Today -> Destination.Today
    MainTab.Organizer -> Destination.Organizer
    MainTab.Medications -> Destination.Medications
    MainTab.History -> Destination.History
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppNavigation(
    settings: ApiSettings,
    invalidConfiguration: Boolean,
    account: AccountUiState,
    demo: DemoRepository?,
    organizers: OrganizerCatalogRepository?,
    onRetry: () -> Unit,
    onSaveDisplayName: suspend (String) -> Unit,
    onSavePreferences: suspend (AccessibilityPreferences) -> Unit,
    onSignOut: suspend () -> Unit,
) {
    var routeName by rememberSaveable { mutableStateOf(Destination.Today.name) }
    val route = Destination.entries.firstOrNull { it.name == routeName } ?: Destination.Today
    val ready = account as? AccountUiState.Ready
    val preferences = ready?.preferences ?: AccessibilityPreferences.Default

    fun navigate(destination: Destination) {
        routeName = destination.name
    }

    val backDestination = when (route) {
        Destination.Today -> null
        Destination.Preparation, Destination.OrganizerCatalog -> Destination.Organizer
        Destination.Profile -> Destination.Settings
        else -> Destination.Today
    }
    BackHandler(enabled = backDestination != null) {
        backDestination?.let(::navigate)
    }

    val tab = route.tab
    val contentInsets = if (tab == null) {
        WindowInsets.safeDrawing
    } else {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            key(route) {
                val title = stringResource(route.title)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(contentInsets)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .testTag(route.tag)
                        .semantics { paneTitle = title },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    AppModeStatus(settings, invalidConfiguration)
                    when (route) {
                        Destination.Today -> {
                            val onOrganizer = { navigate(Destination.Organizer) }
                            if (preferences.simplifiedMode && demo != null) {
                                SimplifiedToday(demo, onOrganizer)
                            } else {
                                TodayContent(ready?.profile?.displayName.orEmpty(), onOrganizer)
                            }
                            SecondaryAction(Res.string.navigation_settings, "today-open-settings") {
                                navigate(Destination.Settings)
                            }
                        }
                        Destination.Organizer -> {
                            Text(stringResource(Res.string.organizer_description), style = MaterialTheme.typography.bodyLarge)
                            SecondaryAction(Res.string.organizer_choose_model, "organizer-open-catalog") {
                                navigate(Destination.OrganizerCatalog)
                            }
                            AppActionButton(
                                text = stringResource(Res.string.organizer_open_preparation),
                                onClick = { navigate(Destination.Preparation) },
                                modifier = Modifier.fillMaxWidth().testTag("organizer-open-preparation"),
                            )
                        }
                        Destination.OrganizerCatalog -> if (organizers != null) {
                            OrganizerCatalogFlow(organizers)
                        } else {
                            Text(
                                stringResource(Res.string.organizer_catalog_live_unavailable),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Destination.Preparation -> {
                            AppEmptyState(
                                title = stringResource(Res.string.preparation_empty_title),
                                message = stringResource(Res.string.preparation_empty_message),
                            )
                            SecondaryAction(
                                Res.string.preparation_back_organizer,
                                "preparation-back-organizer",
                            ) { navigate(Destination.Organizer) }
                        }
                        Destination.Medications -> AppEmptyState(
                            title = stringResource(Res.string.medications_empty_title),
                            message = stringResource(Res.string.medications_empty_message),
                        )
                        Destination.History -> AppEmptyState(
                            title = stringResource(Res.string.history_empty_title),
                            message = stringResource(Res.string.history_empty_message),
                        )
                        Destination.Settings -> SettingsContent(
                            account = account,
                            onProfile = { navigate(Destination.Profile) },
                            onRetry = onRetry,
                            onSavePreferences = onSavePreferences,
                            onSignOut = onSignOut,
                        )
                        Destination.Profile -> {
                            if (ready != null) {
                                ProfileForm(
                                    email = ready.profile.email,
                                    initialName = ready.profile.displayName.orEmpty(),
                                    onSave = { name ->
                                        onSaveDisplayName(name)
                                        navigate(Destination.Settings)
                                    },
                                )
                            }
                            SecondaryAction(Res.string.navigation_back_settings, "navigation-back-settings") {
                                navigate(Destination.Settings)
                            }
                        }
                    }
                }
            }
            if (tab != null) {
                AppBottomNavigation(
                    selected = tab,
                    textScale = preferences.textScale,
                    onSelect = { navigate(it.destination()) },
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    account: AccountUiState,
    onProfile: () -> Unit,
    onRetry: () -> Unit,
    onSavePreferences: suspend (AccessibilityPreferences) -> Unit,
    onSignOut: suspend () -> Unit,
) {
    var signOutFailed by remember { mutableStateOf(false) }
    when (account) {
        AccountUiState.Unavailable -> Text(stringResource(Res.string.settings_live_unavailable))
        AccountUiState.Loading -> AppLoadingState()
        AccountUiState.Failed -> AppErrorState(
            message = stringResource(Res.string.settings_load_failed),
            onRetry = onRetry,
        )
        is AccountUiState.Ready -> {
            Text(
                stringResource(Res.string.profile_email, account.profile.email),
                modifier = Modifier.testTag("profile-email"),
            )
            val displayName = account.profile.displayName
            Text(
                if (displayName.isNullOrBlank()) stringResource(Res.string.profile_name_missing)
                else stringResource(Res.string.profile_name_summary, displayName),
            )
            SecondaryAction(Res.string.navigation_open_profile, "navigation-open-profile", onProfile)
            AccessibilitySettings(account.preferences, onSavePreferences)
        }
    }
    if (signOutFailed) {
        AppErrorState(message = stringResource(Res.string.auth_logout_failed))
    }
    AppSubmitButton(
        text = stringResource(Res.string.auth_logout),
        onSubmit = {
            signOutFailed = false
            try {
                onSignOut()
            } catch (_: SessionStorageException) {
                signOutFailed = true
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("settings-sign-out"),
    )
}

@Composable
private fun TodayContent(displayName: String, onOrganizer: () -> Unit) {
    if (displayName.isNotBlank()) {
        Text(stringResource(Res.string.today_greeting, displayName), style = MaterialTheme.typography.titleLarge)
    }
    Text(stringResource(Res.string.today_description), style = MaterialTheme.typography.bodyLarge)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.today_first_step_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(Res.string.today_first_step_description), style = MaterialTheme.typography.bodyLarge)
            AppActionButton(
                text = stringResource(Res.string.today_open_organizer),
                onClick = onOrganizer,
                modifier = Modifier.fillMaxWidth().testTag("today-open-organizer"),
            )
        }
    }
}

@Composable
internal fun AppModeStatus(settings: ApiSettings, invalidConfiguration: Boolean) {
    Text(
        stringResource(if (settings is ApiSettings.Mock) Res.string.app_mode_mock else Res.string.app_mode_live),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val scenarioLabel = when ((settings as? ApiSettings.Mock)?.scenario) {
        MockScenario.EMPTY -> Res.string.app_scenario_empty
        MockScenario.SLOW -> Res.string.app_scenario_slow
        MockScenario.OFFLINE -> Res.string.app_scenario_offline
        MockScenario.API_ERROR -> Res.string.app_scenario_api_error
        MockScenario.NORMAL, null -> null
    }
    if (scenarioLabel != null) {
        Text(stringResource(scenarioLabel), style = MaterialTheme.typography.labelLarge)
    }
    if (invalidConfiguration) {
        AppErrorState(message = stringResource(Res.string.app_configuration_warning))
    }
}

@Composable
private fun SecondaryAction(text: StringResource, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
    }
}
