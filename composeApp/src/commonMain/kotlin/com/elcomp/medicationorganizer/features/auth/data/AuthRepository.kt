package com.elcomp.medicationorganizer.features.auth.data

import com.elcomp.medicationorganizer.core.network.ApiClient
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import com.elcomp.medicationorganizer.features.auth.data.dto.UserResponse

class AuthRepository internal constructor(private val api: ApiClient) {
    suspend fun login(request: LoginRequest): TokenResponse = api.post("/api/auth/login", request)

    suspend fun register(request: RegisterRequest): UserResponse = api.post("/api/auth/register", request)
}
