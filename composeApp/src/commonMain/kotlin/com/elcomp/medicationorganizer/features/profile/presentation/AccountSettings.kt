package com.elcomp.medicationorganizer.features.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.features.profile.data.AccessibilityPreferences
import com.elcomp.medicationorganizer.features.profile.data.ProfileResponse
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

internal sealed interface AccountUiState {
    data object Unavailable : AccountUiState
    data object Loading : AccountUiState
    data object Failed : AccountUiState

    data class Ready(
        val profile: ProfileResponse,
        val preferences: AccessibilityPreferences,
    ) : AccountUiState
}

private val preferencesDraftSaver = listSaver<AccessibilityPreferences, Any>(
    save = { listOf(it.simplifiedMode, it.textScale, it.highContrast) },
    restore = {
        AccessibilityPreferences(
            simplifiedMode = it[0] as Boolean,
            textScale = it[1] as Int,
            highContrast = it[2] as Boolean,
        )
    },
)

@Composable
internal fun AccessibilitySettings(
    current: AccessibilityPreferences,
    onSave: suspend (AccessibilityPreferences) -> Unit,
) {
    var draft by rememberSaveable(current, stateSaver = preferencesDraftSaver) { mutableStateOf(current) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun update(value: AccessibilityPreferences) {
        draft = value
        saved = false
        failed = false
    }

    Text(
        stringResource(Res.string.preferences_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
    PreferenceSwitch(
        label = stringResource(Res.string.preferences_simplified),
        checked = draft.simplifiedMode,
        enabled = !saving,
        tag = "preferences-simplified",
        onChange = { update(draft.copy(simplifiedMode = it)) },
    )
    PreferenceSwitch(
        label = stringResource(Res.string.preferences_high_contrast),
        checked = draft.highContrast,
        enabled = !saving,
        tag = "preferences-high-contrast",
        onChange = { update(draft.copy(highContrast = it)) },
    )

    Text(
        stringResource(Res.string.preferences_text_scale, draft.textScale),
        style = MaterialTheme.typography.titleMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (percent in 100..200 step 25) {
            FilterChip(
                selected = draft.textScale == percent,
                onClick = { update(draft.copy(textScale = percent)) },
                label = { Text(stringResource(Res.string.preferences_percent, percent)) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("preferences-text-$percent"),
                enabled = !saving,
            )
        }
    }

    if (failed) {
        AppErrorState(message = stringResource(Res.string.preferences_save_failed))
    }
    if (saved) {
        Text(
            stringResource(Res.string.preferences_saved),
            modifier = Modifier.testTag("preferences-saved").semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    AppSubmitButton(
        text = stringResource(Res.string.preferences_save),
        enabled = draft != current && !saving,
        onSubmit = {
            saving = true
            failed = false
            try {
                onSave(draft)
                saved = true
            } catch (_: ApiException) {
                failed = true
            } finally {
                saving = false
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("preferences-save"),
    )
}

@Composable
private fun PreferenceSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
