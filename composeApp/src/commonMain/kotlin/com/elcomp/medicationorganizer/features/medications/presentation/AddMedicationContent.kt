package com.elcomp.medicationorganizer.features.medications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.demo.MedicationDetails
import com.elcomp.medicationorganizer.core.time.currentLocalDate
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.demo.MedicationForm
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.features.medications.domain.MedicationDraft
import com.elcomp.medicationorganizer.features.medications.domain.MedicationField
import com.elcomp.medicationorganizer.features.medications.domain.MedicationFieldError
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val medicationDraftSaver = listSaver<MedicationDraft, String>(
    save = {
        listOf(it.name, it.doseAmount, it.doseUnit, it.startDate, it.endDate, it.strengthValue, it.strengthUnit, it.form?.name.orEmpty())
    },
    restore = {
        MedicationDraft(
            name = it[0],
            doseAmount = it[1],
            doseUnit = it[2],
            startDate = it[3],
            endDate = it[4],
            strengthValue = it[5],
            strengthUnit = it[6],
            form = MedicationForm.entries.firstOrNull { form -> form.name == it[7] },
        )
    },
)

@Composable
internal fun AddMedicationContent(
    repository: DemoRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    initial: MedicationDetails? = null,
    isSaving: Boolean = false,
    backLabel: StringResource = Res.string.medication_back,
    onSave: suspend (CreateMedication) -> Unit = { repository.createMedication(it) },
) {
    var draft by rememberSaveable(initial?.id, stateSaver = medicationDraftSaver) {
        mutableStateOf(initial?.let { MedicationDraft(it.name, it.doseAmount.content, it.doseUnit, it.startDate, it.endDate.orEmpty(), it.strengthValue?.content.orEmpty(), it.strengthUnit.orEmpty(), it.form) }
            ?: MedicationDraft(startDate = currentLocalDate()))
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val busy = saving || isSaving
    var failure by remember { mutableStateOf<ApiError?>(null) }
    var invalidSubmissions by remember { mutableIntStateOf(0) }
    val validation = remember(draft) { draft.validate() }
    val errors = if (submitted) validation.errors else emptyMap()
    val requesters = remember { MedicationField.entries.associateWith { BringIntoViewRequester() } }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(invalidSubmissions) {
        if (invalidSubmissions > 0) {
            MedicationField.entries.firstOrNull { it in errors }?.let { requesters.getValue(it).bringIntoView() }
        }
    }

    fun change(value: MedicationDraft) {
        draft = value
        failure = null
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("medication-form"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MedicationInput(
            MedicationField.NAME, draft.name, { change(draft.copy(name = it)) }, errors, requesters, !busy,
        )

        MedicationSection(Res.string.medication_dose_title) {
            MedicationInput(
                MedicationField.DOSE_AMOUNT, draft.doseAmount, { change(draft.copy(doseAmount = it)) },
                errors, requesters, !busy,
            )
            MedicationInput(
                MedicationField.DOSE_UNIT, draft.doseUnit, { change(draft.copy(doseUnit = it)) },
                errors, requesters, !busy,
            )
        }
        MedicationSection(Res.string.medication_dates_title) {
            Text(stringResource(Res.string.medication_date_hint), style = MaterialTheme.typography.bodyLarge)
            MedicationInput(
                MedicationField.START_DATE, draft.startDate, { change(draft.copy(startDate = it)) },
                errors, requesters, !busy,
            )
            MedicationInput(
                MedicationField.END_DATE, draft.endDate, { change(draft.copy(endDate = it)) },
                errors, requesters, !busy,
            )
        }
        MedicationSection(Res.string.medication_optional_title) {
            MedicationInput(
                MedicationField.STRENGTH_VALUE, draft.strengthValue, { change(draft.copy(strengthValue = it)) },
                errors, requesters, !busy,
            )
            MedicationInput(
                MedicationField.STRENGTH_UNIT, draft.strengthUnit, { change(draft.copy(strengthUnit = it)) },
                errors, requesters, !busy,
            )
            MedicationFormSelector(draft.form, enabled = !busy) { change(draft.copy(form = it)) }
        }

        if (errors.isNotEmpty()) {
            Text(
                stringResource(Res.string.medication_correct_fields),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        failure?.let { error ->
            AppErrorState(
                message = stringResource(
                    if (error == ApiError.INVALID_REQUEST) Res.string.medication_save_rejected
                    else Res.string.medication_save_failed,
                ),
                modifier = Modifier.testTag("medication-save-error"),
            )
        }
        AppSubmitButton(
            text = stringResource(Res.string.medication_save),
            onSubmit = {
                keyboard?.hide()
                submitted = true
                val request = validation.request
                if (request == null) {
                    invalidSubmissions++
                    return@AppSubmitButton
                }

                saving = true
                failure = null
                try {
                    onSave(request)
                } catch (error: ApiException) {
                    failure = error.error
                    return@AppSubmitButton
                } finally {
                    saving = false
                }
                onSaved()
            },
            modifier = Modifier.fillMaxWidth().testTag("medication-save"),
            isSaving = busy,
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("medication-back"),
            enabled = !busy,
        ) {
            Text(stringResource(backLabel), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MedicationInput(
    field: MedicationField,
    value: String,
    onChange: (String) -> Unit,
    errors: Map<MedicationField, MedicationFieldError>,
    requesters: Map<MedicationField, BringIntoViewRequester>,
    enabled: Boolean,
) {
    val label = when (field) {
        MedicationField.NAME -> Res.string.medication_name
        MedicationField.DOSE_AMOUNT -> Res.string.medication_dose_amount
        MedicationField.DOSE_UNIT -> Res.string.medication_dose_unit
        MedicationField.START_DATE -> Res.string.medication_start_date
        MedicationField.END_DATE -> Res.string.medication_end_date
        MedicationField.STRENGTH_VALUE -> Res.string.medication_strength_value
        MedicationField.STRENGTH_UNIT -> Res.string.medication_strength_unit
    }
    val error = when (errors[field]) {
        MedicationFieldError.REQUIRED -> Res.string.medication_field_required
        MedicationFieldError.POSITIVE_DECIMAL -> Res.string.medication_positive_decimal
        MedicationFieldError.INVALID_DATE -> Res.string.medication_invalid_date
        MedicationFieldError.END_BEFORE_START -> Res.string.medication_end_before_start
        null -> null
    }
    val keyboard = when (field) {
        MedicationField.DOSE_AMOUNT, MedicationField.STRENGTH_VALUE ->
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        MedicationField.START_DATE, MedicationField.END_DATE ->
            KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
        MedicationField.NAME, MedicationField.DOSE_UNIT, MedicationField.STRENGTH_UNIT ->
            KeyboardOptions(autoCorrectEnabled = false)
    }
    AppTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        modifier = Modifier
            .bringIntoViewRequester(requesters.getValue(field))
            .testTag("medication-${field.name.lowercase().replace('_', '-')}"),
        errorMessage = error?.let { stringResource(it) },
        enabled = enabled,
        keyboardOptions = keyboard,
    )
}

@Composable
private fun MedicationSection(title: StringResource, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        content()
    }
}

@Composable
private fun MedicationFormSelector(
    selected: MedicationForm?,
    enabled: Boolean,
    onSelect: (MedicationForm?) -> Unit,
) {
    Text(stringResource(Res.string.medication_form_label), style = MaterialTheme.typography.titleMedium)
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (form in listOf(null) + MedicationForm.entries) {
            val label = when (form) {
                MedicationForm.TABLET -> Res.string.medication_form_tablet
                MedicationForm.CAPSULE -> Res.string.medication_form_capsule
                MedicationForm.LIQUID -> Res.string.medication_form_liquid
                MedicationForm.OTHER -> Res.string.medication_form_other
                null -> Res.string.medication_form_none
            }
            FilterChip(
                selected = selected == form,
                onClick = { onSelect(form) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("medication-form-${form?.name?.lowercase() ?: "none"}"),
                enabled = enabled,
            )
        }
    }
}
