package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.core.demo.DemoRoutes
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import com.elcomp.medicationorganizer.features.auth.data.dto.UserResponse
import com.elcomp.medicationorganizer.features.auth.data.dto.UserRole
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

private const val demoEmail = "alex@example.com"
private const val demoPassword = "example-password"
private const val demoTokenTtlSeconds = 3600L
private val demoTimestamp = Instant.parse("2026-11-02T08:00:00Z")
private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal fun demoAccountLogin() = LoginRequest(demoEmail, demoPassword)

internal class DemoSession(private val scenario: MockScenario, private val clock: Clock) {
    private class DemoCredential(val token: String, val expiresAt: Instant)

    private val lock = Mutex()
    private val routes = DemoRoutes(empty = scenario == MockScenario.EMPTY)
    private val accounts = mutableMapOf(demoEmail to demoPassword)
    private var active: DemoCredential? = null

    suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        when (scenario) {
            MockScenario.OFFLINE -> throw IOException("Mock offline")
            MockScenario.API_ERROR -> return respond("", HttpStatusCode.ServiceUnavailable)
            MockScenario.SLOW -> delay(750)
            MockScenario.NORMAL, MockScenario.EMPTY -> Unit
        }
        return lock.withLock {
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/auth/login" ->
                    login(request)
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/auth/register" ->
                    register(request)
                request.method == HttpMethod.Get && request.url.encodedPath.startsWith("/api/organizer-models/") ->
                    with(routes) { handle(request) }
                !hasActiveToken(request) -> respond("", HttpStatusCode.Unauthorized)
                else -> with(routes) { handle(request) }
            }
        }
    }

    suspend fun reset() = lock.withLock {
        accounts.clear()
        accounts[demoEmail] = demoPassword
        active = null
        routes.reset()
    }

    suspend fun endSession(token: String): Unit = lock.withLock {
        if (active?.token == token) {
            active = null
            routes.reset()
        }
    }

    private fun hasActiveToken(request: HttpRequestData): Boolean {
        val credential = active ?: return false
        return credential.expiresAt > clock.now() &&
            request.headers[HttpHeaders.Authorization] == "Bearer ${credential.token}"
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun MockRequestHandleScope.login(request: HttpRequestData): HttpResponseData {
        val credentials = request.decode<LoginRequest>() ?: return respond("", HttpStatusCode.BadRequest)
        val email = credentials.email.trim().lowercase()
        if (!validEmail(email) || !validPassword(credentials.password)) {
            return respond("", HttpStatusCode.BadRequest)
        }
        if (accounts[email] != credentials.password) {
            return respond("", HttpStatusCode.Unauthorized)
        }
        if (active != null) routes.reset()
        val token = "demo-token-${Uuid.random()}"
        active = DemoCredential(token, clock.now() + demoTokenTtlSeconds.seconds)
        return respond(
            apiJson.encodeToString(TokenResponse(token, demoTokenTtlSeconds)),
            HttpStatusCode.OK,
            jsonHeaders,
        )
    }

    private suspend fun MockRequestHandleScope.register(request: HttpRequestData): HttpResponseData {
        val registration = request.decode<RegisterRequest>() ?: return respond("", HttpStatusCode.BadRequest)
        val email = registration.email.trim().lowercase()
        if (!validEmail(email) || !exampleEmail.matches(email) || !validPassword(registration.password) ||
            (registration.displayName?.length ?: 0) > 32
        ) {
            return respond("", HttpStatusCode.BadRequest)
        }
        if (accounts.containsKey(email)) {
            return respond("", HttpStatusCode.Conflict)
        }

        val user = UserResponse(
            id = accounts.size + 1L,
            email = email,
            displayName = registration.displayName?.trim(),
            enabled = true,
            createdAt = demoTimestamp,
            updatedAt = demoTimestamp,
            roles = setOf(UserRole.USER),
        )
        accounts[email] = registration.password
        return respond(apiJson.encodeToString(user), HttpStatusCode.OK, jsonHeaders)
    }

    private suspend inline fun <reified T> HttpRequestData.decode(): T? = try {
        apiJson.decodeFromString(body.toByteArray().decodeToString())
    } catch (_: SerializationException) {
        null
    }
}

private val exampleEmail = Regex("[^@\\s]+@example\\.(com|org|net)")
private val emailFormat = Regex("[^@\\s]+@[^@\\s]+")

private fun validEmail(email: String): Boolean = email.length in 3..254 && emailFormat.matches(email)

private fun validPassword(password: String): Boolean = password.isNotBlank() && password.length in 8..32
