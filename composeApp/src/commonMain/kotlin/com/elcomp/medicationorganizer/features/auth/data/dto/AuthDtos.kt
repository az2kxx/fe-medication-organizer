package com.elcomp.medicationorganizer.features.auth.data.dto

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
class TokenResponse(
    val accessToken: String,
    val expiresInSeconds: Long,
)

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val displayName: String? = null,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val roles: Set<UserRole>,
)

@Serializable
enum class UserRole {
    USER,
    ADMIN,
}
