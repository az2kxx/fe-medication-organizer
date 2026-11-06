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
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

private const val demoEmail = "alex@example.com"
private const val demoPassword = "example-password"
private val demoTimestamp = Instant.parse("2026-11-02T08:00:00Z")
private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal class DemoSession(private val scenario: MockScenario) {
    private val lock = Mutex()
    private val routes = DemoRoutes(empty = scenario == MockScenario.EMPTY)
    private val accounts = mutableMapOf(demoEmail to demoPassword)

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
                else -> with(routes) { handle(request) }
            }
        }
    }

    suspend fun reset() = lock.withLock {
        accounts.clear()
        accounts[demoEmail] = demoPassword
        routes.reset()
    }

    private suspend fun MockRequestHandleScope.login(request: HttpRequestData): HttpResponseData {
        val credentials = request.decode<LoginRequest>() ?: return respond("", HttpStatusCode.BadRequest)
        val email = credentials.email.trim().lowercase()
        if (email.isBlank() || credentials.password.isBlank()) {
            return respond("", HttpStatusCode.BadRequest)
        }
        if (accounts[email] != credentials.password) {
            return respond("", HttpStatusCode.Unauthorized)
        }
        return respond(
            apiJson.encodeToString(TokenResponse("demo-token", 3600)),
            HttpStatusCode.OK,
            jsonHeaders,
        )
    }

    private suspend fun MockRequestHandleScope.register(request: HttpRequestData): HttpResponseData {
        val registration = request.decode<RegisterRequest>() ?: return respond("", HttpStatusCode.BadRequest)
        val email = registration.email.trim().lowercase()
        if (!exampleEmail.matches(email) || registration.password.isBlank()) {
            return respond("", HttpStatusCode.BadRequest)
        }
        if (accounts.containsKey(email)) {
            return respond("", HttpStatusCode.Conflict)
        }

        val user = UserResponse(
            id = accounts.size + 1L,
            email = email,
            displayName = registration.displayName,
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
