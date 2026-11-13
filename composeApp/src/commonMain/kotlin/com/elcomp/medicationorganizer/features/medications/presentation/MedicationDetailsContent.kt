package com.elcomp.medicationorganizer.features.medications.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.*
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.*
import com.elcomp.medicationorganizer.features.organizers.data.UserOrganizer
import com.elcomp.medicationorganizer.features.schedules.presentation.MedicationSchedulePreview
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun MedicationDetailsContent(
    demo: DemoRepository,
    medicationId: Long,
    busy: Boolean,
    mutate: suspend (suspend () -> Unit) -> Unit,
    onSchedule: (Long) -> Unit,
    onBack: () -> Unit,
    organizers: List<UserOrganizer> = emptyList(),
    organizerNames: Map<Long, String> = emptyMap(),
    revision: Long = 0,
) {
    var editing by rememberSaveable(medicationId) { mutableStateOf(false) }
    var confirmInactive by rememberSaveable(medicationId) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    BackHandler(enabled = editing) { if (!busy) editing = false }
    LoadContent(demo, medicationId, retry, if (editing) Unit else revision, load = { demo.medication(medicationId) }) { medication ->
        LaunchedEffect(medication.status) {
            if (medication.status == MedicationStatus.INACTIVE) confirmInactive = false
        }
        if (editing) {
            AddMedicationContent(
                demo, initial = medication, isSaving = busy, backLabel = Res.string.medication_cancel_edit,
                onBack = { editing = false },
                onSave = { request ->
                    mutate {
                        demo.updateMedication(medicationId, UpdateMedication(request.name, request.doseAmount, request.doseUnit,
                            request.startDate, request.endDate, request.strengthValue, request.strengthUnit, request.form))
                    }
                },
                onSaved = { editing = false; retry++ },
            )
        } else {
            Text(medication.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("medication-details-name"))
            Text("${medication.doseAmount.content} ${medication.doseUnit}")
            Text(stringResource(Res.string.medication_dates_summary, medication.startDate, medication.endDate ?: stringResource(Res.string.full_no_end)))
            medication.strengthValue?.let { Text(stringResource(Res.string.medication_strength_summary, it.content, medication.strengthUnit.orEmpty())) }
            medication.form?.let { Text(stringResource(formLabel(it))) }
            Text(stringResource(statusLabel(medication.status)), Modifier.testTag("medication-status"))
            if (failed) AppErrorState(stringResource(Res.string.medication_save_failed))
            AppActionButton(stringResource(Res.string.full_edit), { editing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("medication-edit"))
            AppActionButton(stringResource(Res.string.schedule_title), { onSchedule(medicationId) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("medication-open-schedule"))
            MedicationSchedulePreview(demo, medicationId, organizers, organizerNames, retry)
            suspend fun setStatus(status: MedicationStatus) {
                failed = false
                try { mutate { demo.updateMedicationStatus(medicationId, UpdateMedicationStatus(status)) }; retry++ }
                catch (_: ApiException) { failed = true }
            }
            if (medication.status != MedicationStatus.INACTIVE) {
                AppSubmitButton(
                    stringResource(if (medication.status == MedicationStatus.ACTIVE) Res.string.medication_suspend else Res.string.medication_resume),
                    onSubmit = { setStatus(if (medication.status == MedicationStatus.ACTIVE) MedicationStatus.SUSPENDED else MedicationStatus.ACTIVE) },
                    enabled = !busy, isSaving = busy, modifier = Modifier.fillMaxWidth().testTag("medication-toggle-status"),
                )
                OutlinedButton({ confirmInactive = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("medication-deactivate")) {
                    Text(stringResource(Res.string.medication_deactivate))
                }
            } else {
                AppSubmitButton(stringResource(Res.string.medication_resume), { setStatus(MedicationStatus.ACTIVE) }, enabled = !busy, isSaving = busy,
                    modifier = Modifier.fillMaxWidth().testTag("medication-toggle-status"))
            }
            if (confirmInactive && medication.status != MedicationStatus.INACTIVE) AlertDialog(
                onDismissRequest = { if (!busy) confirmInactive = false },
                title = { Text(stringResource(Res.string.medication_deactivate)) },
                text = { Text(stringResource(Res.string.medication_deactivate_explanation)) },
                confirmButton = { AppSubmitButton(stringResource(Res.string.medication_deactivate), {
                    setStatus(MedicationStatus.INACTIVE); confirmInactive = false
                }, enabled = !busy, isSaving = busy, modifier = Modifier.testTag("medication-confirm-deactivate")) },
                dismissButton = { TextButton({ confirmInactive = false }, enabled = !busy) { Text(stringResource(Res.string.schedule_cancel)) } },
            )
        }
    }
    if (!editing) OutlinedButton(onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("medication-details-back")) {
        Text(stringResource(Res.string.medication_back))
    }
}

internal fun statusLabel(status: MedicationStatus) = when (status) {
    MedicationStatus.ACTIVE -> Res.string.medication_active
    MedicationStatus.SUSPENDED -> Res.string.medication_suspended
    MedicationStatus.INACTIVE -> Res.string.medication_inactive
}

private fun formLabel(form: MedicationForm) = when (form) {
    MedicationForm.TABLET -> Res.string.medication_form_tablet
    MedicationForm.CAPSULE -> Res.string.medication_form_capsule
    MedicationForm.LIQUID -> Res.string.medication_form_liquid
    MedicationForm.OTHER -> Res.string.medication_form_other
}
