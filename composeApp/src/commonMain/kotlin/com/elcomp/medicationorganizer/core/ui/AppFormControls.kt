package com.elcomp.medicationorganizer.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.resources.Res
import com.elcomp.medicationorganizer.resources.component_saving
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val MinimumControlHeight = 56.dp

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val errorSemantics = if (errorMessage == null) {
        Modifier
    } else {
        Modifier.semantics { error(errorMessage) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinimumControlHeight)
            .then(errorSemantics),
        label = { Text(label) },
        enabled = enabled,
        isError = errorMessage != null,
        supportingText = if (errorMessage == null) {
            null
        } else {
            {
                Text(
                    text = errorMessage,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
    )
}

@Composable
fun AppActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinimumControlHeight),
        enabled = enabled,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The submit action stays locked until [onSubmit] completes. */
@Composable
fun AppSubmitButton(
    text: String,
    onSubmit: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSaving: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    val busy = submitting || isSaving
    AppActionButton(
        text = if (busy) stringResource(Res.string.component_saving) else text,
        enabled = enabled && !busy,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        onClick = {
            // A second input event can arrive before the disabled state is recomposed.
            if (enabled && !submitting && !isSaving) {
                submitting = true
                scope.launch {
                    try {
                        onSubmit()
                    } finally {
                        submitting = false
                    }
                }
            }
        },
    )
}
