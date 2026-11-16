package com.elcomp.medicationorganizer.core.network

import com.elcomp.medicationorganizer.core.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal val apiJson = Json { ignoreUnknownKeys = true }

internal fun HttpClientConfig<*>.configureApiClient() {
    followRedirects = false
    install(ContentNegotiation) {
        json(apiJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}

internal class ApiClient(
    private val client: HttpClient,
    private val origin: String,
    private val session: SessionManager? = null,
) {
    suspend inline fun <reified Response> get(path: String, requiresSession: Boolean = false): Response =
        request<Unit, Response>(HttpMethod.Get, path, null, requiresSession)

    suspend inline fun <reified Response> post(path: String, requiresSession: Boolean = false): Response =
        request<Unit, Response>(HttpMethod.Post, path, null, requiresSession)

    suspend inline fun <reified Request, reified Response> post(
        path: String,
        body: Request,
        requiresSession: Boolean = false,
    ): Response = request(HttpMethod.Post, path, body, requiresSession)

    suspend inline fun <reified Request, reified Response> put(
        path: String,
        body: Request,
        requiresSession: Boolean = false,
    ): Response = request(HttpMethod.Put, path, body, requiresSession)

    suspend inline fun <reified Request, reified Response> patch(
        path: String,
        body: Request,
        requiresSession: Boolean = false,
    ): Response = request(HttpMethod.Patch, path, body, requiresSession)

    suspend fun delete(path: String, requiresSession: Boolean = false): Unit =
        request<Unit, Unit>(HttpMethod.Delete, path, null, requiresSession)

    private suspend inline fun <reified Request, reified Response> request(
        verb: HttpMethod,
        path: String,
        body: Request?,
        requiresSession: Boolean,
    ): Response {
        require(path.startsWith("/api/"))
        val lease = if (requiresSession) {
            session?.requireSession() ?: throw ApiException(ApiError.UNAUTHORIZED)
        } else {
            null
        }
        try {
            val response = client.request("$origin$path") {
                method = verb
                accept(ContentType.Application.Json)
                if (lease != null) header(HttpHeaders.Authorization, "Bearer ${lease.token}")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            if (lease != null && response.status == HttpStatusCode.Unauthorized) {
                session?.reject(lease)
            }
            response.checkStatus()
            val result: Response = response.body()
            if (lease != null) session?.ensureCurrent(lease)
            return result
        } catch (_: HttpRequestTimeoutException) {
            throw ApiException(ApiError.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            throw ApiException(ApiError.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            throw ApiException(ApiError.TIMEOUT)
        } catch (_: IOException) {
            throw ApiException(ApiError.NETWORK)
        } catch (_: ContentConvertException) {
            throw ApiException(ApiError.INVALID_RESPONSE)
        } catch (_: NoTransformationFoundException) {
            throw ApiException(ApiError.INVALID_RESPONSE)
        } catch (_: SerializationException) {
            throw ApiException(ApiError.INVALID_RESPONSE)
        }
    }
}

private fun HttpResponse.checkStatus() {
    val code = status.value
    val error = when (code) {
        in 200..299 -> return
        400, 422 -> ApiError.INVALID_REQUEST
        401 -> ApiError.UNAUTHORIZED
        403 -> ApiError.FORBIDDEN
        404 -> ApiError.NOT_FOUND
        409 -> ApiError.CONFLICT
        408, 504 -> ApiError.TIMEOUT
        429, in 500..599 -> ApiError.SERVER
        else -> ApiError.UNEXPECTED_STATUS
    }
    throw ApiException(error, code)
}
