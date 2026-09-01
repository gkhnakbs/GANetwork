package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.retry.RetryConfig

/**
 * Immutable representation of an HTTP request.
 *
 * @property url The target URL or relative path.
 * @property method The HTTP method (GET, POST, PUT, DELETE).
 * @property headers Map of HTTP request header key-value pairs.
 * @property connectTimeout Connection timeout in milliseconds (defaults to 10,000 ms).
 * @property readTimeout Socket read timeout in milliseconds (defaults to 20,000 ms).
 * @property body Optional request payload string.
 * @property contentType Optional [ContentType] specifying the request body format.
 * @property retryConfig Optional per-request [RetryConfig] overriding the client's default retry behavior.
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
data class HttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 20000,
    val body: String? = null,
    val contentType: ContentType? = null,
    val retryConfig: RetryConfig? = null,
)