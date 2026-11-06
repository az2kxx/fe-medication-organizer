package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.features.organizers.data.dto.CompartmentDefinitionResponse
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails

const val DEMO_MODEL_7X3_ID = 2L
const val DEMO_MODEL_DAILY_ID = 3L
const val DEMO_MODEL_INACTIVE_ID = 4L
const val DEMO_MODEL_7X3_PRODUCT_CODE = "ELC-7X3"

/** Fictional organizer-model catalog shared by the MOCK catalog routes and the demo plan. */
internal object DemoCatalog {
    private val dayLabels = listOf("Pon.", "Wt.", "Śr.", "Czw.", "Pt.", "Sob.", "Niedz.")
    private val timeLabels = mapOf(
        TimeOfDay.MORNING to "rano",
        TimeOfDay.NOON to "południe",
        TimeOfDay.EVENING to "wieczór",
        TimeOfDay.NIGHT to "noc",
    )

    /** The demo plan and preparation use this model, so its 7×2 layout and compartment ids must stay stable. */
    val demoModel = weekly(
        id = DEMO_MODEL_ID,
        productCode = "ELC-7X2-DEMO",
        name = "Kasetka demonstracyjna",
        description = "Tygodniowa kasetka z przegródkami na rano i wieczór.",
        times = listOf(TimeOfDay.MORNING, TimeOfDay.EVENING),
        firstCompartmentId = 1,
    )

    val models: List<OrganizerModelDetails> = listOf(
        demoModel,
        weekly(
            id = DEMO_MODEL_7X3_ID,
            productCode = DEMO_MODEL_7X3_PRODUCT_CODE,
            name = "Kasetka tygodniowa 7×3",
            description = "Siedem dni, po trzy przegródki: rano, w południe i wieczorem.",
            times = listOf(TimeOfDay.MORNING, TimeOfDay.NOON, TimeOfDay.EVENING),
            firstCompartmentId = 101,
        ),
        OrganizerModelDetails(
            id = DEMO_MODEL_DAILY_ID,
            productCode = "ELC-1X4",
            name = "Kasetka dzienna 4 pory",
            description = "Jeden dzień z czterema przegródkami do wielokrotnego napełniania.",
            daysCovered = 1,
            compartmentsPerDay = 4,
            active = true,
            compartments = listOf(TimeOfDay.MORNING, TimeOfDay.NOON, TimeOfDay.EVENING, TimeOfDay.NIGHT)
                .mapIndexed { index, time ->
                    CompartmentDefinitionResponse(201L + index, index, timeLabels.getValue(time), null, time)
                },
        ),
        weekly(
            id = DEMO_MODEL_INACTIVE_ID,
            productCode = "ELC-7X1-OLD",
            name = "Kasetka tygodniowa 7×1 (wycofana)",
            description = "Model wycofany ze sprzedaży. Nie można go przypisać ponownie.",
            times = listOf(TimeOfDay.MORNING),
            firstCompartmentId = 301,
            active = false,
        ),
    )

    fun model(id: Long): OrganizerModelDetails? = models.firstOrNull { it.id == id }

    fun findByProductCode(code: String): List<OrganizerModelDetails> {
        val normalized = code.trim()
        return models.filter { it.productCode.equals(normalized, ignoreCase = true) }
    }

    private fun weekly(
        id: Long,
        productCode: String,
        name: String,
        description: String,
        times: List<TimeOfDay>,
        firstCompartmentId: Long,
        active: Boolean = true,
    ) = OrganizerModelDetails(
        id = id,
        productCode = productCode,
        name = name,
        description = description,
        daysCovered = 7,
        compartmentsPerDay = times.size,
        active = active,
        compartments = (1..7).flatMap { day ->
            times.mapIndexed { index, time ->
                val position = (day - 1) * times.size + index
                CompartmentDefinitionResponse(
                    id = firstCompartmentId + position,
                    positionIndex = position,
                    label = "${dayLabels[day - 1]} ${timeLabels.getValue(time)}",
                    dayOfWeek = day,
                    timeOfDay = time,
                )
            }
        },
    )
}
