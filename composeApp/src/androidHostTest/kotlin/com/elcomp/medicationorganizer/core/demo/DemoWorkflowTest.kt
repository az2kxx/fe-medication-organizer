package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class DemoWorkflowTest {
    @Test
    fun medicationSchedulePreparationAndIntakeShareOneDemoState() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            val demo = assertNotNull(services.demo)
            val initialDose = demo.dailyPlan().single()
            val amount = Json.parseToJsonElement("0.125").jsonPrimitive
            val medication = demo.createMedication(CreateMedication("Drugi lek demonstracyjny", amount, "TABLET"))
            val rule = ReplaceSchedule(DEMO_DATE, setOf(1, 3), listOf(ScheduleTime("20:00", TimeOfDay.EVENING)))
            val schedule = demo.replaceSchedule(medication.id, rule)

            assertEquals(2, demo.medications().size)
            val daily = demo.dailyPlan()
            assertEquals(listOf("08:00", "20:00"), daily.map(DoseOccurrence::scheduledTime))
            assertEquals(3, demo.weeklyPlan().size)
            val eveningDose = daily.last()
            assertEquals("0.125", eveningDose.doseAmountSnapshot.content)
            assertEquals(schedule.id, demo.replaceSchedule(medication.id, rule).id)
            assertEquals(daily.map(DoseOccurrence::id), demo.dailyPlan().map(DoseOccurrence::id))

            val preparation = demo.startPreparation(StartPreparation(DEMO_DATE, DEMO_DATE))
            assertEquals(preparation.id, demo.currentPreparation().id)
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                demo.completePreparation(preparation.id)
            }.error)
            val definitions = demo.organizerModel().compartments
            for (dose in daily) {
                val compartmentId = assertNotNull(dose.compartmentDefinitionId)
                assertTrue(definitions.any { it.id == compartmentId })
                val progress = demo.markCompartment(preparation.id, compartmentId, UpdateCompartment(CompartmentStatus.PREPARED))
                assertEquals(compartmentId, progress.compartmentDefinitionId)
                assertNotNull(progress.checkedAt)
            }
            assertEquals(PreparationStatus.READY, demo.completePreparation(preparation.id).status)
            assertTrue(demo.dailyPlan().all { it.status == DoseStatus.PENDING })

            val taken = demo.recordIntake(initialDose.id, RecordIntake(IntakeStatus.TAKEN))
            val skipped = demo.recordIntake(eveningDose.id, RecordIntake(IntakeStatus.SKIPPED))
            assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED), demo.dailyPlan().map(DoseOccurrence::status))
            assertEquals(listOf(taken, skipped), demo.intakeHistory())
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                demo.recordIntake(eveningDose.id, RecordIntake(IntakeStatus.TAKEN))
            }.error)

            demo.replaceSchedule(medication.id, rule.copy(times = listOf(ScheduleTime("21:00", TimeOfDay.EVENING))))
            assertEquals(listOf("08:00", "20:00", "21:00"), demo.dailyPlan().map(DoseOccurrence::scheduledTime))
            assertEquals(listOf(taken, skipped), demo.intakeHistory())

            services.resetDemo()
            assertEquals(initialDose, demo.dailyPlan().single())
            assertEquals(1, demo.medications().size)
            assertTrue(demo.intakeHistory().isEmpty())
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { demo.currentPreparation() }.error)
        } finally {
            services.close()
        }
    }

    @Test
    fun emptyDemoAcceptsMutationsAndCompletionChecksTheCurrentSchedule() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            val demo = assertNotNull(services.demo)
            assertTrue(demo.medications().isEmpty())
            assertTrue(demo.dailyPlan().isEmpty())
            assertTrue(demo.intakeHistory().isEmpty())

            val preparation = demo.startPreparation(StartPreparation(DEMO_DATE, DEMO_DATE))
            for (compartment in preparation.compartments) {
                demo.markCompartment(
                    preparation.id,
                    compartment.compartmentDefinitionId,
                    UpdateCompartment(CompartmentStatus.INTENTIONALLY_EMPTY),
                )
            }
            val medication = demo.createMedication(CreateMedication("Lek w pustym demo", JsonPrimitive(1), "TABLET"))
            demo.replaceSchedule(medication.id, ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("08:00", TimeOfDay.MORNING))))
            val compartmentId = assertNotNull(demo.dailyPlan().single().compartmentDefinitionId)
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                demo.completePreparation(preparation.id)
            }.error)
            demo.markCompartment(preparation.id, compartmentId, UpdateCompartment(CompartmentStatus.PREPARED))
            assertEquals(PreparationStatus.READY, demo.completePreparation(preparation.id).status)

            services.resetDemo()
            assertTrue(demo.medications().isEmpty())
            assertTrue(demo.dailyPlan().isEmpty())
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { demo.currentPreparation() }.error)
        } finally {
            services.close()
        }
    }
}
