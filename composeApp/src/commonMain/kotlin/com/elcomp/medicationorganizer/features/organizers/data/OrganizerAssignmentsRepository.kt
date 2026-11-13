package com.elcomp.medicationorganizer.features.organizers.data

import com.elcomp.medicationorganizer.core.network.ApiClient
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Provisional authenticated assignments API, available through AppServices in MOCK. */
class OrganizerAssignmentsRepository internal constructor(private val api: ApiClient) {
    suspend fun list(): List<UserOrganizer> = api.get(assignmentsPath, requiresSession = true)

    suspend fun get(id: Long): UserOrganizer = api.get("$assignmentsPath/$id", requiresSession = true)

    suspend fun assign(request: AssignOrganizerRequest): UserOrganizer =
        api.post(assignmentsPath, request, requiresSession = true)

    suspend fun update(id: Long, request: UpdateOrganizerAssignment): UserOrganizer =
        api.patch("$assignmentsPath/$id", request, requiresSession = true)

    suspend fun remove(id: Long) = api.delete("$assignmentsPath/$id", requiresSession = true)
}

@Serializable
data class UserOrganizer(
    val id: Long,
    val organizerModelId: Long,
    val displayName: String?,
    val active: Boolean,
    val isDefault: Boolean,
    val assignedAt: Instant,
)

@Serializable
data class AssignOrganizerRequest(val organizerModelId: Long, val displayName: String? = null)

@Serializable
data class UpdateOrganizerAssignment(val displayName: String?, val active: Boolean, val isDefault: Boolean)

private const val assignmentsPath = "/api/users/me/organizers"
