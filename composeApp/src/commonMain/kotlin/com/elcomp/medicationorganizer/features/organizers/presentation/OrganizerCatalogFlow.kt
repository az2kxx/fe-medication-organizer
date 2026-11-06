package com.elcomp.medicationorganizer.features.organizers.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppEmptyState
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerCatalogRepository
import com.elcomp.medicationorganizer.features.organizers.data.dto.CompartmentDefinitionResponse
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelSummary
import com.elcomp.medicationorganizer.features.organizers.domain.layoutRows
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Failed : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

/**
 * Catalog → model details → confirmation. [onAssign] saves the assignment (FE-09). While it is null the
 * confirmation step explains that saving is not available yet.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OrganizerCatalogFlow(
    repository: OrganizerCatalogRepository,
    onAssign: (suspend (OrganizerModelDetails) -> Unit)? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf<String?>(null) }

    fun backToCatalog() {
        confirming = false
        selectedId = null
    }

    BackHandler(enabled = selectedId != null) {
        if (confirming) confirming = false else backToCatalog()
    }

    when (val id = selectedId) {
        null -> CatalogStep(
            repository = repository,
            query = query,
            onSearch = { query = it },
            onOpen = { selectedId = it },
        )
        else -> ModelStep(
            repository = repository,
            modelId = id,
            confirming = confirming,
            onSelect = { confirming = true },
            onBack = ::backToCatalog,
            onAssign = onAssign,
        )
    }
}

@Composable
private fun CatalogStep(
    repository: OrganizerCatalogRepository,
    query: String?,
    onSearch: (String?) -> Unit,
    onOpen: (Long) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf(query.orEmpty()) }
    var codeMissing by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<OrganizerModelSummary>>>(LoadState.Loading) }
    LaunchedEffect(query, retry) {
        state = LoadState.Loading
        state = try {
            LoadState.Ready(if (query == null) repository.models() else repository.findByProductCode(query))
        } catch (_: ApiException) {
            LoadState.Failed
        }
    }

    Text(stringResource(Res.string.organizer_catalog_intro), style = MaterialTheme.typography.bodyLarge)
    AppTextField(
        value = code,
        onValueChange = {
            code = it
            codeMissing = false
        },
        label = stringResource(Res.string.organizer_catalog_code_label),
        errorMessage = if (codeMissing) stringResource(Res.string.organizer_catalog_code_required) else null,
        modifier = Modifier.testTag("organizer-code"),
    )
    AppActionButton(
        text = stringResource(Res.string.organizer_catalog_search),
        onClick = {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) codeMissing = true else onSearch(trimmed)
        },
        modifier = Modifier.fillMaxWidth().testTag("organizer-search"),
    )
    if (query != null) {
        SecondaryButton(Res.string.organizer_catalog_show_all, "organizer-show-all") {
            code = ""
            codeMissing = false
            onSearch(null)
        }
    }

    when (val current = state) {
        LoadState.Loading -> AppLoadingState()
        LoadState.Failed -> AppErrorState(
            message = stringResource(Res.string.organizer_catalog_failed),
            onRetry = { retry++ },
            modifier = Modifier.testTag("organizer-catalog-error"),
        )
        is LoadState.Ready -> when {
            current.value.isEmpty() && query != null -> AppEmptyState(
                title = stringResource(Res.string.organizer_search_empty_title),
                message = stringResource(Res.string.organizer_search_empty_message, query),
                modifier = Modifier.testTag("organizer-search-empty"),
            )
            current.value.isEmpty() -> AppEmptyState(
                title = stringResource(Res.string.organizer_catalog_empty_title),
                message = stringResource(Res.string.organizer_catalog_empty_message),
            )
            else -> {
                if (query != null) {
                    Text(
                        stringResource(Res.string.organizer_catalog_results, query),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                current.value.forEach { model -> ModelCard(model) { onOpen(model.id) } }
            }
        }
    }
}

@Composable
private fun ModelCard(model: OrganizerModelSummary, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("organizer-model-${model.id}")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.organizer_product_code, model.productCode))
            LayoutSummary(model.daysCovered, model.compartmentsPerDay)
            if (!model.active) InactiveNote()
        }
    }
}

@Composable
private fun ModelStep(
    repository: OrganizerCatalogRepository,
    modelId: Long,
    confirming: Boolean,
    onSelect: () -> Unit,
    onBack: () -> Unit,
    onAssign: (suspend (OrganizerModelDetails) -> Unit)?,
) {
    var retry by remember { mutableIntStateOf(0) }
    var state by remember(modelId) { mutableStateOf<LoadState<OrganizerModelDetails>>(LoadState.Loading) }
    LaunchedEffect(modelId, retry) {
        state = LoadState.Loading
        state = try {
            LoadState.Ready(repository.model(modelId))
        } catch (_: ApiException) {
            LoadState.Failed
        }
    }

    when (val current = state) {
        LoadState.Loading -> AppLoadingState()
        LoadState.Failed -> AppErrorState(
            message = stringResource(Res.string.organizer_details_failed),
            onRetry = { retry++ },
        )
        is LoadState.Ready -> if (confirming && current.value.active) {
            ConfirmContent(current.value, onAssign)
        } else {
            DetailsContent(current.value, onSelect)
        }
    }
    SecondaryButton(
        if (confirming) Res.string.organizer_change_model else Res.string.organizer_back_catalog,
        "organizer-back-catalog",
        onBack,
    )
}

@Composable
private fun DetailsContent(model: OrganizerModelDetails, onSelect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("organizer-details"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModelHeader(model)
        model.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (!model.active) InactiveNote()
        Text(
            stringResource(Res.string.organizer_layout_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        OrganizerLayoutGrid(model)
        AppActionButton(
            text = stringResource(Res.string.organizer_select),
            onClick = onSelect,
            enabled = model.active,
            modifier = Modifier.fillMaxWidth().testTag("organizer-select"),
        )
    }
}

@Composable
private fun ConfirmContent(model: OrganizerModelDetails, onAssign: (suspend (OrganizerModelDetails) -> Unit)?) {
    var failed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("organizer-confirm"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.organizer_confirm_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(Res.string.organizer_confirm_message), style = MaterialTheme.typography.bodyLarge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ModelHeader(model)
            }
        }
        if (onAssign == null) {
            Text(
                stringResource(Res.string.organizer_assign_unavailable),
                modifier = Modifier.testTag("organizer-assign-unavailable"),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            if (failed) AppErrorState(message = stringResource(Res.string.organizer_assign_failed))
            AppSubmitButton(
                text = stringResource(Res.string.organizer_assign),
                onSubmit = {
                    failed = false
                    try {
                        onAssign(model)
                    } catch (_: ApiException) {
                        failed = true
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("organizer-assign"),
            )
        }
    }
}

@Composable
private fun ModelHeader(model: OrganizerModelDetails) {
    Text(
        model.name,
        modifier = Modifier.semantics { heading() }.testTag("organizer-model-name"),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(stringResource(Res.string.organizer_product_code, model.productCode))
    LayoutSummary(model.daysCovered, model.compartmentsPerDay)
    Text(stringResource(Res.string.organizer_compartment_count, model.compartments.size))
}

@Composable
private fun OrganizerLayoutGrid(model: OrganizerModelDetails) {
    val rows = model.layoutRows()
    if (rows.isEmpty()) {
        Text(stringResource(Res.string.organizer_layout_empty), style = MaterialTheme.typography.bodyLarge)
        return
    }
    rows.forEach { row ->
        Column(
            modifier = Modifier.fillMaxWidth().testTag("organizer-layout-row"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val rowTitle = row.dayOfWeek?.let { dayName(it) } ?: stringResource(Res.string.organizer_layout_row)
            Text(rowTitle, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.compartments.forEach { compartment ->
                    CompartmentCell(compartment, rowDay = row.dayOfWeek)
                }
            }
        }
    }
}

/** What a compartment cell shows and announces. The physical label always leads; day and time add context. */
internal data class CompartmentText(val label: String, val context: String?, val description: String)

/** [rowHasDay] hides the day from the visible context when the row heading already shows it. */
internal fun compartmentText(label: String, day: String?, time: String?, rowHasDay: Boolean): CompartmentText =
    CompartmentText(
        label = label,
        context = listOfNotNull(day.takeUnless { rowHasDay }, time).joinToString(", ").ifEmpty { null },
        description = listOfNotNull(label, day, time).joinToString(", "),
    )

@Composable
private fun CompartmentCell(compartment: CompartmentDefinitionResponse, rowDay: Int?) {
    val text = compartmentText(
        label = compartment.label,
        day = compartment.dayOfWeek?.let { dayName(it) },
        time = compartment.timeOfDay?.let { timeName(it) },
        rowHasDay = rowDay != null,
    )
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("organizer-compartment-${compartment.id}")
            .clearAndSetSemantics { contentDescription = text.description },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text.label, style = MaterialTheme.typography.titleMedium)
            text.context?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LayoutSummary(daysCovered: Int?, compartmentsPerDay: Int?) {
    if (daysCovered != null && compartmentsPerDay != null) {
        Text(stringResource(Res.string.organizer_layout_summary, daysCovered, compartmentsPerDay))
    }
}

@Composable
private fun InactiveNote() {
    Text(
        stringResource(Res.string.organizer_inactive),
        modifier = Modifier.testTag("organizer-inactive"),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun SecondaryButton(text: StringResource, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun dayName(day: Int): String? = when (day) {
    1 -> Res.string.day_1
    2 -> Res.string.day_2
    3 -> Res.string.day_3
    4 -> Res.string.day_4
    5 -> Res.string.day_5
    6 -> Res.string.day_6
    7 -> Res.string.day_7
    else -> null
}?.let { stringResource(it) }

@Composable
private fun timeName(time: TimeOfDay): String = stringResource(
    when (time) {
        TimeOfDay.MORNING -> Res.string.time_of_day_morning
        TimeOfDay.NOON -> Res.string.time_of_day_noon
        TimeOfDay.EVENING -> Res.string.time_of_day_evening
        TimeOfDay.NIGHT -> Res.string.time_of_day_night
        TimeOfDay.OTHER -> Res.string.time_of_day_other
    },
)
