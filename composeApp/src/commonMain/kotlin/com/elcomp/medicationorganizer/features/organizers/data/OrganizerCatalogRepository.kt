package com.elcomp.medicationorganizer.features.organizers.data

import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelSummary
import io.ktor.http.encodeURLQueryComponent

/**
 * Public organizer-model catalog. The backend design marks catalog reads as public, so requests carry no bearer.
 * AppServices exposes this repository only in MOCK until the backend implements the routes.
 */
class OrganizerCatalogRepository internal constructor(private val api: ApiClient) {
    suspend fun models(): List<OrganizerModelSummary> = api.get("/api/organizer-models")

    /**
     * An unknown code yields an empty list (working assumption, see docs/api-contract.md). The code is user input,
     * so it is fully encoded: the default encoding keeps `&` and `=`, which would split the query.
     */
    suspend fun findByProductCode(productCode: String): List<OrganizerModelSummary> =
        api.get("/api/organizer-models?productCode=${productCode.trim().encodeURLQueryComponent(encodeFull = true)}")

    suspend fun model(id: Long): OrganizerModelDetails = api.get("/api/organizer-models/$id")
}
