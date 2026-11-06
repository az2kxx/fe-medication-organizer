package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.features.auth.data.AuthRepository
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.UserRole
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTransportTest {
    @Test
    fun loginUsesConfirmedJsonAndToleratesAdditionalFields() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/auth/login", request.url.encodedPath)
                    val json = apiJson.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals("alex@example.com", json["email"]?.jsonPrimitive?.content)
                    assertEquals("example-password", json["password"]?.jsonPrimitive?.content)
                    respond(
                        """{"accessToken":"demo-token","expiresInSeconds":3600,"addedLater":true}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        try {
            val token = AuthRepository(ApiClient(client, "https://demo.invalid")).login(demoLogin)
            assertEquals("demo-token", token.accessToken)
            assertEquals(3600L, token.expiresInSeconds)
        } finally {
            client.close()
        }
    }

    @Test
    fun demoAuthKeepsRegistrationUntilReset() = runBlocking {
        val services = AppServices.create()
        try {
            val registration = RegisterRequest("new@example.org", "mock-password")
            val user = services.auth.register(registration)
            assertEquals("new@example.org", user.email)
            assertNull(user.displayName)
            assertEquals(setOf(UserRole.USER), user.roles)

            val login = LoginRequest(registration.email, registration.password)
            val firstToken = services.auth.login(login).accessToken
            val secondToken = services.auth.login(login).accessToken
            assertTrue(firstToken.startsWith("demo-token-"))
            assertTrue(secondToken.startsWith("demo-token-"))
            assertFalse(firstToken == secondToken)
            val duplicate = assertFailsWith<ApiException> { services.auth.register(registration) }
            assertEquals(ApiError.CONFLICT, duplicate.error)
            assertEquals(409, duplicate.statusCode)

            services.resetDemo()
            assertEquals(ApiError.UNAUTHORIZED, assertFailsWith<ApiException> {
                services.auth.login(login)
            }.error)
            assertTrue(services.auth.login(demoLogin).accessToken.startsWith("demo-token-"))
        } finally {
            services.close()
        }
    }

    @Test
    fun offlineAndServerErrorsAreRetryableWithoutAutomaticRetries() = runBlocking {
        val offline = AppServices.create(ApiSettings.Mock(MockScenario.OFFLINE))
        val serverError = AppServices.create(ApiSettings.Mock(MockScenario.API_ERROR))
        try {
            val offlineFailure = assertFailsWith<ApiException> { offline.auth.login(demoLogin) }
            assertEquals(ApiError.NETWORK, offlineFailure.error)
            assertTrue(offlineFailure.error.retryable)
            assertNull(offlineFailure.statusCode)

            val serverFailure = assertFailsWith<ApiException> { serverError.auth.login(demoLogin) }
            assertEquals(ApiError.SERVER, serverFailure.error)
            assertTrue(serverFailure.error.retryable)
            assertEquals(503, serverFailure.statusCode)
        } finally {
            offline.close()
            serverError.close()
        }
    }

    @Test
    fun slowScenarioCanBeCancelled() = runBlocking {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.SLOW))
        try {
            assertFailsWith<CancellationException> {
                withTimeout(50) { services.auth.login(demoLogin) }
            }
            assertTrue(services.auth.login(demoLogin).accessToken.startsWith("demo-token-"))
        } finally {
            services.close()
        }
    }

    @Test
    fun invalidPayloadAndHttpFailureUseSafeMessages() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureApiClient()
            engine {
                addHandler {
                    respond("untrusted-details", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                addHandler { respond("untrusted-details", HttpStatusCode.Unauthorized) }
            }
        }
        try {
            val auth = AuthRepository(ApiClient(client, "https://demo.invalid"))
            val malformed = assertFailsWith<ApiException> { auth.login(demoLogin) }
            assertEquals(ApiError.INVALID_RESPONSE, malformed.error)
            assertFalse(malformed.message.orEmpty().contains("untrusted-details"))

            val unauthorized = assertFailsWith<ApiException> { auth.login(demoLogin) }
            assertEquals(ApiError.UNAUTHORIZED, unauthorized.error)
            assertEquals(401, unauthorized.statusCode)
            assertFalse(unauthorized.message.orEmpty().contains("untrusted-details"))
            assertFalse(unauthorized.error.retryable)
        } finally {
            client.close()
        }
    }

    @Test
    fun liveUsesConfiguredOriginAndDoesNotFollowRedirects() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/auth/login") { exchange ->
            exchange.use {
                val request = exchange.requestBody.bufferedReader().use { it.readText() }
                val email = apiJson.parseToJsonElement(request).jsonObject["email"]?.jsonPrimitive?.content
                if (exchange.requestMethod == "POST" && email == "integration@local.test") {
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val body = """{"accessToken":"local-server-token","expiresInSeconds":60}""".encodeToByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.write(body)
                } else {
                    exchange.sendResponseHeaders(400, -1)
                }
            }
        }
        server.createContext("/api/auth/register") { exchange ->
            exchange.use {
                exchange.responseHeaders.set("Location", "/api/auth/login")
                exchange.sendResponseHeaders(307, -1)
            }
        }
        server.start()
        try {
            val services = AppServices.create(ApiSettings.Live("http://127.0.0.1:${server.address.port}"))
            try {
                val token = services.auth.login(LoginRequest("integration@local.test", "local-password"))
                assertEquals("local-server-token", token.accessToken)
                val redirect = assertFailsWith<ApiException> {
                    services.auth.register(RegisterRequest("integration@local.test", "local-password"))
                }
                assertEquals(307, redirect.statusCode)
            } finally {
                services.close()
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun liveDoesNotExposeDemoRepositoryAndRequiresAnOrigin() {
        val services = AppServices.create(ApiSettings.Live("http://127.0.0.1:8080/"))
        try {
            assertNull(services.demo)
        } finally {
            services.close()
        }
        listOf(
            "http://example.com",
            "https://api.example.com/api",
            "https://user:password@api.example.com",
            "https://api.example.com/#fragment",
            "https://api.example.com?param=value",
            "https://",
            "https:///api.example.com",
            "https://api.example.com:99999",
            "https://api.example.com:not-a-port",
            "api.example.com",
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) { ApiSettings.Live(url) }
        }
    }
}

private val demoLogin = LoginRequest("alex@example.com", "example-password")
