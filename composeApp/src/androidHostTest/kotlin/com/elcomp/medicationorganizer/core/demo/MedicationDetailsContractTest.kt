package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking

class MedicationDetailsContractTest {
    @Test
    fun detailsAndCurrentScheduleUseProtectedGetRoutesAndDecodeCompleteFixtures() = runBlocking<Unit> {
        val origin = "https://fixture.invalid"
        val session = SessionManager(origin, null, Clock.System)
        session.signIn { TokenResponse("medication-fixture-token", 60) }
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("Bearer medication-fixture-token", request.headers[HttpHeaders.Authorization])
                    val path = request.url.encodedPath
                    paths += path
                    val fixture = when (path) {
                        "/api/medications/12" -> "/fixtures/medications/fe11-medication-details-response.json"
                        "/api/medications/12/schedule" -> "/fixtures/schedules/fe12-assigned-schedule-response.json"
                        else -> error("Unexpected route")
                    }
                    respond(checkNotNull(javaClass.getResource(fixture)).readText(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        try {
            val demo = DemoRepository(ApiClient(client, origin, session))
            val details = demo.medication(12)
            assertEquals("0.125", details.doseAmount.content)
            assertEquals("12.50", details.strengthValue?.content)
            assertEquals(MedicationForm.LIQUID, details.form)
            assertEquals(101L, details.userOrganizerId)
            val schedule = demo.currentSchedule(12)
            assertEquals(101L, schedule.userOrganizerId)
            assertEquals(setOf(1, 3, 7), schedule.days)
            assertEquals(listOf("08:00", "20:15"), schedule.times.map { it.time })
            assertEquals(listOf("/api/medications/12", "/api/medications/12/schedule"), paths)
        } finally {
            client.close()
        }
    }
}
