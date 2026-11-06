package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.core.network.configureApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

class DemoContractTest {
    @Test
    fun planAndPreparationDecodeThePlannedStatusesAndAnEmptyList() {
        val empty = apiJson.decodeFromString<List<DoseOccurrence>>(fixture("daily-plan-empty-response.json"))
        val missed = apiJson.decodeFromString<List<DoseOccurrence>>(fixture("daily-plan-missed-response.json")).single()
        val cancelled = apiJson.decodeFromString<Preparation>(fixture("preparation-cancelled-response.json"))

        assertTrue(empty.isEmpty())
        assertEquals(DoseStatus.MISSED, missed.status)
        assertEquals("0.25", missed.doseAmountSnapshot.content)
        assertEquals(PreparationStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.completedAt)
        assertEquals(8L, cancelled.compartments.single().compartmentDefinitionId)
    }

    @Test
    fun medicationPayloadsKeepTheirWireFields() {
        val create = assertFixtureRoundTrips<CreateMedication>("medication-create-request.json")
        val edit = assertFixtureRoundTrips<UpdateMedication>("medication-update-request.json")
        val status = assertFixtureRoundTrips<UpdateMedicationStatus>("medication-status-request.json")

        assertEquals(MedicationForm.CAPSULE, create.form)
        assertEquals("0.25", create.doseAmount.content)
        assertEquals(MedicationForm.LIQUID, edit.form)
        assertEquals("0.75", edit.doseAmount.content)
        assertEquals(MedicationStatus.SUSPENDED, status.status)
    }

    @Test
    fun medicationPatchesUpdatePendingDosesAndKeepRecordedHistory() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            services.signInWithDemoAccount()
            val demo = assertNotNull(services.demo)
            val create = apiJson.decodeFromString<CreateMedication>(fixture("medication-create-request.json"))
            val medication = demo.createMedication(create)
            assertEquals(MedicationStatus.ACTIVE, medication.status)
            demo.replaceSchedule(
                medication.id,
                ReplaceSchedule(
                    DEMO_DATE,
                    setOf(1),
                    listOf(ScheduleTime("08:00", TimeOfDay.MORNING), ScheduleTime("20:00", TimeOfDay.EVENING)),
                ),
            )
            val firstPlan = demo.dailyPlan()
            val recorded = demo.recordIntake(firstPlan.first().id, RecordIntake(IntakeStatus.TAKEN))

            val edit = apiJson.decodeFromString<UpdateMedication>(fixture("medication-update-request.json"))
            assertEquals(medication.copy(name = edit.name), demo.updateMedication(medication.id, edit))
            val updatedPlan = demo.dailyPlan()
            assertEquals(firstPlan.first().copy(status = DoseStatus.TAKEN), updatedPlan.first())
            val pending = updatedPlan.last()
            assertNotEquals(firstPlan.last().id, pending.id)
            assertEquals(edit.name, pending.medicationName)
            assertEquals(edit.doseAmount, pending.doseAmountSnapshot)
            assertEquals(edit.doseUnit, pending.doseUnitSnapshot)

            val invalid = assertFailsWith<ApiException> {
                demo.updateMedication(medication.id, edit.copy(doseAmount = JsonPrimitive(0)))
            }
            assertEquals(ApiError.INVALID_REQUEST, invalid.error)
            assertEquals(updatedPlan, demo.dailyPlan())

            val suspension = apiJson.decodeFromString<UpdateMedicationStatus>(fixture("medication-status-request.json"))
            assertEquals(MedicationStatus.SUSPENDED, demo.updateMedicationStatus(medication.id, suspension).status)
            assertEquals(listOf(updatedPlan.first()), demo.dailyPlan())
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> {
                demo.recordIntake(pending.id, RecordIntake(IntakeStatus.TAKEN))
            }.error)

            demo.updateMedicationStatus(medication.id, UpdateMedicationStatus(MedicationStatus.ACTIVE))
            assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.PENDING), demo.dailyPlan().map { it.status })
            demo.updateMedicationStatus(medication.id, UpdateMedicationStatus(MedicationStatus.INACTIVE))
            assertEquals(MedicationStatus.INACTIVE, demo.medications().single().status)
            assertEquals(listOf(updatedPlan.first()), demo.dailyPlan())
            assertEquals(listOf(recorded), demo.intakeHistory())
        } finally {
            services.close()
        }
    }

    @Test
    fun provisionalErrorBodiesUseTheBranchShapeAndHttpStatusRemainsAuthoritative() = runBlocking<Unit> {
        val expectations = listOf(
            400 to ApiError.INVALID_REQUEST,
            401 to ApiError.UNAUTHORIZED,
            403 to ApiError.FORBIDDEN,
            404 to ApiError.NOT_FOUND,
            409 to ApiError.CONFLICT,
            500 to ApiError.SERVER,
        )
        val responses = expectations.map { (code, _) ->
            val body = fixture("error-$code-response.json")
            val payload = apiJson.decodeFromString<ProvisionalErrorResponse>(body)
            assertEquals(code, payload.status)
            assertEquals(HttpStatusCode.fromValue(code).description, payload.error)
            assertEquals(Instant.parse("2026-11-02T09:00:00Z"), payload.timestamp)
            assertTrue(payload.message.isNotBlank())
            assertTrue(payload.path.startsWith("/api/"))
            payload to body
        }
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                responses.forEach { (payload, body) ->
                    addHandler { request ->
                        assertEquals(payload.path, request.url.encodedPath)
                        respond(
                            body,
                            HttpStatusCode.fromValue(payload.status),
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                addHandler {
                    respond(
                        responses.last().second,
                        HttpStatusCode.Forbidden,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        try {
            val api = ApiClient(client, "https://fixture.invalid")
            for ((index, expected) in expectations.withIndex()) {
                val (code, error) = expected
                val (payload, _) = responses[index]
                val failure = assertFailsWith<ApiException> { api.get<Unit>(payload.path) }
                assertEquals(code, failure.statusCode)
                assertEquals(error, failure.error)
                assertEquals(error.message, failure.message)
            }
            val inconsistent = assertFailsWith<ApiException> { api.get<Unit>(responses.last().first.path) }
            assertEquals(403, inconsistent.statusCode)
            assertEquals(ApiError.FORBIDDEN, inconsistent.error)
        } finally {
            client.close()
        }
    }

    private inline fun <reified T> assertFixtureRoundTrips(name: String): T {
        val body = fixture(name)
        val value = apiJson.decodeFromString<T>(body)
        assertEquals(apiJson.parseToJsonElement(body), apiJson.parseToJsonElement(apiJson.encodeToString(value)))
        return value
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/demo-contract/$name")) {
            "Missing demo contract fixture: $name"
        }.bufferedReader().use { it.readText() }
}

@Serializable
private data class ProvisionalErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
)
