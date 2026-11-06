package com.elcomp.medicationorganizer.core.session

import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.configureApiClient
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive

class SessionLifecycleTest {
    @Test
    fun restoredSessionIsBoundToItsOriginAndItsOriginalExpiry() = runBlocking<Unit> {
        val clock = TestClock()
        val store = TestStore()
        val manager = SessionManager(origin, store, clock)
        manager.signIn { TokenResponse("server-token", 60) }
        val deadline = manager.requireSession().expiresAt
        assertNotNull(store.bytes)

        val otherOrigin = SessionManager("https://other.example.test", store, clock)
        otherOrigin.restore()
        assertEquals(SessionState.SignedOut(), otherOrigin.state.value)
        assertFailsWith<ApiException> { otherOrigin.requireSession() }

        clock.instant += 30.seconds
        val restored = SessionManager(origin, store, clock)
        restored.restore()
        assertIs<SessionState.SignedIn>(restored.state.value)
        assertEquals(deadline, restored.requireSession().expiresAt)

        clock.instant = deadline
        val expired = SessionManager(origin, store, clock)
        expired.restore()
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), expired.state.value)
        assertNull(store.bytes)
        assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> { restored.requireSession() }.error)
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), restored.state.value)
    }

    @Test
    fun monitoringExpirySignsOutWithoutARequest() = runBlocking<Unit> {
        val clock = TestClock()
        val store = TestStore()
        val manager = SessionManager(origin, store, clock)
        manager.signIn { TokenResponse("server-token", 1) }
        clock.instant += 1.seconds

        val monitor = launch { manager.monitorExpiry() }
        try {
            val state = withTimeout(5_000) { manager.state.first { it is SessionState.SignedOut } }
            assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), state)
            assertNull(store.bytes)
        } finally {
            monitor.cancelAndJoin()
        }
    }

    @Test
    fun manualSignOutRequiresSecureStorageToBeCleared() = runBlocking<Unit> {
        val store = TestStore()
        val manager = SessionManager(origin, store, TestClock())
        manager.restore()
        store.unavailable = true
        assertFailsWith<SessionStorageException> { manager.signIn { TokenResponse("server-token", 60) } }
        assertEquals(SessionState.SignedOut(), manager.state.value)

        store.unavailable = false
        manager.signIn { TokenResponse("server-token", 60) }
        val signedIn = assertIs<SessionState.SignedIn>(manager.state.value)
        store.unavailable = true
        assertFailsWith<SessionStorageException> { manager.signOut() }
        assertEquals(signedIn, manager.state.value)

        store.unavailable = false
        manager.signOut()
        assertEquals(SessionState.SignedOut(SignOutReason.MANUAL), manager.state.value)
        assertNull(store.bytes)
    }

    @Test
    fun lateUnauthorizedResponseCannotInvalidateANewerSession() = runBlocking<Unit> {
        val oldHeader = CompletableDeferred<String?>()
        val releaseOldRequest = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/protected/old" -> {
                            oldHeader.complete(request.headers[HttpHeaders.Authorization])
                            releaseOldRequest.await()
                        }
                        "/api/auth/login" -> assertNull(request.headers[HttpHeaders.Authorization])
                        "/api/protected/current" ->
                            assertEquals("Bearer second-token", request.headers[HttpHeaders.Authorization])
                        else -> error("Unexpected test path")
                    }
                    respond("", HttpStatusCode.Unauthorized)
                }
            }
        }
        val store = TestStore()
        val manager = SessionManager(origin, store, TestClock())
        val api = ApiClient(client, origin, manager)

        try {
            withTimeout(5_000) {
                manager.signIn { TokenResponse("first-token", 60) }
                val oldFailure = async {
                    assertFailsWith<ApiException> { api.get<Unit>("/api/protected/old", requiresSession = true) }
                }
                assertEquals("Bearer first-token", oldHeader.await())

                manager.signIn { TokenResponse("second-token", 60) }
                val signedIn = assertIs<SessionState.SignedIn>(manager.state.value)
                releaseOldRequest.complete(Unit)
                assertEquals(401, oldFailure.await().statusCode)
                assertEquals(signedIn, manager.state.value)

                val failedLogin = assertFailsWith<ApiException> {
                    api.post<LoginRequest, TokenResponse>("/api/auth/login", LoginRequest("fake@example.com", "wrong-password"))
                }
                assertEquals(401, failedLogin.statusCode)
                assertEquals(signedIn, manager.state.value)

                val rejected = assertFailsWith<ApiException> {
                    api.get<Unit>("/api/protected/current", requiresSession = true)
                }
                assertEquals(401, rejected.statusCode)
                assertEquals(SessionState.SignedOut(SignOutReason.UNAUTHORIZED), manager.state.value)
                assertNull(store.bytes)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun demoLogoutClearsPrivateDataButKeepsRegisteredDemoAccountsInMemory() = runBlocking<Unit> {
        val store = TestStore(unavailable = true)
        val services = AppServices.create(ApiSettings.Mock(), store)
        try {
            val demo = assertNotNull(services.demo)
            services.session.restore()
            assertEquals(SessionState.SignedOut(), services.session.state.value)
            assertTrue(demo.organizerModel().compartments.isNotEmpty())
            assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> { demo.medications() }.error)

            val registration = RegisterRequest("new@example.org", "example-password")
            services.auth.register(registration)
            services.signIn(LoginRequest(registration.email, registration.password))
            val first = assertIs<SessionState.SignedIn>(services.session.state.value)
            val startingCount = demo.medications().size
            demo.createMedication(CreateMedication("Lek demonstracyjny konta", JsonPrimitive(1), "TABLET"))
            assertEquals(startingCount + 1, demo.medications().size)

            services.session.signOut()
            assertEquals(SessionState.SignedOut(SignOutReason.MANUAL), services.session.state.value)
            assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> { demo.medications() }.error)

            services.signIn(LoginRequest(registration.email, registration.password))
            val second = assertIs<SessionState.SignedIn>(services.session.state.value)
            assertTrue(second.generation > first.generation)
            assertEquals(startingCount, demo.medications().size)
            assertNull(store.bytes)
        } finally {
            services.close()
        }
    }
}

private const val origin = "https://api.example.test"

private class TestClock(var instant: Instant = Instant.parse("2026-11-02T10:00:00Z")) : Clock {
    override fun now(): Instant = instant
}

private class TestStore(var unavailable: Boolean = false) : SessionTokenStore {
    var bytes: ByteArray? = null
        private set

    override suspend fun read(): ByteArray? {
        if (unavailable) throw SessionStorageException()
        return bytes?.copyOf()
    }

    override suspend fun write(bytes: ByteArray) {
        if (unavailable) throw SessionStorageException()
        this.bytes = bytes.copyOf()
    }

    override suspend fun clear() {
        if (unavailable) throw SessionStorageException()
        bytes = null
    }
}
