package com.elcomp.medicationorganizer.features.profile.data

import com.elcomp.medicationorganizer.core.network.ApiClient
import kotlinx.serialization.Serializable

/** Provisional profile contract. AppServices exposes this repository only in MOCK. */
class ProfileRepository internal constructor(private val api: ApiClient) {
    suspend fun profile(): ProfileResponse = api.get("/api/users/me", requiresSession = true)

    suspend fun updateDisplayName(displayName: String): ProfileResponse =
        api.patch("/api/users/me", UpdateDisplayNameRequest(displayName), requiresSession = true)

    suspend fun preferences(): AccessibilityPreferences = api.get("/api/users/me/preferences", requiresSession = true)

    suspend fun savePreferences(value: AccessibilityPreferences): AccessibilityPreferences =
        api.put("/api/users/me/preferences", value, requiresSession = true)
}

@Serializable
data class ProfileResponse(val id: Long, val email: String, val displayName: String?)

@Serializable
internal data class UpdateDisplayNameRequest(val displayName: String)

@Serializable
data class AccessibilityPreferences(val simplifiedMode: Boolean, val textScale: Int, val highContrast: Boolean) {
    companion object {
        val Default = AccessibilityPreferences(false, 100, false)
        internal val SupportedTextScales = 100..200
    }
}
