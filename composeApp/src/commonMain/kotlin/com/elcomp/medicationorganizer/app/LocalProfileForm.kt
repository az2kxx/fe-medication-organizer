package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private const val maxDisplayNameLength = 32

@Composable
internal fun LocalProfileForm(initialName: String, editing: Boolean, onSave: (String) -> Unit) {
    var draft by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val normalizedName = draft.trim()
    val error = when {
        normalizedName.isEmpty() -> Res.string.setup_name_required
        normalizedName.length > maxDisplayNameLength -> Res.string.setup_name_too_long
        else -> null
    }
    val tagPrefix = if (editing) "profile" else "setup"

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.setup_local_description))
        AppTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(Res.string.setup_name_label),
            modifier = Modifier.testTag(tagPrefix + "-name"),
            errorMessage = if (submitted && error != null) stringResource(error) else null,
        )
        AppSubmitButton(
            text = stringResource(if (editing) Res.string.profile_save else Res.string.setup_continue),
            onSubmit = {
                submitted = true
                if (error == null) onSave(normalizedName)
            },
            modifier = Modifier.fillMaxWidth().testTag(tagPrefix + "-submit"),
        )
        if (!editing) {
            Text(stringResource(Res.string.app_data_note))
        }
    }
}
