package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.cache.CachePolicy
import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.progress.Progress
import com.gkhnakbs.gnetwork.retry.RetryConfig
import com.gkhnakbs.gnetwork.retry.RetryConfigBuilder
import java.net.URLEncoder
import kotlin.time.Duration

/**
 * DSL builder for configuring and assembling an [HttpRequest].
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
class HttpRequestBuilder {
    var url: String = ""
    var method: HttpMethod = HttpMethod.GET
    private val headers = mutableMapOf<String, String>()
    private val queryParams = mutableMapOf<String, String>()
    private var body: String? = null
    private var rawBody: ByteArray? = null
    private var contentType: ContentType? = null
    private var retryConfig: RetryConfig? = null
    private var cachePolicy: CachePolicy = CachePolicy.DEFAULT
    private var onUploadProgress: ((Progress) -> Unit)? = null
    private var onDownloadProgress: ((Progress) -> Unit)? = null
    private var connectTimeout: Int? = null
    private var readTimeout: Int? = null
    private var callTimeout: Long? = null

    /**
     * Adds an HTTP header.
     */
    fun header(key: String, value: String) {
        headers[key] = value
    }

    /**
     * Configures HTTP headers via a builder lambda.
     */
    fun headers(block: MutableMap<String, String>.() -> Unit) {
        headers.apply(block)
    }

    /**
     * Adds a query parameter key-value pair.
     */
    fun queryParam(key: String, value: String) {
        queryParams[key] = value
    }

    /**
     * Adds a query parameter converting the value to string.
     */
    fun queryParam(key: String, value: Any) {
        queryParams[key] = value.toString()
    }

    /**
     * Adds multiple query parameter pairs.
     */
    fun queryParams(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) ->
            queryParams[key] = value
        }
    }

    /**
     * Configures query parameters via a builder lambda.
     */
    fun queryParams(block: MutableMap<String, String>.() -> Unit) {
        queryParams.apply(block)
    }

    /**
     * Sets a raw JSON payload string as the request body.
     */
    fun jsonBody(json: String) {
        body = json
        rawBody = null
        contentType = ContentType.JSON
        header("Content-Type", ContentType.JSON.value)
    }

    /**
     * Builds and sets a JSON request body using a DSL builder.
     */
    fun jsonBody(block: JsonBodyBuilder.() -> Unit) {
        val builder = JsonBodyBuilder().apply(block)
        body = builder.build()
        rawBody = null
        contentType = ContentType.JSON
        header("Content-Type", ContentType.JSON.value)
    }

    /**
     * Sets URL-encoded form data as the request body from pairs.
     */
    fun formBody(vararg pairs: Pair<String, String>) {
        val formData = pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        body = formData
        rawBody = null
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    /**
     * Builds and sets URL-encoded form data using a DSL builder.
     */
    fun formBody(block: FormBodyBuilder.() -> Unit) {
        val builder = FormBodyBuilder().apply(block)
        body = builder.build()
        rawBody = null
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    /**
     * Sets plain text as the request body.
     */
    fun textBody(text: String) {
        body = text
        rawBody = null
        contentType = ContentType.TEXT_PLAIN
        header("Content-Type", ContentType.TEXT_PLAIN.value)
    }

    /**
     * Configures a multipart/form-data request body for uploading files, byte arrays, and form fields.
     *
     * Automatically serializes parts into an RFC-compliant binary payload and sets the
     * Content-Type header with the corresponding boundary.
     *
     * @param block DSL builder for adding fields, files, or byte arrays.
     */
    fun multipartBody(block: MultipartBodyBuilder.() -> Unit) {
        val builder = MultipartBodyBuilder().apply(block)
        val bytes = builder.build()
        this.rawBody = bytes
        this.body = null
        this.contentType = ContentType.MULTIPART_FORM_DATA
        header("Content-Type", "multipart/form-data; boundary=${builder.boundary}")
    }

    /**
     * Sets a raw binary byte array as the request body.
     *
     * @param bytes The raw byte array.
     * @param contentType The MIME content type (defaults to application/octet-stream).
     */
    fun binaryBody(bytes: ByteArray, contentType: String = "application/octet-stream") {
        this.rawBody = bytes
        this.body = null
        header("Content-Type", contentType)
    }

    /**
     * Enables and configures retry behavior for this specific request.
     *
     * @param maxRetries Maximum retry attempts.
     * @param initialDelayMs Initial delay before the first retry in milliseconds.
     * @param maxDelayMs Maximum ceiling for backoff delay.
     * @param backoffMultiplier Multiplier for exponential backoff.
     * @param jitter Whether to add random jitter.
     * @param retryOn Optional predicate to determine if a retry should be executed.
     */
    fun retry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        backoffMultiplier: Double = 2.0,
        jitter: Boolean = true,
        retryOn: ((HttpRequest, RawResponse) -> Boolean)? = null,
    ) {
        this.retryConfig = if (retryOn != null) {
            RetryConfig(maxRetries, initialDelayMs, maxDelayMs, backoffMultiplier, jitter, retryOn)
        } else {
            RetryConfig(maxRetries, initialDelayMs, maxDelayMs, backoffMultiplier, jitter)
        }
    }

    /**
     * Configures retry policy for this specific request via a DSL builder block.
     */
    fun retry(block: RetryConfigBuilder.() -> Unit) {
        this.retryConfig = RetryConfigBuilder().apply(block).build()
    }

    /**
     * Explicitly disables retrying for this request, overriding any client-level default retry policy.
     */
    fun noRetry() {
        this.retryConfig = RetryConfig.NO_RETRY
    }

    /**
     * Sets the [CachePolicy] for this specific request.
     */
    fun cachePolicy(policy: CachePolicy) {
        this.cachePolicy = policy
    }

    /**
     * Forces the request to bypass the cache and fetch fresh data from the network.
     */
    fun forceNetwork() {
        this.cachePolicy = CachePolicy.FORCE_NETWORK
    }

    /**
     * Forces the request to only return from cache, failing with 504 if not cached.
     */
    fun forceCache() {
        this.cachePolicy = CachePolicy.FORCE_CACHE
    }

    /**
     * Sets a progress callback listener for tracking upload progress of request body or multipart data.
     *
     * @param listener Lambda periodically invoked with [Progress] details (bytesTransferred, totalBytes, percentage).
     */
    fun onUploadProgress(listener: (Progress) -> Unit) {
        this.onUploadProgress = listener
    }

    /**
     * Sets a progress callback listener for tracking download progress of response payload.
     *
     * @param listener Lambda periodically invoked with [Progress] details (bytesTransferred, totalBytes, percentage).
     */
    fun onDownloadProgress(listener: (Progress) -> Unit) {
        this.onDownloadProgress = listener
    }

    /**
     * Sets the connection timeout for this specific request in milliseconds.
     *
     * @param ms Connection timeout in milliseconds.
     */
    fun connectTimeout(ms: Int) {
        this.connectTimeout = ms
    }

    /**
     * Sets the connection timeout for this specific request using [Duration].
     *
     * @param duration Connection timeout duration.
     */
    fun connectTimeout(duration: Duration) {
        this.connectTimeout = duration.inWholeMilliseconds.toInt()
    }

    /**
     * Sets the socket read timeout for this specific request in milliseconds.
     *
     * @param ms Read timeout in milliseconds.
     */
    fun readTimeout(ms: Int) {
        this.readTimeout = ms
    }

    /**
     * Sets the socket read timeout for this specific request using [Duration].
     *
     * @param duration Read timeout duration.
     */
    fun readTimeout(duration: Duration) {
        this.readTimeout = duration.inWholeMilliseconds.toInt()
    }

    /**
     * Sets the overall call-level timeout ceiling for this specific request in milliseconds.
     *
     * @param ms Call timeout in milliseconds (0 to disable).
     */
    fun callTimeout(ms: Long) {
        this.callTimeout = ms
    }

    /**
     * Sets the overall call-level timeout ceiling for this specific request using [Duration].
     *
     * @param duration Call timeout duration.
     */
    fun callTimeout(duration: Duration) {
        this.callTimeout = duration.inWholeMilliseconds
    }

    /**
     * Validates and builds the immutable [HttpRequest].
     */
    fun build(): HttpRequest {
        require(url.isNotEmpty()) { "URL cannot be empty" }

        val finalUrl = buildFullUrl(url, queryParams)

        return HttpRequest(
            url = finalUrl,
            method = method,
            headers = headers.toMap(),
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            callTimeout = callTimeout,
            body = body,
            rawBody = rawBody,
            contentType = contentType,
            retryConfig = retryConfig,
            cachePolicy = cachePolicy,
            onUploadProgress = onUploadProgress,
            onDownloadProgress = onDownloadProgress,
        )
    }

    private fun buildFullUrl(url: String, queryParams: Map<String, String> = emptyMap()): String {
        return if (queryParams.isNotEmpty()) {
            val queryString = queryParams.entries.joinToString("&") { (key, value) ->
                val encKey = URLEncoder.encode(key, "UTF-8")
                // Preserve commas for APIs requiring unencoded commas (e.g. open-meteo)
                val encVal = URLEncoder.encode(value, "UTF-8").replace("%2C", ",")
                "${encKey}=${encVal}"
            }
            if (url.contains("?")) {
                "$url&$queryString"
            } else {
                "$url?$queryString"
            }
        } else {
            url
        }
    }
}

/**
 * DSL builder for creating JSON object payloads.
 */
class JsonBodyBuilder {
    private val fields = mutableMapOf<String, Any?>()

    /**
     * Infix helper to assign a field value to a key.
     */
    infix fun String.to(value: Any?) {
        fields[this] = value
    }

    /**
     * Sets a field key and value.
     */
    fun field(key: String, value: Any?) {
        fields[key] = value
    }

    /**
     * Serializes fields into a formatted JSON string.
     */
    fun build(): String {
        val jsonFields = fields.entries.joinToString(",\n  ") { (key, value) ->
            val jsonValue = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is Number, is Boolean -> value.toString()
                else -> "\"$value\""
            }
            "\"$key\": $jsonValue"
        }
        return "{\n  $jsonFields\n}"
    }
}

/**
 * DSL builder for creating application/x-www-form-urlencoded payloads.
 */
class FormBodyBuilder {
    private val fields = mutableListOf<Pair<String, String>>()

    /**
     * Adds a form field key-value pair.
     */
    fun field(key: String, value: String) {
        fields.add((key to value))
    }

    /**
     * Encodes and joins fields into a form URL-encoded string.
     */
    fun build(): String {
        return fields.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
    }
}