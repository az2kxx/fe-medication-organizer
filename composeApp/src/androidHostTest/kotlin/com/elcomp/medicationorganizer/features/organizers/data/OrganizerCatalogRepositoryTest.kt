package com.elcomp.medicationorganizer.features.organizers.data

import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_7X3_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_7X3_PRODUCT_CODE
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_DAILY_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_ID
import com.elcomp.medicationorganizer.core.demo.DEMO_MODEL_INACTIVE_ID
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.core.network.demoAccountLogin
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import com.elcomp.medicationorganizer.features.organizers.data.dto.CompartmentDefinitionResponse
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelSummary
import com.elcomp.medicationorganizer.features.organizers.domain.layoutRows
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking

class OrganizerCatalogRepositoryTest {
    @Test
    fun catalogReadsAreSentWithoutBearerEvenWhenSignedIn() = runBlocking<Unit> {
        val session = SessionManager(origin, null, Clock.System)
        session.signIn { TokenResponse("fixture-catalog-token", 60) }
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    requests += request
                    val fixture = if (request.url.encodedPath == "/api/organizer-models") {
                        "organizer-models-response.json"
                    } else {
                        "organizer-model-daily-response.json"
                    }
                    respond(readFixture(fixture), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        try {
            val catalog = OrganizerCatalogRepository(ApiClient(client, origin, session))
            catalog.models()
            catalog.findByProductCode("  ELC 7/3&x=1  ")
            catalog.model(DEMO_MODEL_DAILY_ID)

            assertEquals(
                listOf("/api/organizer-models", "/api/organizer-models", "/api/organizer-models/3"),
                requests.map { it.url.encodedPath },
            )
            assertTrue(requests.none { it.headers.contains(HttpHeaders.Authorization) })
            assertNull(requests[0].url.parameters["productCode"])
            assertEquals("ELC 7/3&x=1", requests[1].url.parameters["productCode"])
            assertEquals(setOf("productCode"), requests[1].url.parameters.names())
        } finally {
            client.close()
        }
    }

    @Test
    fun fixturesDecodeOptionalFieldsAndIgnoreUnknownOnes() {
        val models = apiJson.decodeFromString<List<OrganizerModelSummary>>(readFixture("organizer-models-response.json"))
        assertEquals(listOf(2L, 4L), models.map(OrganizerModelSummary::id))
        assertTrue(models[0].active)
        assertFalse(models[1].active)
        assertNull(models[1].description)

        val daily = apiJson.decodeFromString<OrganizerModelDetails>(readFixture("organizer-model-daily-response.json"))
        assertEquals("ELC-1X4", daily.productCode)
        assertEquals(4, daily.compartments.size)
        assertTrue(daily.compartments.all { it.dayOfWeek == null })
        assertNull(daily.compartments.single { it.id == 204L }.timeOfDay)
    }

    @Test
    fun layoutRowsFollowPositionIndexAndCompartmentsPerDay() {
        val daily = apiJson.decodeFromString<OrganizerModelDetails>(readFixture("organizer-model-daily-response.json"))
        val rows = daily.layoutRows()
        assertEquals(1, rows.size)
        assertNull(rows.single().dayOfWeek)
        assertEquals(listOf(0, 1, 2, 3), rows.single().compartments.map(CompartmentDefinitionResponse::positionIndex))

        val withoutPerDay = OrganizerModelDetails(
            id = 9,
            productCode = "FIXTURE",
            name = "Fixture",
            active = true,
            compartments = listOf(
                compartment(id = 4, position = 3, day = 2),
                compartment(id = 1, position = 0, day = 1),
                compartment(id = 3, position = 2, day = 2),
                compartment(id = 2, position = 1, day = 1),
            ),
        )
        val grouped = withoutPerDay.layoutRows()
        assertEquals(listOf(1, 2), grouped.map { it.dayOfWeek })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), grouped.map { row -> row.compartments.map { it.id } })
        assertTrue(withoutPerDay.copy(compartments = emptyList()).layoutRows().isEmpty())
    }

    @Test
    fun demoCatalogIsPublicAndCoversWeeklyDailyAndInactiveModels() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            val catalog = assertNotNull(services.organizers)
            val models = catalog.models()
            assertEquals(
                listOf(DEMO_MODEL_ID, DEMO_MODEL_7X3_ID, DEMO_MODEL_DAILY_ID, DEMO_MODEL_INACTIVE_ID),
                models.map(OrganizerModelSummary::id),
            )
            assertFalse(models.single { it.id == DEMO_MODEL_INACTIVE_ID }.active)

            val found = catalog.findByProductCode(" ${DEMO_MODEL_7X3_PRODUCT_CODE.lowercase()} ")
            assertEquals(listOf(DEMO_MODEL_7X3_ID), found.map(OrganizerModelSummary::id))
            assertTrue(catalog.findByProductCode("NO-SUCH-CODE").isEmpty())

            val weekly = catalog.model(DEMO_MODEL_7X3_ID).layoutRows()
            assertEquals((1..7).toList(), weekly.map { it.dayOfWeek })
            assertTrue(weekly.all { row ->
                row.compartments.map(CompartmentDefinitionResponse::timeOfDay) ==
                    listOf(TimeOfDay.MORNING, TimeOfDay.NOON, TimeOfDay.EVENING)
            })
            val positions = catalog.model(DEMO_MODEL_7X3_ID).compartments.map(CompartmentDefinitionResponse::positionIndex)
            assertEquals((0..20).toList(), positions)

            val daily = catalog.model(DEMO_MODEL_DAILY_ID).layoutRows()
            assertEquals(1, daily.size)
            assertEquals(4, daily.single().compartments.size)

            assertEquals(ApiError.NOT_FOUND, assertFailsWith<ApiException> { catalog.model(99) }.error)
        } finally {
            services.close()
        }
    }

    @Test
    fun demoRepositoryReadsEveryCatalogModelWithTheSharedDto() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            val demo = assertNotNull(services.demo)
            val catalog = assertNotNull(services.organizers)
            for (id in listOf(DEMO_MODEL_ID, DEMO_MODEL_7X3_ID, DEMO_MODEL_DAILY_ID, DEMO_MODEL_INACTIVE_ID)) {
                assertEquals(catalog.model(id), demo.organizerModel(id))
            }
            assertTrue(demo.organizerModel(DEMO_MODEL_DAILY_ID).compartments.all { it.dayOfWeek == null })
        } finally {
            services.close()
        }
    }

    @Test
    fun demoPlanModelKeepsItsLayoutAfterSignIn() = runBlocking<Unit> {
        val services = AppServices.create(ApiSettings.Mock())
        try {
            services.signIn(demoAccountLogin())
            val demo = assertNotNull(services.demo)
            val catalogModel = assertNotNull(services.organizers).model(DEMO_MODEL_ID)
            val planModel = demo.organizerModel()
            assertEquals(14, planModel.compartments.size)
            assertEquals(catalogModel.compartments.map { it.id }, planModel.compartments.map { it.id })
            assertTrue(demo.dailyPlan().all { dose ->
                dose.compartmentDefinitionId == null || dose.compartmentDefinitionId in planModel.compartments.map { it.id }
            })
        } finally {
            services.close()
        }
    }

    @Test
    fun emptyScenarioKeepsTheCatalogAndOfflineReportsNetworkError() = runBlocking<Unit> {
        val empty = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        val offline = AppServices.create(ApiSettings.Mock(MockScenario.OFFLINE))
        try {
            assertEquals(4, assertNotNull(empty.organizers).models().size)
            val failure = assertFailsWith<ApiException> { assertNotNull(offline.organizers).models() }
            assertEquals(ApiError.NETWORK, failure.error)
            assertTrue(failure.error.retryable)
        } finally {
            empty.close()
            offline.close()
        }
    }

    @Test
    fun liveModeDoesNotExposeTheUnconfirmedCatalog() {
        val services = AppServices.create(ApiSettings.Live("http://127.0.0.1:8080"))
        try {
            assertNull(services.organizers)
        } finally {
            services.close()
        }
    }

    private fun compartment(id: Long, position: Int, day: Int) =
        CompartmentDefinitionResponse(id, position, "fixture-$id", day, TimeOfDay.MORNING)

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/organizers/$name")) {
            "Missing organizer contract fixture: $name"
        }.bufferedReader().use { it.readText() }

    private companion object {
        const val origin = "https://catalog.test"
    }
}
