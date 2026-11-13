package com.elcomp.medicationorganizer.features.organizers.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.demo.Preparation
import com.elcomp.medicationorganizer.core.demo.PreparationStatus
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppEmptyState
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerCatalogRepository
import com.elcomp.medicationorganizer.features.organizers.data.UpdateOrganizerAssignment
import com.elcomp.medicationorganizer.features.organizers.data.UserOrganizer
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun assignmentName(item: UserOrganizer, modelName: String?): String =
    item.displayName ?: modelName ?: stringResource(Res.string.assignment_unnamed)

@Composable
internal fun AssignmentListStatus(state: OrganizerAssignmentState) {
    when (state.list) {
        OrganizerAssignmentList.Loading -> AppLoadingState()
        OrganizerAssignmentList.Failed -> AppErrorState(
            message = stringResource(Res.string.assignment_load_failed),
            onRetry = state::reload,
        )
        is OrganizerAssignmentList.Ready -> Unit
    }
}

@Composable
internal fun NoSelectedAssignment() {
    AppEmptyState(
        title = stringResource(Res.string.assignment_no_selection_title),
        message = stringResource(Res.string.assignment_no_selection_message),
    )
}

@Composable
internal fun OrganizerAssignmentListContent(
    state: OrganizerAssignmentState,
    onAdd: () -> Unit,
    onSelected: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val list = state.list as? OrganizerAssignmentList.Ready
    if (list == null) {
        AssignmentListStatus(state)
        return
    }

    if (list.items.isEmpty()) {
        AppEmptyState(
            title = stringResource(Res.string.assignment_list_empty_title),
            message = stringResource(Res.string.assignment_list_empty_message),
        )
    }
    AppActionButton(
        text = stringResource(Res.string.assignment_add),
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().testTag("assignments-add"),
    )
    list.items.forEach { item ->
        key(item.id) {
            val selected = state.selected?.id == item.id
            val model = list.models[item.organizerModelId]
            AssignmentCard(
                name = assignmentName(item, model?.name),
                item = item,
                selected = selected,
                modifier = Modifier.testTag("assignment-${item.id}"),
            ) {
                if (model != null && item.displayName != null) {
                    Text(stringResource(Res.string.assignment_model, model.name))
                }
                if (item.active && !selected) {
                    AppActionButton(
                        text = stringResource(Res.string.assignment_use),
                        enabled = !state.busy,
                        onClick = {
                            state.select(item)
                            onSelected()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("assignment-use-${item.id}"),
                    )
                }
                AssignmentSecondaryButton(
                    Res.string.assignment_edit,
                    "assignment-edit-${item.id}",
                    enabled = !state.busy,
                    onClick = { onEdit(item.id) },
                )
            }
        }
    }
}

@Composable
internal fun OrganizerAssignmentDetailsContent(
    state: OrganizerAssignmentState,
    assignmentId: Long?,
    onRemoved: () -> Unit,
) {
    val list = state.list as? OrganizerAssignmentList.Ready
    if (list == null) {
        AssignmentListStatus(state)
        return
    }
    val item = list.items.firstOrNull { it.id == assignmentId }
    if (item == null) {
        AppEmptyState(
            title = stringResource(Res.string.assignment_missing_title),
            message = stringResource(Res.string.assignment_missing_message),
        )
        return
    }

    key(item.id) {
        val modelName = list.models[item.organizerModelId]?.name
        val name = assignmentName(item, modelName)
        var draft by rememberSaveable(item.displayName) { mutableStateOf(item.displayName.orEmpty()) }
        var confirmRemove by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf<ApiError?>(null) }
        var removeError by remember { mutableStateOf<ApiError?>(null) }
        val normalized = draft.trim().ifEmpty { null }
        val nameTooLong = (normalized?.length ?: 0) > AssignmentNameLimit

        suspend fun update(request: UpdateOrganizerAssignment) {
            error = null
            try {
                state.update(item, request)
            } catch (failure: ApiException) {
                error = failure.error
            }
        }

        AssignmentCard(name, item, state.selected?.id == item.id, Modifier.testTag("assignment-details")) {
            if (modelName != null && item.displayName != null) {
                Text(stringResource(Res.string.assignment_model, modelName))
            }
        }
        AppTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(Res.string.assignment_name),
            modifier = Modifier.testTag("assignment-name"),
            errorMessage = if (nameTooLong) stringResource(Res.string.assignment_name_too_long) else null,
            enabled = !state.busy,
        )
        error?.let { AssignmentMutationError(it, Res.string.assignment_save_failed) }
        AppSubmitButton(
            text = stringResource(Res.string.assignment_save_name),
            enabled = !state.busy && !nameTooLong && normalized != item.displayName,
            onSubmit = { update(UpdateOrganizerAssignment(normalized, item.active, item.isDefault)) },
            modifier = Modifier.fillMaxWidth().testTag("assignment-save-name"),
        )
        if (item.active && !item.isDefault) {
            AppSubmitButton(
                text = stringResource(Res.string.assignment_make_default),
                enabled = !state.busy,
                onSubmit = { update(UpdateOrganizerAssignment(item.displayName, active = true, isDefault = true)) },
                modifier = Modifier.fillMaxWidth().testTag("assignment-set-default"),
            )
        }
        AppSubmitButton(
            text = stringResource(
                if (item.active) Res.string.assignment_deactivate else Res.string.assignment_activate,
            ),
            enabled = !state.busy,
            onSubmit = { update(UpdateOrganizerAssignment(item.displayName, active = !item.active, isDefault = false)) },
            modifier = Modifier.fillMaxWidth().testTag("assignment-toggle-active"),
        )
        AssignmentSecondaryButton(Res.string.assignment_remove, "assignment-remove", enabled = !state.busy) {
            removeError = null
            confirmRemove = true
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { if (!state.busy) confirmRemove = false },
                title = { Text(stringResource(Res.string.assignment_remove)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(Res.string.assignment_remove_message, name))
                        removeError?.let { AssignmentMutationError(it, Res.string.assignment_remove_failed) }
                    }
                },
                dismissButton = {
                    AssignmentSecondaryButton(Res.string.assignment_cancel, "assignment-remove-cancel", !state.busy) {
                        confirmRemove = false
                    }
                },
                confirmButton = {
                    AppSubmitButton(
                        text = stringResource(Res.string.assignment_remove),
                        enabled = !state.busy,
                        onSubmit = {
                            removeError = null
                            try {
                                state.remove(item)
                                confirmRemove = false
                                onRemoved()
                            } catch (failure: ApiException) {
                                removeError = failure.error
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("assignment-remove-confirm"),
                    )
                },
            )
        }
    }
}

@Composable
private fun AssignmentCard(
    name: String,
    item: UserOrganizer,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            val active = stringResource(if (item.active) Res.string.assignment_active else Res.string.assignment_inactive)
            val default = if (item.isDefault) stringResource(Res.string.assignment_default) else null
            val inUse = if (selected) stringResource(Res.string.assignment_in_use) else null
            Text(listOfNotNull(inUse, active, default).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
internal fun SelectedOrganizerModel(
    selected: UserOrganizer,
    catalog: OrganizerCatalogRepository,
    modelName: String?,
) {
    key(selected.id, selected.organizerModelId) {
        var retry by remember { mutableIntStateOf(0) }
        var state by remember { mutableStateOf<AssignmentRead<OrganizerModelDetails>>(AssignmentRead.Loading) }
        LaunchedEffect(catalog, retry) {
            state = AssignmentRead.Loading
            state = try {
                AssignmentRead.Ready(catalog.model(selected.organizerModelId))
            } catch (_: ApiException) {
                AssignmentRead.Failed
            }
        }
        AssignmentCard(
            assignmentName(selected, modelName),
            selected,
            selected = true,
            modifier = Modifier.testTag("selected-assignment-${selected.id}"),
        ) {
            when (val current = state) {
                AssignmentRead.Loading -> AppLoadingState()
                AssignmentRead.Failed -> AppErrorState(
                    message = stringResource(Res.string.organizer_details_failed),
                    onRetry = { retry++ },
                )
                is AssignmentRead.Ready -> {
                    val model = current.value
                    Text(stringResource(Res.string.assignment_model, model.name))
                    Text(stringResource(Res.string.organizer_product_code, model.productCode))
                    LayoutSummary(model.daysCovered, model.compartmentsPerDay)
                    Text(stringResource(Res.string.organizer_compartment_count, model.compartments.size))
                }
            }
        }
    }
}

@Composable
internal fun SelectedOrganizerPreparation(
    selected: UserOrganizer,
    demo: DemoRepository,
    modelName: String?,
) {
    key(selected.id) {
        var retry by remember { mutableIntStateOf(0) }
        var state by remember { mutableStateOf<AssignmentRead<Preparation?>>(AssignmentRead.Loading) }
        LaunchedEffect(demo, retry) {
            state = AssignmentRead.Loading
            state = try {
                AssignmentRead.Ready(demo.currentPreparation(organizerId = selected.id))
            } catch (failure: ApiException) {
                if (failure.error == ApiError.NOT_FOUND) AssignmentRead.Ready(null) else AssignmentRead.Failed
            }
        }
        Text(
            stringResource(Res.string.assignment_preparation_for, assignmentName(selected, modelName)),
            modifier = Modifier.testTag("preparation-assignment-${selected.id}"),
            style = MaterialTheme.typography.bodyLarge,
        )
        when (val current = state) {
            AssignmentRead.Loading -> AppLoadingState()
            AssignmentRead.Failed -> AppErrorState(
                message = stringResource(Res.string.assignment_preparation_load_failed),
                onRetry = { retry++ },
            )
            is AssignmentRead.Ready -> if (current.value == null) {
                AppEmptyState(
                    title = stringResource(Res.string.preparation_empty_title),
                    message = stringResource(Res.string.preparation_empty_message),
                )
            } else {
                Text(
                    stringResource(
                        if (current.value.status == PreparationStatus.READY) Res.string.assignment_preparation_ready
                        else Res.string.assignment_preparation_in_progress,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private sealed interface AssignmentRead<out T> {
    data object Loading : AssignmentRead<Nothing>
    data object Failed : AssignmentRead<Nothing>
    data class Ready<T>(val value: T) : AssignmentRead<T>
}

@Composable
internal fun AssignmentMutationError(error: ApiError, fallback: StringResource) {
    AppErrorState(
        message = stringResource(if (error == ApiError.CONFLICT) Res.string.assignment_conflict else fallback),
        modifier = Modifier.testTag("assignment-mutation-error"),
    )
}

@Composable
private fun AssignmentSecondaryButton(
    label: StringResource,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    }
}
