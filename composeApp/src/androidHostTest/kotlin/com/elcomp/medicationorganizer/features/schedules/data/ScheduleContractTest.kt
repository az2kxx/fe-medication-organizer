package com.elcomp.medicationorganizer.features.schedules.data

import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.demo.DEMO_DATE
import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.demo.DoseStatus
import com.elcomp.medicationorganizer.core.demo.IntakeStatus
import com.elcomp.medicationorganizer.core.demo.MedicationSchedule
import com.elcomp.medicationorganizer.core.demo.RecordIntake
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleDraft
import com.elcomp.medicationorganizer.features.schedules.domain.ScheduleTimeDraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class ScheduleContractTest {
    @Test
    fun schedulePutSendsTheMedicationIdBearerAndCompleteValidatedRules() = runBlocking<Unit> {
        val expectedJson = apiJson.parseToJsonElement(fixture("fe12-replace-schedule-request.json"))
        val response = fixture("fe12-replace-schedule-response.json")
        val session = SessionManager(origin, null, Clock.System)
        session.signIn { TokenResponse("fixture-schedule-token", 60) }
        var calls = 0
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    calls++
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("/api/medications/12/schedule", request.url.encodedPath)
                    assertEquals("Bearer fixture-schedule-token", request.headers[HttpHeaders.Authorization])
                    assertEquals(expectedJson, apiJson.parseToJsonElement(request.body.toByteArray().decodeToString()))
                    respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        try {
            val draft = ScheduleDraft(
                validFrom = "2026-11-02", validUntil = "2026-11-30", days = setOf(7, 3, 1),
                times = listOf(
                    ScheduleTimeDraft("8:00", TimeOfDay.MORNING),
                    ScheduleTimeDraft("20:15", TimeOfDay.EVENING),
                    ScheduleTimeDraft("23:59", TimeOfDay.OTHER),
                ),
            )
            val repository = DemoRepository(ApiClient(client, origin, session))
            val saved = repository.replaceSchedule(12, assertNotNull(draft.validate().request))
            assertEquals(apiJson.decodeFromString<MedicationSchedule>(response), saved)
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun replacingFutureRulesRefreshesTheMockPlanWithoutRewritingEarlierIntake() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            services.signInWithDemoAccount()
            val demo = assertNotNull(services.demo)
            val medication = demo.createMedication(CreateMedication("Lek demonstracyjny", JsonPrimitive(1), "TABLET"))
            demo.replaceSchedule(medication.id, ReplaceSchedule(DEMO_DATE, setOf(1, 2), listOf(ScheduleTime("08:00", TimeOfDay.MORNING))))
            val earlierDose = demo.dailyPlan().single()
            val recorded = demo.recordIntake(earlierDose.id, RecordIntake(IntakeStatus.TAKEN))
            val futureDate = "2026-11-03"
            val oldFutureDose = demo.dailyPlan(futureDate).single()
            val replacement = assertNotNull(
                ScheduleDraft(futureDate, days = setOf(2), times = listOf(ScheduleTimeDraft("20:00", TimeOfDay.EVENING)))
                    .validate().request,
            )
            demo.replaceSchedule(medication.id, replacement)

            val futurePlan = demo.dailyPlan(futureDate)
            assertEquals(listOf("20:00"), futurePlan.map { it.scheduledTime })
            assertNotEquals(oldFutureDose.id, futurePlan.single().id)
            assertEquals(earlierDose.copy(status = DoseStatus.TAKEN), demo.dailyPlan().single())
            assertEquals(listOf(recorded), demo.intakeHistory(DEMO_DATE, futureDate))
        } finally {
            services.close()
        }
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/schedules/$name")).readText()
}

private const val origin = "https://demo.invalid"
