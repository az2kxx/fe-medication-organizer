package com.elcomp.medicationorganizer.features.organizers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.features.organizers.data.AssignOrganizerRequest
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerAssignmentsRepository
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerCatalogRepository
import com.elcomp.medicationorganizer.features.organizers.data.UpdateOrganizerAssignment
import com.elcomp.medicationorganizer.features.organizers.data.UserOrganizer
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal const val AssignmentNameLimit = 32

internal sealed interface OrganizerAssignmentList {
    data object Loading : OrganizerAssignmentList
    data object Failed : OrganizerAssignmentList
    data class Ready(
        val items: List<UserOrganizer>,
        val models: Map<Long, OrganizerModelSummary>,
    ) : OrganizerAssignmentList
}

/** The selected assignment is local to this signed-in composition; active/default come from the API. */
internal class OrganizerAssignmentState(
    private val repository: OrganizerAssignmentsRepository,
    private val catalog: OrganizerCatalogRepository,
    private val selectedId: MutableState<Long?>,
) {
    var list by mutableStateOf<OrganizerAssignmentList>(OrganizerAssignmentList.Loading)
        private set
    var revision by mutableIntStateOf(0)
        private set
    private var refreshing by mutableStateOf(true)
    private var saving by mutableStateOf(false)

    val busy: Boolean get() = refreshing || saving
    val selected: UserOrganizer?
        get() = (list as? OrganizerAssignmentList.Ready)?.items
            ?.firstOrNull { it.id == selectedId.value && it.active }

    fun select(item: UserOrganizer) {
        if (!busy && item.active) selectedId.value = item.id
    }

    fun reload() {
        refreshing = true
        revision++
    }

    suspend fun read() {
        refreshing = true
        try {
            val (items, models) = coroutineScope {
                val items = async { repository.list() }
                val models = async { catalog.models() }
                items.await() to models.await()
            }
            val active = items.filter { it.active }
            if (active.count { it.isDefault } > 1 || items.any { it.isDefault && !it.active }) {
                throw ApiException(ApiError.INVALID_RESPONSE)
            }
            selectedId.value = active.firstOrNull { it.id == selectedId.value }?.id
                ?: active.firstOrNull { it.isDefault }?.id
                ?: active.firstOrNull()?.id
            list = OrganizerAssignmentList.Ready(items, models.associateBy(OrganizerModelSummary::id))
        } catch (_: ApiException) {
            list = OrganizerAssignmentList.Failed
        } finally {
            refreshing = false
        }
    }

    suspend fun assign(modelId: Long, displayName: String?) = change {
        val created = repository.assign(AssignOrganizerRequest(modelId, displayName))
        selectedId.value = created.id
    }

    suspend fun update(item: UserOrganizer, update: UpdateOrganizerAssignment) = change {
        repository.update(item.id, update)
    }

    suspend fun remove(item: UserOrganizer) = change {
        repository.remove(item.id)
    }

    private suspend fun change(write: suspend () -> Unit) {
        if (busy) throw ApiException(ApiError.CONFLICT)
        saving = true
        try {
            write()
        } finally {
            // Read back authoritative flags even if navigation cancelled a write after dispatch.
            reload()
            saving = false
        }
    }
}

@Composable
internal fun rememberOrganizerAssignments(
    repository: OrganizerAssignmentsRepository?,
    catalog: OrganizerCatalogRepository?,
): OrganizerAssignmentState? {
    if (repository == null || catalog == null) return null
    val selectedId = rememberSaveable { mutableStateOf<Long?>(null) }
    val state = remember(repository, catalog, selectedId) { OrganizerAssignmentState(repository, catalog, selectedId) }
    LaunchedEffect(state, state.revision) { state.read() }
    return state
}
