package com.elcomp.medicationorganizer.features.profile.data

import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.DemoSession
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.core.network.demoAccountLogin
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.features.auth.data.AuthRepository
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ProfileRepositoryTest {
    @Test
    fun putSendsAllThreeFieldsAndTheActiveBearer() = runBlocking<Unit> {
        val session = SessionManager(demoOrigin, null, Clock.System)
        session.signIn { TokenResponse("fixture-profile-token", 60) }
        val expectedBody = apiJson.parseToJsonElement("""{"simplifiedMode":false,"textScale":100,"highContrast":false}""")
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("/api/users/me/preferences", request.url.encodedPath)
                    assertEquals("Bearer fixture-profile-token", request.headers[HttpHeaders.Authorization])
                    assertEquals(expectedBody, apiJson.parseToJsonElement(request.body.toByteArray().decodeToString()))
                    respond(expectedBody.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        try {
            val profile = ProfileRepository(ApiClient(client, demoOrigin, session))
            assertEquals(AccessibilityPreferences.Default, profile.savePreferences(AccessibilityPreferences.Default))
        } finally {
            client.close()
        }
    }

    @Test
    fun onlyTheBearerOwnerCanUseProfileRoutesAndInvalidBodiesDoNotApply() = runBlocking<Unit> {
        val mock = DemoSession(MockScenario.NORMAL, Clock.System)
        val session = SessionManager(demoOrigin, null, Clock.System)
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request -> with(mock) { handle(request) } }
            }
        }
        try {
            val api = ApiClient(client, demoOrigin, session)
            val unauthorized = assertFailsWith<ApiException> { api.get<ProfileResponse>("/api/users/me") }
            assertEquals(401, unauthorized.statusCode)

            session.signIn { AuthRepository(api).login(demoAccountLogin()) }
            val profile = ProfileRepository(api)
            val original = profile.profile()
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                profile.updateDisplayName("   ")
            }.error)
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                profile.updateDisplayName("x".repeat(33))
            }.error)
            assertEquals(original, profile.profile())

            val incomplete = JsonObject(mapOf("textScale" to JsonPrimitive(150)))
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                api.put<JsonObject, AccessibilityPreferences>(
                    "/api/users/me/preferences",
                    incomplete,
                    requiresSession = true,
                )
            }.error)
            assertEquals(ApiError.INVALID_REQUEST, assertFailsWith<ApiException> {
                profile.savePreferences(AccessibilityPreferences(false, 201, false))
            }.error)
            assertEquals(AccessibilityPreferences.Default, profile.preferences())
        } finally {
            client.close()
        }
    }

    @Test
    fun accountProfilesAndPreferencesStaySeparateAcrossSignOut() = runBlocking<Unit> {
        val store = TestPreferencesStore()
        val services = AppServices.create(demoPreferencesStore = store)
        try {
            val profile = assertNotNull(services.profile)
            services.signInWithDemoAccount()
            val seed = profile.profile()
            assertEquals("alex@example.com", seed.email)
            val renamed = profile.updateDisplayName("  Alex demo  ")
            assertEquals(seed.copy(displayName = "Alex demo"), renamed)
            val seedSettings = AccessibilityPreferences(true, 150, false)
            profile.savePreferences(seedSettings)
            val seedRecord = assertNotNull(store.record)

            val temporary = services.auth.register(RegisterRequest(temporaryEmail, temporaryPassword, "Iga"))
            services.session.signOut()
            services.signIn(LoginRequest(temporaryEmail, temporaryPassword))
            assertEquals(ProfileResponse(temporary.id, temporaryEmail, "Iga"), profile.profile())
            assertEquals(AccessibilityPreferences.Default, profile.preferences())
            val temporarySettings = AccessibilityPreferences(false, 200, true)
            profile.savePreferences(temporarySettings)
            assertEquals(seedRecord, store.record)

            services.session.signOut()
            services.signInWithDemoAccount()
            assertEquals(renamed, profile.profile())
            assertEquals(seedSettings, profile.preferences())
            services.session.signOut()
            services.signIn(LoginRequest(temporaryEmail, temporaryPassword))
            assertEquals(temporarySettings, profile.preferences())
        } finally {
            services.close()
        }
    }

    @Test
    fun restartRestoresOnlySeedPreferencesAndResetClearsThem() = runBlocking<Unit> {
        val store = TestPreferencesStore()
        val settings = AccessibilityPreferences(true, 175, true)
        val first = AppServices.create(demoPreferencesStore = store)
        try {
            val profile = assertNotNull(first.profile)
            first.signInWithDemoAccount()
            profile.updateDisplayName("Nazwa tylko w tym uruchomieniu")
            profile.savePreferences(settings)
            first.auth.register(RegisterRequest(temporaryEmail, temporaryPassword))
            first.signIn(LoginRequest(temporaryEmail, temporaryPassword))
            profile.savePreferences(settings)
        } finally {
            first.close()
        }

        val restarted = AppServices.create(demoPreferencesStore = store)
        try {
            val profile = assertNotNull(restarted.profile)
            restarted.signInWithDemoAccount()
            assertEquals("Alex", profile.profile().displayName)
            assertEquals(settings, profile.preferences())
            assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> {
                restarted.signIn(LoginRequest(temporaryEmail, temporaryPassword))
            }.error)
            restarted.auth.register(RegisterRequest(temporaryEmail, temporaryPassword))
            restarted.signIn(LoginRequest(temporaryEmail, temporaryPassword))
            assertEquals(AccessibilityPreferences.Default, profile.preferences())

            restarted.resetDemo()
            assertNull(store.record)
            restarted.signInWithDemoAccount()
            assertEquals(AccessibilityPreferences.Default, profile.preferences())
        } finally {
            restarted.close()
        }
    }

    @Test
    fun failedStorageWriteLeavesSavedAndInMemoryValuesIntact() = runBlocking<Unit> {
        val store = TestPreferencesStore()
        val services = AppServices.create(demoPreferencesStore = store)
        try {
            val profile = assertNotNull(services.profile)
            services.signInWithDemoAccount()
            val original = AccessibilityPreferences(false, 125, true)
            profile.savePreferences(original)
            val previousRecord = assertNotNull(store.record)

            store.failWrites = true
            val failure = assertFailsWith<ApiException> {
                profile.savePreferences(AccessibilityPreferences(true, 175, false))
            }
            assertEquals(ApiError.SERVER, failure.error)
            assertEquals(503, failure.statusCode)
            assertEquals(previousRecord, store.record)
            assertEquals(original, profile.preferences())
        } finally {
            services.close()
        }
    }

    @Test
    fun liveDoesNotExposeProvisionalProfileRoutes() {
        val services = AppServices.create(ApiSettings.Live("https://api.example.test"))
        try {
            assertNull(services.profile)
        } finally {
            services.close()
        }
    }
}

private const val demoOrigin = "https://demo.invalid"
private const val temporaryEmail = "iga@example.org"
private const val temporaryPassword = "demo-password"

private class TestPreferencesStore : DemoPreferencesStore {
    var record: String? = null
        private set
    var failWrites = false

    override suspend fun read(): String? = record

    override suspend fun write(record: String) {
        if (failWrites) throw DemoPreferencesStorageException()
        this.record = record
    }

    override suspend fun clear() {
        record = null
    }
}
