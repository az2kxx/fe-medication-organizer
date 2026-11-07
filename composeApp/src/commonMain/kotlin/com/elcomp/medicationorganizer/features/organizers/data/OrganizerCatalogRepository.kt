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

    /** An unknown code yields an empty list (working assumption, see docs/api-contract.md). */
    suspend fun findByProductCode(productCode: String): List<OrganizerModelSummary> =
        find("productCode", productCode.trim())

    /** Looks up the model printed as a QR code on the organizer. The value is an identifier, never a URL to open. */
    suspend fun findByQrCode(qrCode: String): List<OrganizerModelSummary> = find("qrCode", qrCode)

    suspend fun model(id: Long): OrganizerModelDetails = api.get("/api/organizer-models/$id")

    /** The value is user or camera input, so it is fully encoded: the default keeps `&` and `=`, which split the query. */
    private suspend fun find(parameter: String, value: String): List<OrganizerModelSummary> =
        api.get("/api/organizer-models?$parameter=${value.encodeURLQueryComponent(encodeFull = true)}")
}
