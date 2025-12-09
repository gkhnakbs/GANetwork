package com.gkhnakbs.gnetwork.core

import com.gkhnakbs.gnetwork.core.HttpMethod.Companion.allowsBody
import com.gkhnakbs.gnetwork.interceptor.Interceptor
import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.interceptor.RealInterceptorChain
import com.gkhnakbs.gnetwork.interceptor.TerminalInterceptor
import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.response.HttpResponse
import com.gkhnakbs.gnetwork.response.ResponseHeaders
import com.gkhnakbs.gnetwork.response.contentCharset
import com.gkhnakbs.gnetwork.response.isJson
import com.gkhnakbs.gnetwork.response.wrapIfCompressed
import com.gkhnakbs.gnetwork.ssl.SSLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

class HttpClient(
    val defaultHeaders: Map<String, String> = emptyMap(),
    val baseUrl: String = "",
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    },
    private val interceptors: List<Interceptor> = emptyList(),
    private val sslConfig: SSLConfig = SSLConfig.default(),
) {
    suspend inline fun <reified T> execute(request: HttpRequest): HttpResponse<T> {
        return executeWithSerializer(request, serializer<T>())
    }

    @PublishedApi
    internal suspend fun <T> executeWithSerializer(
        request: HttpRequest,
        serializer: KSerializer<T>,
    ): HttpResponse<T> = withContext(Dispatchers.IO) {
        val startRequest = requestWithBaseAndHeaders(request)
        val chain = RealInterceptorChain(
            interceptors = interceptors + TerminalInterceptor(::performNetworkCall),
            index = 0,
            request = startRequest
        )
        val raw = chain.proceed(startRequest)
        return@withContext parseRawResponse(raw, serializer)
    }

    private fun requestWithBaseAndHeaders(request: HttpRequest): HttpRequest {
        val fullUrl = buildFullUrl(request.url)
        val merged = (defaultHeaders + request.headers)
        return request.copy(url = fullUrl, headers = merged)
    }

    private fun buildFullUrl(url: String): String {
        if (baseUrl.isNotEmpty() && !url.startsWith("http", ignoreCase = true)) {
            return URI(baseUrl).resolve(url).toString()
        }
        return url
    }

    private suspend fun performNetworkCall(request: HttpRequest): RawResponse =
        suspendCancellableCoroutine { cont ->
            var connection: HttpURLConnection? = null
            cont.invokeOnCancellation { connection?.disconnect() }
            try {
                connection = buildConnection(request)
                writeRequestBody(connection, request)

                val statusCode = connection.responseCode
                val headers = parseResponseHeaders(connection)

                val input =
                    runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
                val stream = if (input != null) wrapIfCompressed(input, headers) else null
                val bytes = stream?.buffered()?.use { it.readBytes() } ?: ByteArray(0)

                cont.resume(
                    RawResponse(
                        statusCode = statusCode,
                        message = connection.responseMessage,
                        headers = headers,
                        body = bytes,
                    )
                )
            } catch (e: Exception) {
                cont.resume(
                    RawResponse(
                        statusCode = -1,
                        message = e.message,
                        headers = ResponseHeaders(),
                        body = ByteArray(0)
                    )
                )
            } finally {
                connection?.disconnect()
            }
        }

    private fun <T> parseRawResponse(
        raw: RawResponse,
        serializer: KSerializer<T>,
    ): HttpResponse<T> {
        val statusCode = raw.statusCode
        val headers = raw.headers
        val bytes = raw.body
        val charset = headers.contentCharset() ?: Charsets.UTF_8
        val rawText = bytes.toString(charset)

        return when (statusCode) {
            in 200..299 -> {
                try {
                    val body: T = when {
                        // String ise direkt döndür
                        serializer.descriptor.serialName == "kotlin.String" -> rawText as T
                        // JSON ise deserialize
                        headers.isJson() -> json.decodeFromString(serializer, rawText)
                        else -> throw IllegalStateException("Unsupported content type for ${serializer.descriptor.serialName}")
                    }
                    HttpResponse.Success(
                        body = body,
                        statusCode = statusCode,
                        headers = headers,
                        rawResponse = rawText
                    )
                } catch (e: Exception) {
                    HttpResponse.Error(e)
                }
            }
            -1 -> {
                HttpResponse.Error(exception = Exception(raw.message ?: "Network error"))
            }
            else -> {
                HttpResponse.Failure(
                    statusCode = statusCode,
                    errorMessage = raw.message ?: "HTTP Error",
                    errorBody = rawText,
                    headers = headers
                )
            }
        }
    }

    @PublishedApi
    internal fun buildConnection(request: HttpRequest): HttpURLConnection {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection

        // HTTPS bağlantıları için SSL yapılandırması uygula
        if (connection is HttpsURLConnection) {
            applySSLConfig(connection)
        }

        with(connection) {
            requestMethod = request.method.name
            connectTimeout = request.connectTimeout
            readTimeout = request.readTimeout
            doInput = true
            instanceFollowRedirects = true
            doOutput = request.body != null && request.method.allowsBody
            if (getRequestProperty("Accept-Encoding") == null) {
                setRequestProperty("Accept-Encoding", "gzip")
            }
        }
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        return connection
    }

    private fun applySSLConfig(connection: HttpsURLConnection) {
        // SSLSocketFactory uygula
        sslConfig.sslSocketFactory?.let { factory ->
            connection.sslSocketFactory = factory
        }

        // HostnameVerifier uygula
        sslConfig.hostnameVerifier?.let { verifier ->
            connection.hostnameVerifier = verifier
        }

        // Bağlantı yapıldıktan sonra certificate pinning kontrolü
        sslConfig.certificatePinner?.let { pinner ->
            connection.connect()
            try {
                val certificates = connection.serverCertificates?.toList() ?: emptyList()
                pinner.check(connection.url.host, certificates)
            } catch (e: SSLPeerUnverifiedException) {
                connection.disconnect()
                throw e
            }
        }
    }

    @PublishedApi
    internal fun writeRequestBody(connection: HttpURLConnection, request: HttpRequest) {
        val body = request.body ?: return
        if (!request.method.allowsBody) return
        val bytes = body.toByteArray(Charsets.UTF_8)
        connection.doOutput = true
        val existingCT = connection.getRequestProperty("Content-Type")
        val desiredCT = request.contentType?.value ?: existingCT ?: "application/json"
        val finalCT = if (!desiredCT.contains(
                "charset",
                ignoreCase = true
            )
        ) "$desiredCT; charset=UTF-8" else desiredCT
        if (existingCT == null || existingCT != finalCT) connection.setRequestProperty(
            "Content-Type",
            finalCT
        )
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.buffered().use { it.write(bytes); it.flush() }
    }

    @PublishedApi
    internal fun parseResponseHeaders(connection: HttpURLConnection): ResponseHeaders {
        val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key!! }
        return ResponseHeaders(headers)
    }
}




