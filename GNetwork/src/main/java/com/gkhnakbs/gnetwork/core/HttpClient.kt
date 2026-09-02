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
import com.gkhnakbs.gnetwork.auth.Authenticator
import com.gkhnakbs.gnetwork.progress.Progress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume

/**
 * Core HTTP client responsible for orchestrating network requests, interceptor pipelines,
 * serialization, and authentication handling.
 *
 * Built on standard Java [HttpURLConnection] and Kotlin Coroutines.
 *
 * @property defaultHeaders Common HTTP headers applied to all outgoing requests.
 * @property baseUrl Base URL prepended to relative request paths.
 * @property json Configured [Json] instance for serialization and deserialization.
 * @property interceptors Ordered list of [Interceptor] instances applied to every request.
 * @property sslConfig SSL/TLS security settings including custom trust managers and certificate pinning.
 * @property authenticator Authenticator invoked when encountering 401 Unauthorized responses.
 * @property connectTimeout Default connection timeout in milliseconds (defaults to 10,000 ms).
 * @property readTimeout Default socket read timeout in milliseconds (defaults to 20,000 ms).
 * @property callTimeout Default call-level timeout ceiling in milliseconds (defaults to 0L, disabled).
 *
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
    val authenticator: Authenticator = Authenticator.NONE,
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 20000,
    val callTimeout: Long = 0L,
) {
    /**
     * Executes the given [request] and deserializes the successful response into [T].
     *
     * @param request The [HttpRequest] specification to send.
     * @return [HttpResponse] representing either [HttpResponse.Success], [HttpResponse.Failure], or [HttpResponse.Error].
     */
    suspend inline fun <reified T> execute(request: HttpRequest): HttpResponse<T> {
        return executeWithSerializer(request, serializer<T>())
    }

    /**
     * Executes the request through the interceptor pipeline and parses the result with [serializer].
     */
    @PublishedApi
    internal suspend fun <T> executeWithSerializer(
        request: HttpRequest,
        serializer: KSerializer<T>,
    ): HttpResponse<T> = withContext(Dispatchers.IO) {
        val effectiveCallTimeout = request.callTimeout ?: callTimeout
        if (effectiveCallTimeout > 0L) {
            try {
                withTimeout(effectiveCallTimeout) {
                    executeChainAndParse(request, serializer)
                }
            } catch (e: TimeoutCancellationException) {
                HttpResponse.Error(
                    exception = TimeoutException("Call timed out after ${effectiveCallTimeout}ms"),
                    message = "Call timed out after ${effectiveCallTimeout}ms"
                )
            }
        } else {
            executeChainAndParse(request, serializer)
        }
    }

    private suspend fun <T> executeChainAndParse(
        request: HttpRequest,
        serializer: KSerializer<T>,
    ): HttpResponse<T> {
        val startRequest = requestWithBaseAndHeaders(request)
        val chain = RealInterceptorChain(
            interceptors = interceptors + TerminalInterceptor(::performNetworkCall),
            index = 0,
            request = startRequest
        )
        var raw = chain.proceed(startRequest)

        // 401 Unauthorized durumunda Authenticator ile yeniden deneme (tekrar döngüye girmemesi için 1 kez denenir)
        if (raw.statusCode == 401 && authenticator != Authenticator.NONE) {
            val retryRequest = authenticator.authenticate(startRequest, raw)
            if (retryRequest != null) {
                val retryChain = RealInterceptorChain(
                    interceptors = interceptors + TerminalInterceptor(::performNetworkCall),
                    index = 0,
                    request = retryRequest
                )
                raw = retryChain.proceed(retryRequest)
            }
        }

        return parseRawResponse(raw, serializer)
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
                val downloadListener = request.onDownloadProgress

                val bytes = if (stream != null) {
                    if (downloadListener != null) {
                        val contentLength = connection.contentLengthLong.takeIf { it >= 0 }
                            ?: headers.firstIgnoreCase("Content-Length")?.toLongOrNull()
                            ?: -1L
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        var bytesRead = 0L
                        stream.use { inStream ->
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                downloadListener(Progress(bytesRead, contentLength))
                            }
                        }
                        output.toByteArray()
                    } else {
                        stream.buffered().use { it.readBytes() }
                    }
                } else {
                    ByteArray(0)
                }

                cont.resume(
                    RawResponse(
                        statusCode = statusCode,
                        message = connection.responseMessage,
                        headers = headers,
                        body = bytes,
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                        // String ise direkt metin olarak döndür
                        serializer.descriptor.serialName == "kotlin.String" -> rawText as T
                        // ByteArray ise doğrudan ham baytları döndür (dosya/görsel indirimi)
                        serializer.descriptor.serialName == "kotlin.ByteArray" -> bytes as T
                        // Unit ise boş yanıt veya 204 No Content durumunu karşıla
                        serializer.descriptor.serialName == "kotlin.Unit" -> Unit as T
                        // JSON ise deserialize et
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

    /**
     * Opens and configures an [HttpURLConnection] for the specified [request].
     */
    @PublishedApi
    internal fun buildConnection(request: HttpRequest): HttpURLConnection {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection

        // HTTPS bağlantıları için SSL yapılandırması uygula
        if (connection is HttpsURLConnection) {
            applySSLConfig(connection)
        }

        with(connection) {
            requestMethod = request.method.name
            connectTimeout = request.connectTimeout ?: this@HttpClient.connectTimeout
            readTimeout = request.readTimeout ?: this@HttpClient.readTimeout
            doInput = true
            useCaches = false
            instanceFollowRedirects = true
            doOutput = (request.body != null || request.rawBody != null) && request.method.allowsBody
        }

        // Önce custom header'ları uygula, sonra eksikleri tamamla
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

        // Header isimleri case-insensitive karşılaştır
        val hasAcceptEncoding = connection.requestProperties.keys
            .any { it.equals("Accept-Encoding", ignoreCase = true) }

        if (!hasAcceptEncoding) {
            connection.setRequestProperty("Accept-Encoding", "gzip")
        }

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

    /**
     * Serializes and writes the request body to the connection's output stream.
     */
    @PublishedApi
    internal fun writeRequestBody(connection: HttpURLConnection, request: HttpRequest) {
        if (!request.method.allowsBody) return
        val bytes = request.rawBody ?: request.body?.toByteArray(Charsets.UTF_8) ?: return
        connection.doOutput = true
        val existingCT = connection.getRequestProperty("Content-Type")
        val desiredCT = request.contentType?.value ?: existingCT ?: "application/json"
        val finalCT = if (!desiredCT.contains("charset", ignoreCase = true) && !desiredCT.startsWith("multipart/", ignoreCase = true)) {
            "$desiredCT; charset=UTF-8"
        } else {
            desiredCT
        }
        if (existingCT == null || existingCT != finalCT) {
            connection.setRequestProperty("Content-Type", finalCT)
        }
        connection.setFixedLengthStreamingMode(bytes.size)
        val uploadListener = request.onUploadProgress
        if (uploadListener != null) {
            val totalBytes = bytes.size.toLong()
            val buffer = ByteArray(8192)
            var bytesWritten = 0L
            val inputStream = java.io.ByteArrayInputStream(bytes)
            connection.outputStream.buffered().use { output ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesWritten += read
                    uploadListener(Progress(bytesWritten, totalBytes))
                }
                output.flush()
            }
        } else {
            connection.outputStream.buffered().use { it.write(bytes); it.flush() }
        }
    }

    /**
     * Extracts response headers from the [connection] into a [ResponseHeaders] instance.
     */
    @PublishedApi
    internal fun parseResponseHeaders(connection: HttpURLConnection): ResponseHeaders {
        val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key!! }
        return ResponseHeaders(headers)
    }
}




