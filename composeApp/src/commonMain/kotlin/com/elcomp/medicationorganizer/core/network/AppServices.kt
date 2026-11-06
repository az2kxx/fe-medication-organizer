package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.core.session.SessionTokenStore
import com.elcomp.medicationorganizer.features.auth.data.AuthRepository
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import kotlin.time.Clock

class AppServices private constructor(
    val settings: ApiSettings,
    private val client: HttpClient,
    origin: String,
    sessionStore: SessionTokenStore?,
    clock: Clock,
    private val demoSession: DemoSession? = null,
) {
    val session = SessionManager(origin, sessionStore, clock) { token ->
        demoSession?.endSession(token)
    }
    private val api = ApiClient(client, origin, session)

    val auth = AuthRepository(api)
    val demo: DemoRepository? = demoSession?.let { DemoRepository(api) }

    suspend fun signIn(request: LoginRequest) {
        session.signIn { auth.login(request) }
    }

    internal suspend fun signInWithDemoAccount() {
        check(settings is ApiSettings.Mock)
        signIn(demoAccountLogin())
    }

    suspend fun resetDemo() {
        demoSession?.let { session.resetMock(it::reset) }
    }

    fun close() {
        client.close()
    }

    companion object {
        fun create(
            settings: ApiSettings = ApiSettings.Mock(),
            sessionStore: SessionTokenStore? = null,
        ): AppServices = create(settings, sessionStore, Clock.System)

        internal fun create(
            settings: ApiSettings,
            sessionStore: SessionTokenStore?,
            clock: Clock,
        ): AppServices = when (settings) {
            is ApiSettings.Mock -> {
                val demo = DemoSession(settings.scenario, clock)
                val client = HttpClient(MockEngine) {
                    configureApiClient()
                    engine {
                        addHandler { request -> with(demo) { handle(request) } }
                    }
                }
                AppServices(settings, client, "https://demo.invalid", null, clock, demo)
            }
            is ApiSettings.Live -> AppServices(
                settings,
                liveHttpClient { configureApiClient() },
                settings.baseUrl.trimEnd('/'),
                sessionStore,
                clock,
            )
        }
    }
}

internal expect fun liveHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient
