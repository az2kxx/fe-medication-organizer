package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.features.organizers.data.AssignOrganizerRequest
import com.elcomp.medicationorganizer.features.organizers.data.UpdateOrganizerAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class MedicationScheduleWorkflowTest {
    @Test
    fun detailsEditingAndEveryStatusTransitionPreserveEarlierDoseSnapshotsAndHistory() = runBlocking<Unit> {
        val services = createServices()
        try {
            val demo = assertNotNull(services.demo)
            val request = CreateMedication(
                "Płyn demonstracyjny", JsonPrimitive(0.25), "ml", DEMO_DATE, "2026-12-31",
                JsonPrimitive(12.5), "mg/ml", MedicationForm.LIQUID,
            )
            val medication = demo.createMedication(request)
            val details = demo.medication(medication.id)
            assertEquals(request.name, details.name)
            assertEquals(request.doseAmount, details.doseAmount)
            assertEquals(request.doseUnit, details.doseUnit)
            assertEquals(request.strengthValue, details.strengthValue)
            assertEquals(request.strengthUnit, details.strengthUnit)
            assertEquals(request.form, details.form)
            assertEquals(request.startDate, details.startDate)
            assertEquals(request.endDate, details.endDate)
            assertNull(details.userOrganizerId)
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { demo.currentSchedule(medication.id) }.error)

            demo.replaceSchedule(medication.id, rule())
            val earlier = demo.dailyPlan().single()
            val record = demo.recordIntake(earlier.id, RecordIntake(IntakeStatus.TAKEN))
            val oldFuture = demo.dailyPlan("2026-11-03").single()
            demo.updateMedication(
                medication.id,
                UpdateMedication("Zmieniona nazwa", JsonPrimitive(0.5), "ml", DEMO_DATE),
            )
            val edited = demo.medication(medication.id)
            assertEquals("Zmieniona nazwa", edited.name)
            assertEquals(JsonPrimitive(0.5), edited.doseAmount)
            assertNull(edited.endDate)
            assertNull(edited.strengthValue)
            assertNull(edited.strengthUnit)
            assertNull(edited.form)
            assertEquals(earlier.copy(status = DoseStatus.TAKEN), demo.dailyPlan().single())
            val newFuture = demo.dailyPlan("2026-11-03").single()
            assertNotEquals(oldFuture.id, newFuture.id)
            assertEquals(JsonPrimitive(0.5), newFuture.doseAmountSnapshot)
            assertEquals(edited.name, newFuture.medicationName)

            for (status in listOf(MedicationStatus.SUSPENDED, MedicationStatus.INACTIVE)) {
                demo.updateMedicationStatus(medication.id, UpdateMedicationStatus(status))
                assertEquals(status, demo.medication(medication.id).status)
                assertTrue(demo.dailyPlan("2026-11-03").isEmpty())
                assertEquals(listOf(record), demo.intakeHistory(DEMO_DATE, "2026-11-03"))
                assertEquals(earlier.copy(status = DoseStatus.TAKEN), demo.dailyPlan().single())
            }
            demo.updateMedicationStatus(medication.id, UpdateMedicationStatus(MedicationStatus.ACTIVE))
            assertEquals(JsonPrimitive(0.5), demo.dailyPlan("2026-11-03").single().doseAmountSnapshot)
            assertEquals(listOf(record), demo.intakeHistory(DEMO_DATE, "2026-11-03"))
        } finally {
            services.close()
        }
    }

    @Test
    fun futureOrganizerChangePreservesEarlierMappingAndConfirmedDose() = runBlocking<Unit> {
        val services = createServices()
        try {
            val demo = assertNotNull(services.demo)
            val second = assertNotNull(services.assignments).assign(AssignOrganizerRequest(DEMO_MODEL_7X3_ID, "Druga"))
            val medication = demo.createMedication(medicine())
            demo.replaceSchedule(medication.id, rule())
            val earlier = demo.dailyPlan().single()
            val record = demo.recordIntake(earlier.id, RecordIntake(IntakeStatus.TAKEN))
            val staleFuture = demo.dailyPlan("2026-11-03").single()
            val moved = demo.replaceSchedule(medication.id, rule().copy(validFrom = "2026-11-03", userOrganizerId = second.id))

            assertEquals(moved, demo.currentSchedule(medication.id))
            assertEquals(second.id, demo.medication(medication.id).userOrganizerId)
            assertTrue(demo.dailyPlan("2026-11-03", 1).isEmpty())
            val newFuture = demo.dailyPlan("2026-11-03", second.id).single()
            assertEquals(second.id, newFuture.userOrganizerId)
            assertNotEquals(staleFuture.id, newFuture.id)
            assertEquals(earlier.copy(status = DoseStatus.TAKEN), demo.dailyPlan().single())
            assertEquals(listOf(record), demo.intakeHistory(DEMO_DATE, "2026-11-03"))

            val unqueried = demo.createMedication(medicine("Drugi lek"))
            demo.replaceSchedule(unqueried.id, rule())
            demo.replaceSchedule(unqueried.id, rule().copy(validFrom = "2026-11-03", userOrganizerId = second.id))
            assertTrue(demo.dailyPlan().any { it.medicationId == unqueried.id && it.userOrganizerId == 1L })
            assertTrue(demo.dailyPlan(DEMO_DATE, second.id).isEmpty())
        } finally {
            services.close()
        }
    }

    @Test
    fun aPastUnassignedRuleDoesNotAcquireAFutureOrganizerRetroactively() = runBlocking<Unit> {
        val services = createServices()
        try {
            val assignments = assertNotNull(services.assignments)
            assignments.update(1, UpdateOrganizerAssignment(null, active = false, isDefault = false))
            val demo = assertNotNull(services.demo)
            val medication = demo.createMedication(medicine())
            val unassigned = demo.replaceSchedule(medication.id, rule().copy(userOrganizerId = null))
            assertNull(unassigned.userOrganizerId)
            val newOrganizer = assignments.assign(AssignOrganizerRequest(DEMO_MODEL_7X3_ID, "Nowa"))
            demo.replaceSchedule(medication.id, rule().copy(validFrom = "2026-11-03", userOrganizerId = newOrganizer.id))
            assertTrue(demo.dailyPlan(DEMO_DATE, newOrganizer.id).isEmpty())
            assertEquals(newOrganizer.id, demo.dailyPlan("2026-11-03", newOrganizer.id).single().userOrganizerId)
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                demo.replaceSchedule(medication.id, rule())
            }.error)
        } finally {
            services.close()
        }
    }

    @Test
    fun mutationRevisionRefreshesOtherScreensButReadsAndRejectedRequestsDoNotEmit() = runBlocking<Unit> {
        val services = createServices()
        try {
            val demo = assertNotNull(services.demo)
            val before = services.demoRevision.value
            val medication = demo.createMedication(medicine())
            assertEquals(before + 1, services.demoRevision.value)
            demo.medication(medication.id)
            demo.medications()
            assertEquals(before + 1, services.demoRevision.value)
            demo.replaceSchedule(medication.id, rule())
            val written = services.demoRevision.value
            assertEquals(before + 2, written)
            demo.currentSchedule(medication.id)
            demo.dailyPlan()
            assertEquals(written, services.demoRevision.value)
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                demo.replaceSchedule(medication.id, rule().copy(times = listOf(ScheduleTime("24:00"))))
            }.error)
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { demo.medication(99999) }.error)
            assertEquals(written, services.demoRevision.value)
            services.session.signOut()
            assertEquals(written + 1, services.demoRevision.value)
            services.signInWithDemoAccount()
            services.resetDemo()
            assertEquals(written + 2, services.demoRevision.value)
        } finally {
            services.close()
        }
    }

    private suspend fun createServices(): AppServices =
        AppServices.create(ApiSettings.Mock(MockScenario.EMPTY)).also { it.signInWithDemoAccount() }

    private fun medicine(name: String = "Lek demonstracyjny") = CreateMedication(name, JsonPrimitive(1), "TABLET")

    private fun rule() = ReplaceSchedule(
        DEMO_DATE, setOf(1, 2), listOf(ScheduleTime("08:00", TimeOfDay.MORNING)), userOrganizerId = 1,
    )
}
