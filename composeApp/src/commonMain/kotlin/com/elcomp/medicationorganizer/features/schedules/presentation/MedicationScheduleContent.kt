package com.elcomp.medicationorganizer.features.schedules.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.*
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.*
import com.elcomp.medicationorganizer.core.time.shiftDate
import com.elcomp.medicationorganizer.features.organizers.data.UserOrganizer
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private suspend fun DemoRepository.optionalSchedule(id: Long): MedicationSchedule? = try {
    currentSchedule(id)
} catch (error: ApiException) {
    if (error.error == ApiError.NOT_FOUND) null else throw error
}

@Composable
internal fun MedicationScheduleContent(
    demo: DemoRepository,
    medicationId: Long,
    organizers: List<UserOrganizer>,
    organizerNames: Map<Long, String>,
    preferredOrganizerId: Long?,
    busy: Boolean,
    mutate: suspend (suspend () -> Unit) -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    LoadContent(demo, medicationId, load = { demo.medication(medicationId) to demo.optionalSchedule(medicationId) }) { (medication, schedule) ->
        val active = organizers.filter { it.active }
        if (active.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppEmptyState(stringResource(Res.string.schedule_no_organizer_title), stringResource(Res.string.schedule_no_organizer_message))
                AppActionButton(stringResource(Res.string.medication_back), onBack)
            }
        } else {
            var organizerId by rememberSaveable(medicationId) {
                mutableStateOf(schedule?.userOrganizerId?.takeIf { id -> active.any { it.id == id } }
                    ?: preferredOrganizerId?.takeIf { id -> active.any { it.id == id } }
                    ?: active.first().id)
            }
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                Text(medication.name, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                Box(Modifier.padding(horizontal = 24.dp)) {
                    OutlinedButton({ expanded = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("schedule-organizer")) {
                        Text(stringResource(Res.string.schedule_organizer, organizerNames[organizerId] ?: stringResource(Res.string.navigation_organizer)))
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        active.forEach { assignment ->
                            DropdownMenuItem(
                                text = { Text(organizerNames[assignment.id] ?: stringResource(Res.string.navigation_organizer)) },
                                onClick = { organizerId = assignment.id; expanded = false },
                                modifier = Modifier.heightIn(min = 56.dp).testTag("schedule-assignment-${assignment.id}"),
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    val initial = remember(schedule, medicationId) {
                        schedule?.let { ReplaceSchedule(it.validFrom, it.days, it.times, it.validUntil) }
                            ?: ReplaceSchedule(medication.startDate, emptySet(), listOf(ScheduleTime("")), medication.endDate)
                    }
                    ScheduleEditor(
                        medicationId, initial, isSaving = busy,
                        onSave = { id, request ->
                            val payload = request.copy(userOrganizerId = organizerId)
                            mutate { demo.replaceSchedule(id, payload) }
                        },
                        onSaved = onSaved, onCancel = onBack,
                    )
                }
            }
        }
    }
}

private data class Preview(
    val schedule: MedicationSchedule?,
    val doses: List<DoseOccurrence> = emptyList(),
    val model: OrganizerModelDetails? = null,
    val unavailableOrganizer: Boolean = false,
)

@Composable
internal fun MedicationSchedulePreview(
    demo: DemoRepository,
    medicationId: Long,
    organizers: List<UserOrganizer>,
    organizerNames: Map<Long, String>,
    revision: Int,
) {
    Column(Modifier.fillMaxWidth().testTag("schedule-preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.schedule_preview_title), style = MaterialTheme.typography.titleLarge)
        LoadContent(demo, medicationId, revision, organizers, load = {
            val schedule = demo.optionalSchedule(medicationId)
            val assignment = organizers.firstOrNull { it.id == schedule?.userOrganizerId }
            when {
                schedule == null -> Preview(null)
                assignment == null || !assignment.active -> Preview(schedule, unavailableOrganizer = true)
                else -> Preview(schedule,
                    demo.weeklyPlan(schedule.validFrom, assignment.id).filter { it.medicationId == medicationId },
                    demo.organizerModel(assignment.organizerModelId))
            }
        }) { preview ->
            val schedule = preview.schedule
            when {
                schedule == null -> Text(stringResource(Res.string.schedule_not_set))
                preview.unavailableOrganizer -> Text(stringResource(Res.string.schedule_organizer_unavailable))
                else -> {
                    Text(stringResource(Res.string.schedule_organizer, organizerNames[schedule.userOrganizerId] ?: stringResource(Res.string.navigation_organizer)))
                    Text(stringResource(Res.string.schedule_preview_range, schedule.validFrom, shiftDate(schedule.validFrom, 6)))
                    if (preview.doses.isEmpty()) Text(stringResource(Res.string.schedule_preview_empty))
                    preview.doses.forEach { dose ->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("schedule-preview-dose-${dose.id}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${dose.scheduledDate} · ${dose.scheduledTime}", style = MaterialTheme.typography.titleMedium)
                                Text("${dose.doseAmountSnapshot.content} ${dose.doseUnitSnapshot}")
                                Text(stringResource(doseStatusLabel(dose.status)))
                                val label = preview.model?.compartments?.firstOrNull { it.id == dose.compartmentDefinitionId }?.label
                                Text(label ?: stringResource(Res.string.schedule_no_compartment))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun doseStatusLabel(status: DoseStatus) = when (status) {
    DoseStatus.PENDING -> Res.string.schedule_dose_pending
    DoseStatus.TAKEN -> Res.string.schedule_dose_taken
    DoseStatus.SKIPPED -> Res.string.schedule_dose_skipped
    DoseStatus.MISSED -> Res.string.schedule_dose_missed
}
