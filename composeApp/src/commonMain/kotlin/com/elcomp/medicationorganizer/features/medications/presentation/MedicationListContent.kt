package com.elcomp.medicationorganizer.features.medications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.demo.Medication
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppEmptyState
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface MedicationListState {
    data object Loading : MedicationListState
    data object Failed : MedicationListState
    data class Loaded(val medications: List<Medication>) : MedicationListState
}

@Composable
internal fun MedicationListContent(repository: DemoRepository, revision: Long = 0, onOpen: (Long) -> Unit = {}, onAdd: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val list by produceState<MedicationListState>(MedicationListState.Loading, repository, retry, revision) {
        value = MedicationListState.Loading
        value = try {
            MedicationListState.Loaded(repository.medications())
        } catch (_: ApiException) {
            MedicationListState.Failed
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("medications-list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppActionButton(
            text = stringResource(Res.string.medication_add_title),
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().testTag("medications-add"),
        )
        when (val current = list) {
            MedicationListState.Loading -> AppLoadingState()
            MedicationListState.Failed -> AppErrorState(
                message = stringResource(Res.string.medications_list_failed),
                modifier = Modifier.testTag("medications-load-error"),
                onRetry = { retry++ },
            )
            is MedicationListState.Loaded -> if (current.medications.isEmpty()) {
                AppEmptyState(
                    title = stringResource(Res.string.medications_empty_title),
                    message = stringResource(Res.string.medications_empty_message),
                    modifier = Modifier.testTag("medications-empty"),
                )
            } else {
                current.medications.forEach { medication ->
                    key(medication.id) {
                        OutlinedCard(onClick = { onOpen(medication.id) }, modifier = Modifier.fillMaxWidth().testTag("medication-item-${medication.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                medication.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(statusLabel(medication.status)))
                            Text(stringResource(Res.string.medication_details_action), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
