package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.core.session.SessionManager
import com.elcomp.medicationorganizer.core.session.SessionTokenStore
import com.elcomp.medicationorganizer.features.auth.data.AuthRepository
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerAssignmentsRepository
import com.elcomp.medicationorganizer.features.organizers.data.OrganizerCatalogRepository
import com.elcomp.medicationorganizer.features.profile.data.DemoPreferencesStore
import com.elcomp.medicationorganizer.features.profile.data.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val profile: ProfileRepository? = demoSession?.let { ProfileRepository(api) }
    val organizers: OrganizerCatalogRepository? = demoSession?.let { OrganizerCatalogRepository(api) }
    val assignments: OrganizerAssignmentsRepository? = demoSession?.let { OrganizerAssignmentsRepository(api) }
    val demoRevision: StateFlow<Long> = demoSession?.revision ?: MutableStateFlow(0L).asStateFlow()

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
            demoPreferencesStore: DemoPreferencesStore? = null,
        ): AppServices = create(settings, sessionStore, Clock.System, demoPreferencesStore)

        internal fun create(
            settings: ApiSettings,
            sessionStore: SessionTokenStore?,
            clock: Clock,
            demoPreferencesStore: DemoPreferencesStore? = null,
        ): AppServices = when (settings) {
            is ApiSettings.Mock -> {
                val demo = DemoSession(settings.scenario, clock, demoPreferencesStore)
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
