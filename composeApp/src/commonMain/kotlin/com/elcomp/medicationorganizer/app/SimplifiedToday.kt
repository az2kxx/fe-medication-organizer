package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.demo.DoseOccurrence
import com.elcomp.medicationorganizer.core.demo.DoseStatus
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppLoadingState
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface DoseState {
    data object Loading : DoseState
    data object Failed : DoseState
    data class Ready(val dose: DoseOccurrence?) : DoseState
}

@Composable
internal fun SimplifiedToday(demo: DemoRepository, organizerId: Long, onOrganizer: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val doseState by produceState<DoseState>(DoseState.Loading, demo, organizerId, retry) {
        value = DoseState.Loading
        value = try {
            DoseState.Ready(demo.dailyPlan(organizerId = organizerId).firstOrNull { it.status == DoseStatus.PENDING })
        } catch (_: ApiException) {
            DoseState.Failed
        }
    }

    when (val current = doseState) {
        DoseState.Loading -> AppLoadingState()
        DoseState.Failed -> AppErrorState(
            message = stringResource(Res.string.simplified_plan_failed),
            onRetry = { retry++ },
        )
        is DoseState.Ready -> Surface(
            modifier = Modifier.fillMaxWidth().testTag("simplified-dose"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val dose = current.dose
                Text(
                    stringResource(
                        if (dose == null) Res.string.simplified_no_dose else Res.string.simplified_next_dose,
                    ),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (dose != null) {
                    Text(dose.scheduledTime, style = MaterialTheme.typography.headlineLarge)
                    Text(dose.medicationName, style = MaterialTheme.typography.bodyLarge)
                }
                AppActionButton(
                    text = stringResource(Res.string.today_open_organizer),
                    onClick = onOrganizer,
                    modifier = Modifier.fillMaxWidth().testTag("today-open-organizer"),
                )
            }
        }
    }
}
