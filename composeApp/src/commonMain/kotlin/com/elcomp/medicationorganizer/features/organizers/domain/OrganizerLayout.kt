package com.elcomp.medicationorganizer.features.organizers.domain

import com.elcomp.medicationorganizer.features.organizers.data.dto.CompartmentDefinitionResponse
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails

/** One row of the physical organizer. [dayOfWeek] is set when every compartment in the row shares it. */
data class OrganizerLayoutRow(val dayOfWeek: Int?, val compartments: List<CompartmentDefinitionResponse>)

/**
 * Orders compartments by [CompartmentDefinitionResponse.positionIndex] and splits them into rows.
 * Rows follow `compartmentsPerDay` when the model declares it, otherwise consecutive compartments with the same
 * day form a row. A model without either hint is shown as a single row.
 */
fun OrganizerModelDetails.layoutRows(): List<OrganizerLayoutRow> {
    val ordered = compartments.sortedBy(CompartmentDefinitionResponse::positionIndex)
    if (ordered.isEmpty()) return emptyList()
    val perRow = compartmentsPerDay?.takeIf { it > 0 }
    val chunks = if (perRow != null) {
        ordered.chunked(perRow)
    } else {
        ordered.fold(mutableListOf<MutableList<CompartmentDefinitionResponse>>()) { rows, compartment ->
            val last = rows.lastOrNull()
            if (last != null && last.first().dayOfWeek == compartment.dayOfWeek) {
                last.add(compartment)
            } else {
                rows.add(mutableListOf(compartment))
            }
            rows
        }
    }
    return chunks.map { row ->
        val day = row.first().dayOfWeek
        OrganizerLayoutRow(day.takeIf { row.all { it.dayOfWeek == day } }, row)
    }
}
