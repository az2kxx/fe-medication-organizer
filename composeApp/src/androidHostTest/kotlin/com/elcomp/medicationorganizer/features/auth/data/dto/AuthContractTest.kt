package com.elcomp.medicationorganizer.features.auth.data.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.Json

class AuthContractTest {
    @Test
    fun loginRequestDecodesAndKeepsTheWireFields() {
        val fixture = readFixture("login-request.json")
        val request = Json.decodeFromString<LoginRequest>(fixture)

        assertEquals("alex@example.com", request.email)
        assertEquals("example-password", request.password)
        assertEquals(Json.parseToJsonElement(fixture), Json.encodeToJsonElement(LoginRequest.serializer(), request))
    }

    @Test
    fun loginResponseDecodesTheTokenAndTtlInSeconds() {
        val response = Json.decodeFromString<TokenResponse>(readFixture("login-response.json"))

        assertEquals("fixture-only-token", response.accessToken)
        assertEquals(3600L, response.expiresInSeconds)
    }

    @Test
    fun registerRequestDecodesAndKeepsTheWireFields() {
        val fixture = readFixture("register-request.json")
        val request = Json.decodeFromString<RegisterRequest>(fixture)

        assertEquals("alex@example.com", request.email)
        assertEquals("example-password", request.password)
        assertEquals("Alex", request.displayName)
        assertEquals(Json.parseToJsonElement(fixture), Json.encodeToJsonElement(RegisterRequest.serializer(), request))
    }

    @Test
    fun registerRequestAcceptsAnOmittedDisplayName() {
        val fixture = readFixture("register-request-no-display-name.json")
        val request = Json.decodeFromString<RegisterRequest>(fixture)

        assertNull(request.displayName)
        assertEquals(Json.parseToJsonElement(fixture), Json.encodeToJsonElement(RegisterRequest.serializer(), request))
    }

    @Test
    fun registerResponseDecodesTheFullUserAndPreservesInstantPrecision() {
        val response = Json.decodeFromString<UserResponse>(readFixture("register-response.json"))

        assertEquals(
            UserResponse(
                id = 42L,
                email = "alex@example.com",
                displayName = "Alex",
                enabled = true,
                createdAt = Instant.parse("2026-10-30T08:05:00.123456Z"),
                updatedAt = Instant.parse("2026-10-30T08:05:00.123456Z"),
                roles = setOf(UserRole.USER),
            ),
            response,
        )
    }

    @Test
    fun registerResponseDecodesAnExplicitlyNullDisplayName() {
        val response = Json.decodeFromString<UserResponse>(readFixture("register-response-null-display-name.json"))

        assertNull(response.displayName)
        assertEquals("user-without-name@example.com", response.email)
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/auth/$name")) {
            "Missing auth contract fixture: $name"
        }.bufferedReader().use { it.readText() }
}
