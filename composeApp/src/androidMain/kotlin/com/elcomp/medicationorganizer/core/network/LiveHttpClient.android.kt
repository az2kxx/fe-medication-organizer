package com.elcomp.medicationorganizer.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun liveHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        configure(this)
        engine {
            config {
                retryOnConnectionFailure(false)
            }
        }
    }
