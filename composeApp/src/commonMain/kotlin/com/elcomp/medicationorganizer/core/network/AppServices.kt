package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.core.demo.DemoRepository
import com.elcomp.medicationorganizer.features.auth.data.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine

class AppServices private constructor(
    val settings: ApiSettings,
    private val client: HttpClient,
    origin: String,
    private val demoSession: DemoSession? = null,
) {
    private val api = ApiClient(client, origin)

    val auth = AuthRepository(api)
    val demo: DemoRepository? = demoSession?.let { DemoRepository(api) }

    suspend fun resetDemo() {
        demoSession?.reset()
    }

    fun close() {
        client.close()
    }

    companion object {
        fun create(settings: ApiSettings = ApiSettings.Mock()): AppServices = when (settings) {
            is ApiSettings.Mock -> {
                val session = DemoSession(settings.scenario)
                val client = HttpClient(MockEngine) {
                    configureApiClient()
                    engine {
                        addHandler { request -> with(session) { handle(request) } }
                    }
                }
                AppServices(settings, client, "https://demo.invalid", session)
            }
            is ApiSettings.Live -> AppServices(
                settings,
                liveHttpClient { configureApiClient() },
                settings.baseUrl.trimEnd('/'),
            )
        }
    }
}

internal expect fun liveHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient
