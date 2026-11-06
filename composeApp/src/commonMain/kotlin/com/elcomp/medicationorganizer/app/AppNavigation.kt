package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.ui.AppEmptyState
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Destination(val slug: String, val title: StringResource) {
    Setup("setup", Res.string.setup_title),
    Today("today", Res.string.navigation_today),
    Organizer("organizer", Res.string.navigation_organizer),
    Medications("medications", Res.string.navigation_medications),
    Preparation("preparation", Res.string.navigation_preparation),
    History("history", Res.string.navigation_history),
    Settings("settings", Res.string.navigation_settings),
    Profile("profile", Res.string.profile_title),
}

private val sections = listOf(
    Destination.Today,
    Destination.Organizer,
    Destination.Medications,
    Destination.Preparation,
    Destination.History,
    Destination.Settings,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppNavigation(settings: ApiSettings, invalidConfiguration: Boolean) {
    var routeName by rememberSaveable { mutableStateOf(Destination.Setup.name) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    val route = Destination.entries.firstOrNull { it.name == routeName } ?: Destination.Setup

    fun navigate(destination: Destination) {
        routeName = destination.name
        menuOpen = false
    }

    fun goBack() {
        when {
            menuOpen -> menuOpen = false
            route == Destination.Profile -> navigate(Destination.Settings)
            else -> navigate(Destination.Today)
        }
    }

    BackHandler(enabled = menuOpen || (route != Destination.Setup && route != Destination.Today)) {
        goBack()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        key(route, menuOpen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .testTag("navigation-scroll")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppHeader(settings, invalidConfiguration)
                if (menuOpen) {
                    SectionMenu(route, onChoose = ::navigate, onClose = ::goBack)
                } else {
                    val title = stringResource(route.title)
                    val screenTag = if (route == Destination.Setup || route == Destination.Profile) {
                        route.slug + "-screen"
                    } else {
                        "screen-" + route.slug
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(screenTag)
                            .semantics { paneTitle = title },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (route in sections) {
                            NavigationButton(stringResource(Res.string.navigation_open_menu), "navigation-open-menu") {
                                menuOpen = true
                            }
                            if (route != Destination.Today) {
                                NavigationButton(stringResource(Res.string.navigation_back_today), "navigation-today") {
                                    navigate(Destination.Today)
                                }
                            }
                        }

                        NavigationTitle(title)
                        when (route) {
                            Destination.Setup, Destination.Profile -> {
                                val editing = route == Destination.Profile
                                LocalProfileForm(
                                    initialName = displayName,
                                    editing = editing,
                                    onSave = { name ->
                                        displayName = name
                                        navigate(if (editing) Destination.Settings else Destination.Today)
                                    },
                                )
                                if (editing) {
                                    NavigationButton(
                                        stringResource(Res.string.navigation_back_settings),
                                        "navigation-back-settings",
                                        onClick = ::goBack,
                                    )
                                }
                            }
                            Destination.Settings -> {
                                Text(stringResource(Res.string.profile_name_summary, displayName))
                                NavigationButton(
                                    stringResource(Res.string.navigation_open_profile),
                                    "navigation-open-profile",
                                ) {
                                    navigate(Destination.Profile)
                                }
                            }
                            else -> SectionContent(route, displayName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(settings: ApiSettings, invalidConfiguration: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.app_brand), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.app_product), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                if (settings is ApiSettings.Mock) Res.string.app_mode_mock else Res.string.app_mode_live,
            ),
        )
        val scenarioLabel = when ((settings as? ApiSettings.Mock)?.scenario) {
            MockScenario.EMPTY -> Res.string.app_scenario_empty
            MockScenario.SLOW -> Res.string.app_scenario_slow
            MockScenario.OFFLINE -> Res.string.app_scenario_offline
            MockScenario.API_ERROR -> Res.string.app_scenario_api_error
            MockScenario.NORMAL, null -> null
        }
        if (scenarioLabel != null) {
            Text(stringResource(scenarioLabel))
        }
        if (invalidConfiguration) {
            AppErrorState(message = stringResource(Res.string.app_configuration_warning))
        }
    }
}

@Composable
private fun SectionMenu(current: Destination, onChoose: (Destination) -> Unit, onClose: () -> Unit) {
    val title = stringResource(Res.string.navigation_menu_title)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("navigation-menu").semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavigationTitle(title)
        NavigationButton(stringResource(Res.string.navigation_close_menu), "navigation-close-menu", onClick = onClose)
        for (destination in sections) {
            NavigationButton(
                text = stringResource(destination.title),
                tag = "navigation-" + destination.slug + "-destination",
                current = destination == current,
                onClick = { onChoose(destination) },
            )
        }
    }
}

@Composable
private fun SectionContent(route: Destination, displayName: String) {
    val emptyState = when (route) {
        Destination.Today -> Res.string.today_empty_title to Res.string.today_empty_message
        Destination.Organizer -> Res.string.organizer_empty_title to Res.string.organizer_empty_message
        Destination.Medications -> Res.string.medications_empty_title to Res.string.medications_empty_message
        Destination.Preparation -> Res.string.preparation_empty_title to Res.string.preparation_empty_message
        Destination.History -> Res.string.history_empty_title to Res.string.history_empty_message
        Destination.Setup, Destination.Settings, Destination.Profile -> return
    }
    if (route == Destination.Today) {
        Text(stringResource(Res.string.today_greeting, displayName))
    }
    AppEmptyState(title = stringResource(emptyState.first), message = stringResource(emptyState.second))
    if (route == Destination.Today) {
        Text(stringResource(Res.string.app_data_note))
    }
}

@Composable
private fun NavigationTitle(title: String) {
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun NavigationButton(text: String, tag: String, current: Boolean? = null, onClick: () -> Unit) {
    val selection = if (current == null) Modifier else Modifier.semantics { selected = current }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).then(selection),
    ) {
        Text(text)
    }
}
