package com.elcomp.medicationorganizer.features.organizers.data.dto

import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import kotlinx.serialization.Serializable

/**
 * Catalog entry from `GET /api/organizer-models`. Fields follow the backend design document. The list shape
 * and the absence of compartments in list items are working assumptions until the backend implements the route.
 */
@Serializable
data class OrganizerModelSummary(
    val id: Long,
    val productCode: String,
    val name: String,
    val description: String? = null,
    val daysCovered: Int? = null,
    val compartmentsPerDay: Int? = null,
    val active: Boolean,
)

/** Model with its compartment layout from `GET /api/organizer-models/{id}`. */
@Serializable
data class OrganizerModelDetails(
    val id: Long,
    val productCode: String,
    val name: String,
    val description: String? = null,
    val daysCovered: Int? = null,
    val compartmentsPerDay: Int? = null,
    val active: Boolean,
    val compartments: List<CompartmentDefinitionResponse>,
)

/** `dayOfWeek` uses ISO numbering (Monday = 1). Both it and `timeOfDay` are nullable in the backend design. */
@Serializable
data class CompartmentDefinitionResponse(
    val id: Long,
    val positionIndex: Int,
    val label: String,
    val dayOfWeek: Int? = null,
    val timeOfDay: TimeOfDay? = null,
)

fun OrganizerModelDetails.toSummary() = OrganizerModelSummary(
    id = id,
    productCode = productCode,
    name = name,
    description = description,
    daysCovered = daysCovered,
    compartmentsPerDay = compartmentsPerDay,
    active = active,
)
