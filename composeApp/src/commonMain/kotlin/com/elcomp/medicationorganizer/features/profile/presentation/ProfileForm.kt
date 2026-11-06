package com.elcomp.medicationorganizer.features.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private const val maxDisplayNameLength = 32

@Composable
internal fun ProfileForm(email: String, initialName: String, onSave: suspend (String) -> Unit) {
    var draft by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val normalizedName = draft.trim()
    val error = when {
        normalizedName.isEmpty() -> Res.string.profile_name_required
        normalizedName.length > maxDisplayNameLength -> Res.string.profile_name_too_long
        rejected -> Res.string.profile_name_rejected
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.profile_email, email), modifier = Modifier.testTag("profile-email"))
        AppTextField(
            value = draft,
            onValueChange = {
                draft = it
                rejected = false
                saveFailed = false
            },
            label = stringResource(Res.string.profile_name_label),
            modifier = Modifier.testTag("profile-name"),
            enabled = !saving,
            errorMessage = if (submitted && error != null) stringResource(error) else null,
        )
        if (saveFailed) {
            AppErrorState(message = stringResource(Res.string.profile_save_failed))
        }
        AppSubmitButton(
            text = stringResource(Res.string.profile_save),
            onSubmit = {
                submitted = true
                if (error == null) {
                    saving = true
                    saveFailed = false
                    try {
                        onSave(normalizedName)
                    } catch (failure: ApiException) {
                        if (failure.error == ApiError.INVALID_REQUEST) rejected = true
                        else saveFailed = true
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("profile-submit"),
        )
    }
}
