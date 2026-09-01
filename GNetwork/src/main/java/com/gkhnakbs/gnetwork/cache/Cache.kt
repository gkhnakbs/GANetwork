package com.gkhnakbs.gnetwork.cache

import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.response.ResponseHeaders

/**
 * Cache policy controlling how an HTTP request interacts with the local cache.
 */
enum class CachePolicy {
    /**
     * Standard HTTP caching. Serves fresh responses from cache and revalidates
     * stale responses with conditional headers (ETag, If-None-Match).
     */
    DEFAULT,

    /**
     * Bypasses the cache completely, fetches the latest response from the network,
     * and updates the cache if the response is cacheable.
     */
    FORCE_NETWORK,

    /**
     * Only retrieves the response from the local cache.
     * Never performs a network call; returns an HTTP 504 Gateway Timeout if missing or stale.
     */
    FORCE_CACHE
}

/**
 * General cache interface for storing and retrieving HTTP responses.
 */
interface Cache {
    /**
     * Retrieves the cached response associated with [key], or null if not found.
     *
     * @param key Unique cache key (typically the request URL).
     * @return The stored [CachedResponse], or null.
     */
    fun get(key: String): CachedResponse?

    /**
     * Stores [response] in the cache under [key].
     *
     * @param key Unique cache key (typically the request URL).
     * @param response The response to store.
     */
    fun put(key: String, response: CachedResponse)

    /**
     * Removes the cached entry for [key].
     *
     * @param key The cache key to remove.
     * @return True if an entry was removed, false otherwise.
     */
    fun remove(key: String): Boolean

    /**
     * Clears all cached entries.
     */
    fun clear()

    /**
     * Current total size of all cached entries in bytes.
     */
    val size: Long

    /**
     * Maximum allowed size of the cache in bytes before eviction.
     */
    val maxSize: Long
}

/**
 * Snapshot of an HTTP response stored in the cache.
 *
 * @property statusCode The HTTP status code of the response.
 * @property message The HTTP status message.
 * @property headers The response headers.
 * @property body The raw response body bytes.
 * @property receivedAtMillis Epoch timestamp in milliseconds when this response was received.
 */
data class CachedResponse(
    val statusCode: Int,
    val message: String?,
    val headers: ResponseHeaders,
    val body: ByteArray,
    val receivedAtMillis: Long = System.currentTimeMillis(),
) {
    /**
     * The ETag header value if present.
     */
    val etag: String? get() = headers.firstIgnoreCase("ETag")

    /**
     * The Last-Modified header value if present.
     */
    val lastModified: String? get() = headers.firstIgnoreCase("Last-Modified")

    /**
     * Determines whether this cached response is still fresh based on `Cache-Control: max-age`.
     *
     * @param now Current epoch timestamp in milliseconds.
     * @return True if the response is within its freshness lifetime, false otherwise.
     */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean {
        val cacheControl = headers.firstIgnoreCase("Cache-Control") ?: return false
        if (cacheControl.contains("no-cache", ignoreCase = true) ||
            cacheControl.contains("no-store", ignoreCase = true)
        ) {
            return false
        }

        val maxAgeSeconds = extractMaxAgeSeconds(cacheControl) ?: return false
        val ageMillis = (now - receivedAtMillis).coerceAtLeast(0L)
        return ageMillis < (maxAgeSeconds * 1000L)
    }

    /**
     * Checks if the response explicitly forbids caching via `Cache-Control: no-store`.
     *
     * @return True if the response contains `no-store`, false otherwise.
     */
    fun isNoStore(): Boolean {
        val cacheControl = headers.firstIgnoreCase("Cache-Control") ?: return false
        return cacheControl.contains("no-store", ignoreCase = true)
    }

    /**
     * Converts this cached entry into a [RawResponse].
     */
    fun toRawResponse(): RawResponse = RawResponse(
        statusCode = statusCode,
        message = message,
        headers = headers,
        body = body
    )

    private fun extractMaxAgeSeconds(cacheControl: String): Long? {
        val directive = cacheControl.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("max-age=", ignoreCase = true) }
            ?: return null

        return directive.substringAfter("=").trim().toLongOrNull()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedResponse) return false

        if (statusCode != other.statusCode) return false
        if (message != other.message) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false
        if (receivedAtMillis != other.receivedAtMillis) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + receivedAtMillis.hashCode()
        return result
    }
}
