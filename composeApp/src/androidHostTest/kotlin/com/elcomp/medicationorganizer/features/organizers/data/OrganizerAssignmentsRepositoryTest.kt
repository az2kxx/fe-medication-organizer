package com.elcomp.medicationorganizer.features.organizers.data

import com.elcomp.medicationorganizer.core.demo.CompartmentStatus
import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.demo.DEMO_DATE
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_7X3_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_DAILY_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_INACTIVE_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_ORGANIZER_ID
import com.elcomp.medicationorganizer.core.demo.IntakeStatus
import com.elcomp.medicationorganizer.core.demo.RecordIntake
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.demo.StartPreparation
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.demo.UpdateCompartment
import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class OrganizerAssignmentsRepositoryTest {
    @Test
    fun wireContractSendsBearerAndFullPatchAndAcceptsEmptyDelete() = runBlocking<Unit> {
        val origin = "https://assignments.test"
        val session = SessionManager(origin, null, Clock.System)
        session.signIn { TokenResponse("fixture-assignment-token", 60) }
        val collectionPath = "/api/users/me/organizers"
        val itemPath = "$collectionPath/102"
        val fixture = readFixture("assignment-response.json")
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    requests.add(request.method to path)
                    assertEquals("Bearer fixture-assignment-token", request.headers[HttpHeaders.Authorization])
                    val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
                    when (request.method) {
                        HttpMethod.Get -> respond(if (path == collectionPath) "[$fixture]" else fixture, headers = jsonHeaders)
                        HttpMethod.Post -> {
                            assertEquals(
                                apiJson.parseToJsonElement("""{"organizerModelId":3}"""),
                                apiJson.parseToJsonElement(request.body.toByteArray().decodeToString()),
                            )
                            respond(fixture, HttpStatusCode.Created, jsonHeaders)
                        }
                        HttpMethod.Patch -> {
                            assertEquals(
                                apiJson.parseToJsonElement(readFixture("assignment-patch-request.json")),
                                apiJson.parseToJsonElement(request.body.toByteArray().decodeToString()),
                            )
                            respond(fixture, headers = jsonHeaders)
                        }
                        HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                        else -> error("Unexpected assignment request")
                    }
                }
            }
        }
        try {
            val repository = OrganizerAssignmentsRepository(ApiClient(client, origin, session))
            val assignment = repository.list().single()
            assertEquals(UserOrganizer(102, 3, null, true, false, Instant.parse("2026-11-02T09:00:00Z")), assignment)
            assertEquals(assignment, repository.get(102))
            assertEquals(assignment, repository.assign(AssignOrganizerRequest(3)))
            assertEquals(assignment, repository.update(102, UpdateOrganizerAssignment(null, true, false)))
            repository.remove(102)
            assertEquals(
                listOf(
                    HttpMethod.Get to collectionPath,
                    HttpMethod.Get to itemPath,
                    HttpMethod.Post to collectionPath,
                    HttpMethod.Patch to itemPath,
                    HttpMethod.Delete to itemPath,
                ),
                requests,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun defaultAndActiveAreIndependentAndConflictDoesNotChangeState() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            val repository = assertNotNull(services.assignments)
            assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> { repository.list() }.error)
            services.signInWithDemoAccount()
            val original = repository.list()
            val default = original.single(UserOrganizer::isDefault)
            val backup = original.single { !it.isDefault }
            assertEquals(DEMO_ORGANIZER_ID, default.id)
            assertEquals(DEMO_MODEL_7X3_ID, backup.organizerModelId)
            assertNotEquals(backup.id, backup.organizerModelId)

            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                repository.assign(AssignOrganizerRequest(backup.organizerModelId, "a".repeat(33)))
            }.error)
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> {
                repository.assign(AssignOrganizerRequest(999))
            }.error)
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                repository.assign(AssignOrganizerRequest(DEMO_MODEL_INACTIVE_ID))
            }.error)
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> {
                repository.update(backup.id, UpdateOrganizerAssignment("Nie zapisuj", false, true))
            }.error)
            assertEquals(original, repository.list())

            val duplicate = repository.assign(AssignOrganizerRequest(backup.organizerModelId, "  Druga kasetka  "))
            assertNotEquals(duplicate.id, backup.id)
            assertEquals("Druga kasetka", duplicate.displayName)
            assertTrue(duplicate.active)
            assertFalse(duplicate.isDefault)
            repository.update(duplicate.id, UpdateOrganizerAssignment("   ", true, true))
            val selectedDefault = repository.list().single(UserOrganizer::isDefault)
            assertEquals(duplicate.copy(displayName = null, isDefault = true), selectedDefault)
            assertEquals(default.copy(isDefault = false), repository.get(default.id))

            repository.update(duplicate.id, UpdateOrganizerAssignment(null, false, false))
            assertFalse(repository.get(duplicate.id).active)
            assertTrue(repository.list().none(UserOrganizer::isDefault))
            repository.remove(duplicate.id)
            assertEquals(listOf(default.id, backup.id), repository.list().map(UserOrganizer::id))
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { repository.get(duplicate.id) }.error)
        } finally {
            services.close()
        }
    }

    @Test
    fun assignmentsHaveSeparatePlansAndPreparationAndDeletionPreservesIntake() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            services.signInWithDemoAccount()
            val repository = assertNotNull(services.assignments)
            val demo = assertNotNull(services.demo)
            val catalog = assertNotNull(services.organizers)
            val first = repository.list().single(UserOrganizer::isDefault)
            val second = repository.list().single { !it.isDefault }
            assertTrue(demo.dailyPlan(organizerId = second.id).isEmpty())

            repository.update(second.id, UpdateOrganizerAssignment(second.displayName, true, true))
            val medication = demo.createMedication(CreateMedication("Drugi lek", JsonPrimitive(1), "TABLET"))
            demo.replaceSchedule(
                medication.id,
                ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("12:00", TimeOfDay.NOON))),
            )
            val firstDose = demo.dailyPlan(organizerId = first.id).single()
            val secondDose = demo.dailyPlan(organizerId = second.id).single()
            assertEquals(first.id, firstDose.userOrganizerId)
            assertEquals(second.id, secondDose.userOrganizerId)
            assertNotEquals(medication.id, firstDose.medicationId)
            assertEquals(medication.id, secondDose.medicationId)
            assertNotEquals(firstDose.id, secondDose.id)
            val secondModel = catalog.model(second.organizerModelId)
            val mondayNoon = secondModel.compartments.single { it.dayOfWeek == 1 && it.timeOfDay == TimeOfDay.NOON }
            assertEquals(mondayNoon.id, secondDose.compartmentDefinitionId)
            assertEquals(listOf(firstDose), demo.weeklyPlan(organizerId = first.id))
            assertEquals(listOf(secondDose), demo.weeklyPlan(organizerId = second.id))

            val period = StartPreparation(DEMO_DATE, DEMO_DATE)
            val firstPreparation = demo.startPreparation(period, first.id)
            val secondPreparation = demo.startPreparation(period, second.id)
            assertEquals(2, firstPreparation.compartments.size)
            assertEquals(3, secondPreparation.compartments.size)
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> {
                demo.markCompartment(firstPreparation.id, mondayNoon.id, UpdateCompartment(CompartmentStatus.PREPARED))
            }.error)
            demo.markCompartment(secondPreparation.id, mondayNoon.id, UpdateCompartment(CompartmentStatus.PREPARED))
            assertEquals(firstPreparation, demo.currentPreparation(first.id))
            assertEquals(
                CompartmentStatus.PREPARED,
                demo.currentPreparation(second.id).compartments.single { it.compartmentDefinitionId == mondayNoon.id }.status,
            )

            repository.update(second.id, UpdateOrganizerAssignment(second.displayName, false, false))
            assertEquals(ApiError.CONFLICT, assertFailsWith<ApiException> { demo.dailyPlan(organizerId = second.id) }.error)
            val intake = demo.recordIntake(secondDose.id, RecordIntake(IntakeStatus.TAKEN))
            assertEquals(listOf(intake), demo.intakeHistory())
            repository.remove(second.id)
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { demo.currentPreparation(second.id) }.error)
            assertEquals(listOf(intake), demo.intakeHistory())
            assertEquals(listOf(firstDose), demo.dailyPlan(organizerId = first.id))
        } finally {
            services.close()
        }
    }

    @Test
    fun daylessOrganizerUsesOneDayAndAssignmentsResetOnAccountChange() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            services.signInWithDemoAccount()
            val repository = assertNotNull(services.assignments)
            val demo = assertNotNull(services.demo)
            val initial = repository.list().single()
            val daily = repository.assign(AssignOrganizerRequest(DEMO_MODEL_DAILY_ID))
            repository.update(daily.id, UpdateOrganizerAssignment(null, true, true))
            val medication = demo.createMedication(CreateMedication("Lek nocny", JsonPrimitive(1), "TABLET"))
            demo.replaceSchedule(
                medication.id,
                ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("22:00", TimeOfDay.NIGHT))),
            )
            val dose = demo.dailyPlan(organizerId = daily.id).single()
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                demo.startPreparation(StartPreparation(DEMO_DATE, "2026-11-03"), daily.id)
            }.error)
            val session = demo.startPreparation(StartPreparation(DEMO_DATE, DEMO_DATE), daily.id)
            assertEquals(4, session.compartments.size)
            assertTrue(session.compartments.any { it.compartmentDefinitionId == dose.compartmentDefinitionId })

            val credentials = LoginRequest("assignment-test@example.com", "example-password")
            services.auth.register(RegisterRequest(credentials.email, credentials.password))
            services.session.signOut()
            services.signIn(credentials)
            assertEquals(listOf(initial), repository.list())
            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { repository.get(daily.id) }.error)
        } finally {
            services.close()
        }
    }

    @Test
    fun scheduleSavedWithoutActiveOrganizerCanBeAttachedByRepeatingThePut() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            services.signInWithDemoAccount()
            val repository = assertNotNull(services.assignments)
            val demo = assertNotNull(services.demo)
            val initial = repository.list().single()
            repository.update(initial.id, UpdateOrganizerAssignment(initial.displayName, false, false))
            val medication = demo.createMedication(CreateMedication("Lek demonstracyjny", JsonPrimitive(1), "TABLET"))
            val rule = ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("08:00", TimeOfDay.MORNING)))
            val schedule = demo.replaceSchedule(medication.id, rule)
            assertNull(schedule.userOrganizerId)
            assertEquals(medication.id, schedule.medicationId)
            assertEquals(rule.validFrom, schedule.validFrom)
            assertEquals(rule.validUntil, schedule.validUntil)
            assertEquals(rule.days, schedule.days)
            assertEquals(rule.times, schedule.times)

            val active = repository.assign(AssignOrganizerRequest(DEMO_MODEL_7X3_ID))
            assertTrue(demo.dailyPlan(organizerId = active.id).isEmpty())
            val assigned = demo.replaceSchedule(medication.id, rule)
            assertNotEquals(schedule.id, assigned.id)
            assertEquals(medication.id, assigned.medicationId)
            assertEquals(active.id, assigned.userOrganizerId)
            assertEquals(rule.validFrom, assigned.validFrom)
            assertEquals(rule.validUntil, assigned.validUntil)
            assertEquals(rule.days, assigned.days)
            assertEquals(rule.times, assigned.times)
            assertEquals(assigned, demo.currentSchedule(medication.id))
            assertEquals(assigned, demo.replaceSchedule(medication.id, rule))
            val dose = demo.dailyPlan(organizerId = active.id).single()
            assertEquals(active.id, dose.userOrganizerId)
            assertEquals(medication.id, dose.medicationId)
            assertEquals(rule.validFrom, dose.scheduledDate)
            assertEquals(rule.times.single().time, dose.scheduledTime)
        } finally {
            services.close()
        }
    }

    @Test
    fun liveModeDoesNotExposeProvisionalAssignments() {
        val services = AppServices.create(ApiSettings.Live("http://127.0.0.1:8080"))
        try {
            assertNull(services.assignments)
        } finally {
            services.close()
        }
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/organizers/$name")).bufferedReader().use { it.readText() }
}
