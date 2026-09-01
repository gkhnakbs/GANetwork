package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.cache.CachePolicy
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
 * @property rawBody Optional raw binary request payload (e.g. multipart/form-data or binary stream).
 * @property contentType Optional [ContentType] specifying the request body format.
 * @property retryConfig Optional per-request [RetryConfig] overriding the client's default retry behavior.
 * @property cachePolicy Policy controlling local cache interaction for this request (defaults to [CachePolicy.DEFAULT]).
 * @property onUploadProgress Optional callback periodically receiving [Progress] updates during request body upload.
 * @property onDownloadProgress Optional callback periodically receiving [Progress] updates during response download.
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
    val rawBody: ByteArray? = null,
    val contentType: ContentType? = null,
    val retryConfig: RetryConfig? = null,
    val cachePolicy: CachePolicy = CachePolicy.DEFAULT,
    val onUploadProgress: ((com.gkhnakbs.gnetwork.progress.Progress) -> Unit)? = null,
    val onDownloadProgress: ((com.gkhnakbs.gnetwork.progress.Progress) -> Unit)? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false

        if (url != other.url) return false
        if (method != other.method) return false
        if (headers != other.headers) return false
        if (connectTimeout != other.connectTimeout) return false
        if (readTimeout != other.readTimeout) return false
        if (body != other.body) return false
        if (rawBody != null) {
            if (other.rawBody == null) return false
            if (!rawBody.contentEquals(other.rawBody)) return false
        } else if (other.rawBody != null) return false
        if (contentType != other.contentType) return false
        if (retryConfig != other.retryConfig) return false
        if (cachePolicy != other.cachePolicy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + connectTimeout
        result = 31 * result + readTimeout
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (rawBody?.contentHashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (retryConfig?.hashCode() ?: 0)
        result = 31 * result + cachePolicy.hashCode()
        return result
    }
}