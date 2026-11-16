package com.elcomp.medicationorganizer.features.medications.domain

import com.elcomp.medicationorganizer.core.demo.DEMO_DATE
import com.elcomp.medicationorganizer.core.demo.Medication
import com.elcomp.medicationorganizer.core.demo.MedicationForm
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MedicationDraftTest {
    @Test
    fun fractionalDraftMatchesCreateRequestFixtureIncludingDefaultStartDate() {
        val draft = fractionalDraft.copy(
            name = "  Lek demonstracyjny w płynie  ",
            doseAmount = " ,125 ",
            doseUnit = " ml ",
            strengthValue = "0012.50",
            strengthUnit = " mg ",
        )
        val validation = draft.validate()
        val request = assertNotNull(validation.request)
        assertTrue(validation.errors.isEmpty())
        assertEquals(
            apiJson.parseToJsonElement(readFixture("fe11-create-medication-request.json")),
            apiJson.parseToJsonElement(apiJson.encodeToString(request)),
        )
    }

    @Test
    fun decimalNormalizationPreservesPrecisionAndOmitsUnfilledOptionalFields() {
        val request = assertNotNull(
            MedicationDraft("Lek demo", "09007199254740993.000000000000000001", "tabletka", DEMO_DATE)
                .validate().request,
        )
        val payload = apiJson.parseToJsonElement(apiJson.encodeToString(request)).jsonObject
        val dose = assertNotNull(payload["doseAmount"]).jsonPrimitive
        assertFalse(dose.isString)
        assertEquals("9007199254740993.000000000000000001", dose.content)
        assertNull(payload["form"])
        assertNull(payload["strengthValue"])
        assertNull(payload["strengthUnit"])
        assertNull(payload["endDate"])
    }

    @Test
    fun requiredFieldsAndStrengthPairReportErrorsOnTheCorrespondingFields() {
        val blank = MedicationDraft(name = " ", doseAmount = " ", doseUnit = " ", startDate = " ").validate()
        assertNull(blank.request)
        assertEquals(
            setOf(MedicationField.NAME, MedicationField.DOSE_AMOUNT, MedicationField.DOSE_UNIT, MedicationField.START_DATE),
            blank.errors.keys,
        )
        assertTrue(blank.errors.values.all { it == MedicationFieldError.REQUIRED })

        val unitMissing = fractionalDraft.copy(strengthUnit = " ").validate()
        assertNull(unitMissing.request)
        assertEquals(mapOf(MedicationField.STRENGTH_UNIT to MedicationFieldError.REQUIRED), unitMissing.errors)
        val strengthMissing = fractionalDraft.copy(strengthValue = " ").validate()
        assertNull(strengthMissing.request)
        assertEquals(mapOf(MedicationField.STRENGTH_VALUE to MedicationFieldError.REQUIRED), strengthMissing.errors)
    }

    @Test
    fun invalidNumbersAreRejectedForBothDoseAndStrength() {
        for (input in listOf("0", "000,000", ".0", "-1", "+1", "1e-3", "1,2.3", "1 000", "1_000", "1,")) {
            val validation = fractionalDraft.copy(doseAmount = input, strengthValue = input).validate()
            assertNull(validation.request, input)
            assertEquals(
                mapOf(
                    MedicationField.DOSE_AMOUNT to MedicationFieldError.POSITIVE_DECIMAL,
                    MedicationField.STRENGTH_VALUE to MedicationFieldError.POSITIVE_DECIMAL,
                ),
                validation.errors,
                input,
            )
        }
    }

    @Test
    fun calendarDatesAreStrictAndAllowTheSameStartAndEnd() {
        for (input in listOf("2026-4-01", "2026-04-31", "2026-13-01", "2025-02-29", "2026-01-01T00:00:00Z")) {
            val validation = fractionalDraft.copy(startDate = input, endDate = input).validate()
            assertNull(validation.request, input)
            assertEquals(
                mapOf(
                    MedicationField.START_DATE to MedicationFieldError.INVALID_DATE,
                    MedicationField.END_DATE to MedicationFieldError.INVALID_DATE,
                ),
                validation.errors,
                input,
            )
        }
        assertNotNull(fractionalDraft.copy(startDate = "2024-02-29", endDate = "2024-02-29").validate().request)
        val reversed = fractionalDraft.copy(startDate = "2026-12-02", endDate = "2026-12-01").validate()
        assertNull(reversed.request)
        assertEquals(mapOf(MedicationField.END_DATE to MedicationFieldError.END_BEFORE_START), reversed.errors)
    }

    @Test
    fun createAndReloadInMockPreserveMedicationAndRejectedRequestsDoNotAddRows() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            services.signInWithDemoAccount()
            val medications = assertNotNull(services.demo)
            assertTrue(medications.medications().isEmpty())
            val request = assertNotNull(fractionalDraft.validate().request)
            val expected = apiJson.decodeFromString<Medication>(readFixture("fe11-create-medication-response.json"))
            assertEquals(expected, medications.createMedication(request))

            for (invalidRequest in listOf(
                request.copy(doseAmount = JsonPrimitive(0)),
                request.copy(doseAmount = JsonPrimitive("0.125")),
                request.copy(startDate = "2026-11-31"),
                request.copy(endDate = "2026-11-01"),
            )) {
                assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                    medications.createMedication(invalidRequest)
                }.error)
            }
            assertEquals(listOf(expected), medications.medications())

            medications.replaceSchedule(expected.id, ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("08:00"))))
            val dose = medications.dailyPlan(DEMO_DATE).single()
            assertEquals("0.125", dose.doseAmountSnapshot.content)
            assertEquals("ml", dose.doseUnitSnapshot)
            assertTrue(medications.dailyPlan("2026-12-07").isEmpty())
        } finally {
            services.close()
        }
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/medications/$name")) {
            "Missing medication fixture: $name"
        }.bufferedReader().use { it.readText() }

    private val fractionalDraft = MedicationDraft(
        name = "Lek demonstracyjny w płynie",
        doseAmount = "0,125",
        doseUnit = "ml",
        startDate = DEMO_DATE,
        endDate = "2026-11-30",
        strengthValue = "12.50",
        strengthUnit = "mg",
        form = MedicationForm.LIQUID,
    )
}
