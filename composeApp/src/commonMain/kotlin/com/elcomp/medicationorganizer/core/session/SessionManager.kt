package com.elcomp.medicationorganizer.core.session

import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.features.auth.data.dto.TokenResponse
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface SessionState {
    data object Restoring : SessionState
    data class SignedOut(val reason: SignOutReason? = null) : SessionState
    data class SignedIn(val generation: Long) : SessionState
}

enum class SignOutReason { MANUAL, EXPIRED, UNAUTHORIZED, STORAGE_ERROR }

internal class SessionLease(val token: String, val expiresAt: Instant, val generation: Long)

class SessionManager internal constructor(
    private val origin: String,
    private val store: SessionTokenStore?,
    private val clock: Clock,
    private val onSessionEnded: suspend (String) -> Unit = {},
) {
    private val lock = Mutex()
    private val currentState = MutableStateFlow<SessionState>(
        if (store == null) SessionState.SignedOut() else SessionState.Restoring,
    )
    private var active: SessionLease? = null
    private var generation = 0L

    val state: StateFlow<SessionState> = currentState.asStateFlow()

    suspend fun restore(): Unit = lock.withLock {
        if (currentState.value != SessionState.Restoring) return@withLock
        val bytes = try {
            store?.read()
        } catch (_: SessionStorageException) {
            currentState.value = SessionState.SignedOut(SignOutReason.STORAGE_ERROR)
            return@withLock
        }

        if (bytes == null) {
            currentState.value = SessionState.SignedOut()
            return@withLock
        }
        val saved = try {
            Json.decodeFromString<StoredSession>(bytes.decodeToString())
        } catch (_: IllegalArgumentException) {
            null
        }
        when {
            saved == null || !validToken(saved.token) -> endLocked(SignOutReason.STORAGE_ERROR)
            saved.origin != origin -> currentState.value = SessionState.SignedOut()
            saved.expiresAt <= clock.now() -> endLocked(SignOutReason.EXPIRED)
            else -> activate(saved.token, saved.expiresAt)
        }
    }

    suspend fun monitorExpiry() {
        state.collectLatest { signedIn ->
            if (signedIn !is SessionState.SignedIn) return@collectLatest
            while (true) {
                val wait = lock.withLock {
                    val lease = active?.takeIf { it.generation == signedIn.generation } ?: return@withLock null
                    val remaining = lease.expiresAt - clock.now()
                    if (remaining <= Duration.ZERO) {
                        endLocked(SignOutReason.EXPIRED)
                        null
                    } else {
                        remaining
                    }
                } ?: break
                delay(wait)
            }
        }
    }

    suspend fun signOut(): Unit = lock.withLock {
        endLocked(SignOutReason.MANUAL, keepSessionOnStorageFailure = true)
    }

    internal suspend fun signIn(requestToken: suspend () -> TokenResponse): Unit = lock.withLock {
        val response = requestToken()
        val ttl = response.expiresInSeconds.seconds
        val now = clock.now()
        if (!validToken(response.accessToken) || !ttl.isFinite() || ttl <= Duration.ZERO) {
            throw ApiException(ApiError.INVALID_RESPONSE)
        }
        val expiresAt = now + ttl
        if (expiresAt <= now) throw ApiException(ApiError.INVALID_RESPONSE)

        withContext(NonCancellable) {
            store?.write(Json.encodeToString(StoredSession(origin, response.accessToken, expiresAt)).encodeToByteArray())
            activate(response.accessToken, expiresAt)
        }
    }

    internal suspend fun requireSession(): SessionLease = lock.withLock {
        val lease = active ?: throw ApiException(ApiError.UNAUTHORIZED)
        if (lease.expiresAt <= clock.now()) {
            endLocked(SignOutReason.EXPIRED)
            throw ApiException(ApiError.UNAUTHORIZED)
        }
        lease
    }

    internal suspend fun reject(lease: SessionLease): Unit = lock.withLock {
        if (active === lease) endLocked(SignOutReason.UNAUTHORIZED)
    }

    internal suspend fun ensureCurrent(lease: SessionLease): Unit = lock.withLock {
        if (active !== lease) throw ApiException(ApiError.UNAUTHORIZED)
        if (lease.expiresAt <= clock.now()) {
            endLocked(SignOutReason.EXPIRED)
            throw ApiException(ApiError.UNAUTHORIZED)
        }
    }

    internal suspend fun resetMock(reset: suspend () -> Unit): Unit = lock.withLock {
        withContext(NonCancellable) {
            reset()
            active = null
            currentState.value = SessionState.SignedOut(SignOutReason.MANUAL)
        }
    }

    private fun activate(token: String, expiresAt: Instant) {
        val lease = SessionLease(token, expiresAt, ++generation)
        active = lease
        currentState.value = SessionState.SignedIn(lease.generation)
    }

    private suspend fun endLocked(reason: SignOutReason, keepSessionOnStorageFailure: Boolean = false) {
        withContext(NonCancellable) {
            val effectiveReason = try {
                store?.clear()
                reason
            } catch (failure: SessionStorageException) {
                if (keepSessionOnStorageFailure) throw failure
                SignOutReason.STORAGE_ERROR
            }
            active?.let { onSessionEnded(it.token) }
            active = null
            currentState.value = SessionState.SignedOut(effectiveReason)
        }
    }
}

@Serializable
private class StoredSession(val origin: String, val token: String, val expiresAt: Instant)

private val bearerTokenPattern = Regex("[A-Za-z0-9._~+/=-]+")

private fun validToken(token: String): Boolean = bearerTokenPattern.matches(token)
