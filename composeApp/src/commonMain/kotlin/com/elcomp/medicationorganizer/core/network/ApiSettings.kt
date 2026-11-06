package com.elcomp.medicationorganizer.core.network

import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url

enum class MockScenario {
    NORMAL,
    EMPTY,
    SLOW,
    OFFLINE,
    API_ERROR,
}

sealed interface ApiSettings {
    data class Mock(val scenario: MockScenario = MockScenario.NORMAL) : ApiSettings

    data class Live(val baseUrl: String) : ApiSettings {
        init {
            require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) {
                "Specify an HTTP or HTTPS origin."
            }
            val authority = baseUrl.substringAfter("://").removeSuffix("/")
            require(authority.isNotBlank() && authority.none {
                it in "/\\@?#" || it.isWhitespace() || it.isISOControl()
            }) {
                "Specify only the API origin."
            }
            val url = try {
                Url(baseUrl)
            } catch (_: URLParserException) {
                throw IllegalArgumentException("Invalid API origin.")
            }
            require(url.host.isNotBlank() && url.specifiedPort in 0..65535) {
                "Invalid API origin."
            }
            require(url.protocol == URLProtocol.HTTPS || url.host in localHosts) {
                "Use HTTPS for remote API origins."
            }
        }
    }
}

private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")
