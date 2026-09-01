package com.gkhnakbs.gnetwork.cache

import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.interceptor.Interceptor
import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.response.ResponseHeaders

/**
 * Interceptor that handles HTTP caching according to standard Cache-Control and ETag rules.
 *
 * Supports:
 * - Direct cache hits for fresh responses without network roundtrips.
 * - Conditional requests via `If-None-Match` (ETag) and `If-Modified-Since` (Last-Modified).
 * - Automatic conversion of 304 Not Modified responses into full 200 responses with cached body.
 * - Per-request [CachePolicy] overrides ([CachePolicy.DEFAULT], [CachePolicy.FORCE_NETWORK], [CachePolicy.FORCE_CACHE]).
 * - Safe handling of `Cache-Control: no-store` to avoid persisting sensitive payloads.
 *
 * @property cache The [Cache] instance used for storage and retrieval.
 *
 * Created by Gökhan Akbaş.
 */
class CacheInterceptor(
    private val cache: Cache,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val request = chain.request

        // Caching is strictly limited to idempotent read operations (GET)
        if (request.method != HttpMethod.GET) {
            return chain.proceed(request)
        }

        val cacheKey = request.url
        val policy = request.cachePolicy

        return when (policy) {
            CachePolicy.FORCE_CACHE -> handleForceCache(cacheKey)
            CachePolicy.FORCE_NETWORK -> handleForceNetwork(chain, request, cacheKey)
            CachePolicy.DEFAULT -> handleDefault(chain, request, cacheKey)
        }
    }

    private fun handleForceCache(cacheKey: String): RawResponse {
        val cached = cache.get(cacheKey)
        return if (cached != null) {
            cached.toRawResponse()
        } else {
            RawResponse(
                statusCode = 504,
                message = "Unsatisfiable Request (only-if-cached)",
                headers = ResponseHeaders(),
                body = ByteArray(0)
            )
        }
    }

    private suspend fun handleForceNetwork(
        chain: Interceptor.Chain,
        request: HttpRequest,
        cacheKey: String,
    ): RawResponse {
        val networkResponse = chain.proceed(request)
        updateCacheIfCacheable(cacheKey, networkResponse)
        return networkResponse
    }

    private suspend fun handleDefault(
        chain: Interceptor.Chain,
        request: HttpRequest,
        cacheKey: String,
    ): RawResponse {
        val cached = cache.get(cacheKey)

        // 1. Fresh cache hit: Return immediately without touching the network
        if (cached != null && cached.isFresh()) {
            return cached.toRawResponse()
        }

        // 2. Stale cache or cache miss: Prepare network request with conditional headers
        val conditionalRequest = if (cached != null) {
            val conditionalHeaders = mutableMapOf<String, String>()
            cached.etag?.let { conditionalHeaders["If-None-Match"] = it }
            cached.lastModified?.let { conditionalHeaders["If-Modified-Since"] = it }
            request.copy(headers = request.headers + conditionalHeaders)
        } else {
            request
        }

        val networkResponse = chain.proceed(conditionalRequest)

        // 3. 304 Not Modified: Reuse cached body and merge updated headers
        if (networkResponse.statusCode == 304 && cached != null) {
            val mergedHeaders = mergeHeaders(cached.headers, networkResponse.headers)
            val refreshed = cached.copy(
                headers = mergedHeaders,
                receivedAtMillis = System.currentTimeMillis()
            )
            cache.put(cacheKey, refreshed)
            return RawResponse(
                statusCode = 200,
                message = "OK (from cache)",
                headers = mergedHeaders,
                body = cached.body
            )
        }

        // 4. Normal 2xx: Cache the fresh network response if allowed
        updateCacheIfCacheable(cacheKey, networkResponse)
        return networkResponse
    }

    private fun updateCacheIfCacheable(cacheKey: String, response: RawResponse) {
        if (response.statusCode in 200..299) {
            val cacheControl = response.headers.firstIgnoreCase("Cache-Control")
            if (cacheControl?.contains("no-store", ignoreCase = true) == true) {
                cache.remove(cacheKey)
                return
            }

            val entry = CachedResponse(
                statusCode = response.statusCode,
                message = response.message,
                headers = response.headers,
                body = response.body,
                receivedAtMillis = System.currentTimeMillis()
            )
            cache.put(cacheKey, entry)
        }
    }

    private fun mergeHeaders(cached: ResponseHeaders, fresh: ResponseHeaders): ResponseHeaders {
        val merged = cached.headers.toMutableMap()
        fresh.headers.forEach { (key, values) ->
            merged[key] = values
        }
        return ResponseHeaders(merged)
    }
}
