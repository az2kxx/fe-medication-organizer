package com.elcomp.medicationorganizer.features.schedules.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleDraft
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleField
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleFieldError
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleTimeDraft
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val draftSaver = listSaver<ScheduleDraft, Any>(
    save = { draft ->
        listOf(draft.validFrom, draft.validUntil, draft.days.sorted().joinToString(",")) +
            draft.times.flatMap { listOf(it.time, it.timeOfDay?.name.orEmpty()) }
    },
    restore = { values ->
        ScheduleDraft(
            validFrom = values[0] as String,
            validUntil = values[1] as String,
            days = (values[2] as String).split(',').filter(String::isNotEmpty).map(String::toInt).toSet(),
            times = values.drop(3).chunked(2).map {
                ScheduleTimeDraft(it[0] as String, (it[1] as String).takeIf(String::isNotEmpty)?.let(TimeOfDay::valueOf))
            },
        )
    },
)

private val rowKeysSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })

/**
 * Standalone, scrollable screen content; the parent provides bounded height and the medication context.
 * [onSave] receives one complete, validated PUT payload. The parent owns durable writes, shared saving
 * state and plan refresh; [onSaved] is called only after a successful save. Nothing is wired to FE-11 yet.
 */
@Composable
fun ScheduleEditor(
    medicationId: Long,
    initialSchedule: ReplaceSchedule? = null,
    onSave: suspend (Long, ReplaceSchedule) -> Unit,
    onSaved: () -> Unit = {},
    onCancel: () -> Unit,
    isSaving: Boolean = false,
    modifier: Modifier = Modifier,
) {
    require(medicationId > 0)
    key(medicationId, initialSchedule) {
        ScheduleEditorContent(medicationId, initialSchedule, onSave, onSaved, onCancel, isSaving, modifier)
    }
}

@Composable
private fun ScheduleEditorContent(
    medicationId: Long,
    initialSchedule: ReplaceSchedule?,
    onSave: suspend (Long, ReplaceSchedule) -> Unit,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier,
) {
    val initialDraft = remember { initialSchedule?.let(ScheduleDraft::from) ?: ScheduleDraft() }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(initialDraft) }
    var rowKeys by rememberSaveable(stateSaver = rowKeysSaver) { mutableStateOf(initialDraft.times.indices.toList()) }
    var nextRowKey by rememberSaveable { mutableIntStateOf(initialDraft.times.size) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var invalidSubmissions by remember { mutableIntStateOf(0) }
    val busy = saving || isSaving
    val validation = draft.validate()
    val keyboard = LocalSoftwareKeyboardController.current
    val daysRequester = remember { BringIntoViewRequester() }
    val timesRequester = remember { BringIntoViewRequester() }
    val fromRequester = remember { BringIntoViewRequester() }
    val untilRequester = remember { BringIntoViewRequester() }
    val timeRequesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }

    fun update(value: ScheduleDraft) {
        draft = value
        failed = false
        saved = false
    }

    LaunchedEffect(invalidSubmissions) {
        if (invalidSubmissions > 0) {
            when {
                ScheduleField.DAYS in validation.errors -> daysRequester.bringIntoView()
                ScheduleField.TIMES in validation.errors -> timesRequester.bringIntoView()
                validation.timeErrors.isNotEmpty() -> validation.timeErrors.keys.minOrNull()
                    ?.let { rowKeys.getOrNull(it) }?.let { timeRequesters[it]?.bringIntoView() }
                ScheduleField.VALID_FROM in validation.errors -> fromRequester.bringIntoView()
                ScheduleField.VALID_UNTIL in validation.errors -> untilRequester.bringIntoView()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("schedule-editor"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(Res.string.schedule_title)
        Text(stringResource(Res.string.schedule_description))
        val daysError = if (attempted) validation.errors[ScheduleField.DAYS]?.let {
            if (it == ScheduleFieldError.REQUIRED) stringResource(Res.string.schedule_days_required) else fieldErrorText(it)
        } else null
        Column(
            Modifier.fillMaxWidth().bringIntoViewRequester(daysRequester).testTag("schedule-days")
                .semantics { daysError?.let { error(it) } },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(Res.string.schedule_days)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in 1..7) {
                    FilterChip(
                        selected = day in draft.days,
                        onClick = { update(draft.copy(days = draft.days.let { if (day in it) it - day else it + day })) },
                        label = { Text(stringResource(weekdayLabel(day))) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 56.dp).testTag("schedule-day-$day"),
                    )
                }
            }
            OutlinedButton(
                onClick = { update(draft.copy(days = (1..7).toSet())) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 56.dp).testTag("schedule-every-day"),
            ) { Text(stringResource(Res.string.schedule_every_day)) }
            daysError?.let { ErrorText(it) }
        }

        val timesError = if (attempted && ScheduleField.TIMES in validation.errors) stringResource(Res.string.schedule_times_required) else null
        Column(
            Modifier.fillMaxWidth().bringIntoViewRequester(timesRequester).testTag("schedule-times")
                .semantics { timesError?.let { error(it) } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(Res.string.schedule_times)
            for ((index, entry) in draft.times.withIndex()) {
                val rowKey = rowKeys[index]
                key(rowKey) {
                    val requester = timeRequesters.getOrPut(rowKey) { BringIntoViewRequester() }
                    OutlinedCard(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.schedule_time_number, index + 1), style = MaterialTheme.typography.titleMedium)
                            AppTextField(
                                value = entry.time,
                                onValueChange = { value ->
                                    update(draft.copy(times = draft.times.mapIndexed { i, old -> if (i == index) old.copy(time = value) else old }))
                                },
                                label = stringResource(Res.string.schedule_time),
                                errorMessage = if (attempted) validation.timeErrors[index]?.let { fieldErrorText(it) } else null,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                                modifier = Modifier.testTag("schedule-time-$index"),
                            )
                            TimeOfDayPicker(index, entry.timeOfDay, !busy) { period ->
                                update(draft.copy(times = draft.times.mapIndexed { i, old -> if (i == index) old.copy(timeOfDay = period) else old }))
                            }
                            OutlinedButton(
                                onClick = {
                                    update(draft.copy(times = draft.times.filterIndexed { i, _ -> i != index }))
                                    rowKeys = rowKeys.filterIndexed { i, _ -> i != index }
                                    timeRequesters.remove(rowKey)
                                },
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 56.dp).testTag("schedule-remove-time-$index"),
                            ) { Text(stringResource(Res.string.schedule_remove_time, index + 1)) }
                        }
                    }
                }
            }
            timesError?.let { ErrorText(it) }
            AppActionButton(
                text = stringResource(Res.string.schedule_add_time),
                onClick = {
                    update(draft.copy(times = draft.times + ScheduleTimeDraft()))
                    rowKeys = rowKeys + nextRowKey++
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("schedule-add-time"),
            )
        }

        SectionTitle(Res.string.schedule_validity)
        AppTextField(
            value = draft.validFrom,
            onValueChange = { update(draft.copy(validFrom = it)) },
            label = stringResource(Res.string.schedule_valid_from),
            errorMessage = if (attempted) validation.errors[ScheduleField.VALID_FROM]?.let { fieldErrorText(it) } else null,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.bringIntoViewRequester(fromRequester).testTag("schedule-valid-from"),
        )
        AppTextField(
            value = draft.validUntil,
            onValueChange = { update(draft.copy(validUntil = it)) },
            label = stringResource(Res.string.schedule_valid_until),
            errorMessage = if (attempted) validation.errors[ScheduleField.VALID_UNTIL]?.let { fieldErrorText(it) } else null,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.bringIntoViewRequester(untilRequester).testTag("schedule-valid-until"),
        )
        if (failed) {
            Box(Modifier.testTag("schedule-save-error")) {
                AppErrorState(message = stringResource(Res.string.schedule_save_failed))
            }
        }
        if (saved) {
            Text(
                stringResource(Res.string.schedule_saved),
                Modifier.testTag("schedule-saved").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        AppSubmitButton(
            text = stringResource(Res.string.schedule_save),
            enabled = !busy,
            isSaving = busy,
            onSubmit = {
                attempted = true
                val request = draft.validate().request
                keyboard?.hide()
                if (request == null) {
                    invalidSubmissions++
                } else {
                    saving = true
                    failed = false
                    saved = false
                    try {
                        onSave(medicationId, request)
                        saved = true
                        onSaved()
                    } catch (_: ApiException) {
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("schedule-save"),
        )
        OutlinedButton(
            onClick = onCancel,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("schedule-cancel"),
        ) { Text(stringResource(Res.string.schedule_cancel)) }
    }
}

@Composable
private fun TimeOfDayPicker(index: Int, selected: TimeOfDay?, enabled: Boolean, onSelect: (TimeOfDay?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("schedule-time-of-day-$index"),
        ) { Text(stringResource(Res.string.schedule_time_of_day, stringResource(periodLabel(selected)))) }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            for (period in listOf(null) + TimeOfDay.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(periodLabel(period))) },
                    onClick = { onSelect(period); expanded = false },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("schedule-period-$index-${period?.name ?: "NONE"}"),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(label: StringResource) {
    Text(stringResource(label), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ErrorText(message: String) {
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun fieldErrorText(error: ScheduleFieldError): String = stringResource(
    when (error) {
        ScheduleFieldError.REQUIRED -> Res.string.schedule_required
        ScheduleFieldError.INVALID_DATE -> Res.string.schedule_invalid_date
        ScheduleFieldError.END_BEFORE_START -> Res.string.schedule_end_before_start
        ScheduleFieldError.INVALID_TIME -> Res.string.schedule_invalid_time
        ScheduleFieldError.DUPLICATE_TIME -> Res.string.schedule_duplicate_time
        ScheduleFieldError.INVALID_WEEKDAY -> Res.string.schedule_invalid_weekday
    },
)

private fun weekdayLabel(day: Int): StringResource = when (day) {
    1 -> Res.string.schedule_monday
    2 -> Res.string.schedule_tuesday
    3 -> Res.string.schedule_wednesday
    4 -> Res.string.schedule_thursday
    5 -> Res.string.schedule_friday
    6 -> Res.string.schedule_saturday
    else -> Res.string.schedule_sunday
}

private fun periodLabel(period: TimeOfDay?): StringResource = when (period) {
    TimeOfDay.MORNING -> Res.string.schedule_morning
    TimeOfDay.NOON -> Res.string.schedule_noon
    TimeOfDay.EVENING -> Res.string.schedule_evening
    TimeOfDay.NIGHT -> Res.string.schedule_night
    TimeOfDay.OTHER -> Res.string.schedule_other
    null -> Res.string.schedule_no_period
}
