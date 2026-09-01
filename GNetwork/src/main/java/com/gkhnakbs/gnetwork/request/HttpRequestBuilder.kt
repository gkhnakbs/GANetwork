package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.cache.CachePolicy
import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.retry.RetryConfig
import com.gkhnakbs.gnetwork.retry.RetryConfigBuilder
import java.net.URLEncoder

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
    private var contentType: ContentType? = null
    private var retryConfig: RetryConfig? = null
    private var cachePolicy: CachePolicy = CachePolicy.DEFAULT

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
        contentType = ContentType.JSON
        header("Content-Type", ContentType.JSON.value)
    }

    /**
     * Builds and sets a JSON request body using a DSL builder.
     */
    fun jsonBody(block: JsonBodyBuilder.() -> Unit) {
        val builder = JsonBodyBuilder().apply(block)
        body = builder.build()
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
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    /**
     * Builds and sets URL-encoded form data using a DSL builder.
     */
    fun formBody(block: FormBodyBuilder.() -> Unit) {
        val builder = FormBodyBuilder().apply(block)
        body = builder.build()
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    /**
     * Sets plain text as the request body.
     */
    fun textBody(text: String) {
        body = text
        contentType = ContentType.TEXT_PLAIN
        header("Content-Type", ContentType.TEXT_PLAIN.value)
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
     * Validates and builds the immutable [HttpRequest].
     */
    fun build(): HttpRequest {
        require(url.isNotEmpty()) { "URL cannot be empty" }

        val finalUrl = buildFullUrl(url, queryParams)

        return HttpRequest(
            url = finalUrl,
            method = method,
            headers = headers.toMap(),
            body = body,
            contentType = contentType,
            retryConfig = retryConfig,
            cachePolicy = cachePolicy,
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