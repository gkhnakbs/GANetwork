package com.gkhnakbs.gnetwork.core

import com.gkhnakbs.gnetwork.auth.Authenticator
import com.gkhnakbs.gnetwork.auth.BearerTokenAuthenticator
import com.gkhnakbs.gnetwork.cache.Cache
import com.gkhnakbs.gnetwork.cache.CacheInterceptor
import com.gkhnakbs.gnetwork.cache.DiskLruCache
import com.gkhnakbs.gnetwork.cache.MemoryLruCache
import com.gkhnakbs.gnetwork.retry.RetryConfig
import com.gkhnakbs.gnetwork.retry.RetryConfigBuilder
import com.gkhnakbs.gnetwork.retry.RetryInterceptor
import com.gkhnakbs.gnetwork.ssl.SSLConfig
import com.gkhnakbs.gnetwork.ssl.SSLConfigBuilder
import java.io.File
import kotlin.time.Duration

/**
 * Builder class for constructing and configuring [HttpClient] instances.
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
class HttpClientBuilder {
    private val defaultHeaders = mutableMapOf<String, String>()
    private val interceptors = mutableListOf<com.gkhnakbs.gnetwork.interceptor.Interceptor>()
    var baseUrl: String = ""
    private var sslConfig: SSLConfig = SSLConfig.default()
    private var authenticator: Authenticator = Authenticator.NONE
    private var retryConfig: RetryConfig? = null
    private var cache: Cache? = null
    private var connectTimeout: Int = 10000
    private var readTimeout: Int = 20000
    private var callTimeout: Long = 0L

    /**
     * Configures default HTTP headers applied to all outgoing requests.
     */
    fun headers(block: MutableMap<String, String>.() -> Unit) {
        defaultHeaders.apply(block)
    }

    /**
     * Appends an [interceptor] to the execution pipeline.
     */
    fun addInterceptor(interceptor: com.gkhnakbs.gnetwork.interceptor.Interceptor) = apply {
        interceptors += interceptor
    }

    /**
     * Configures the list of interceptors via a builder block.
     */
    fun interceptors(block: MutableList<com.gkhnakbs.gnetwork.interceptor.Interceptor>.() -> Unit) =
        apply {
            interceptors.apply(block)
        }

    /**
     * Sets a custom [Authenticator] for handling 401 Unauthorized responses.
     */
    fun authenticator(authenticator: Authenticator) = apply {
        this.authenticator = authenticator
    }

    /**
     * Configures a Mutex-protected [BearerTokenAuthenticator].
     *
     * @param headerName Name of the auth header (defaults to "Authorization").
     * @param tokenPrefix Token scheme prefix (defaults to "Bearer ").
     * @param currentToken Optional supplier for the current cached token to avoid duplicate refresh calls.
     * @param onAuthFailed Optional suspend callback triggered when token refresh permanently fails or returns null.
     * @param onRefreshToken Suspend callback to fetch a new token given the expired token.
     */
    fun tokenAuthenticator(
        headerName: String = "Authorization",
        tokenPrefix: String = "Bearer ",
        currentToken: (suspend () -> String?)? = null,
        onAuthFailed: (suspend () -> Unit)? = null,
        onRefreshToken: suspend (expiredToken: String?) -> String?,
    ) = apply {
        this.authenticator = BearerTokenAuthenticator(
            headerName = headerName,
            tokenPrefix = tokenPrefix,
            currentToken = currentToken,
            onAuthFailed = onAuthFailed,
            onRefreshToken = onRefreshToken,
        )
    }

    /**
     * Convenience overload configuring a token authenticator with just an [onRefreshToken] block.
     */
    fun tokenAuthenticator(
        onRefreshToken: suspend (expiredToken: String?) -> String?,
    ) = tokenAuthenticator(currentToken = null, onAuthFailed = null, onRefreshToken = onRefreshToken)

    /**
     * Sets the default [RetryConfig] applied to requests that do not specify their own retry policy.
     */
    fun retryConfig(config: RetryConfig) = apply {
        this.retryConfig = config
    }

    /**
     * Configures the default [RetryConfig] using a DSL builder block.
     */
    fun retryConfig(block: RetryConfigBuilder.() -> Unit) = apply {
        this.retryConfig = RetryConfigBuilder().apply(block).build()
    }

    /**
     * Sets a custom [Cache] implementation for HTTP response caching (disabled by default).
     */
    fun cache(cache: Cache) = apply {
        this.cache = cache
    }

    /**
     * Enables an in-memory LRU cache with the specified byte size limit (defaults to 10 MB).
     *
     * @param maxSizeBytes Maximum cache size in bytes (defaults to 10 MB).
     */
    fun memoryCache(maxSizeBytes: Long = 10 * 1024 * 1024L) = apply {
        this.cache = MemoryLruCache(maxSizeBytes)
    }

    /**
     * Enables a persistent disk-based LRU cache stored in [directory].
     *
     * Cached responses survive application restarts and device reboots.
     *
     * @param directory File directory where cache files are stored (e.g. `File(context.cacheDir, "http_cache")`).
     * @param maxSizeBytes Maximum cache storage capacity in bytes (defaults to 50 MB).
     */
    fun diskCache(
        directory: File,
        maxSizeBytes: Long = 50 * 1024 * 1024L,
    ) = apply {
        this.cache = DiskLruCache(directory, maxSizeBytes)
    }

    /**
     * Sets the [SSLConfig] for HTTPS connections.
     */
    fun sslConfig(config: SSLConfig) = apply {
        this.sslConfig = config
    }

    /**
     * Configures [SSLConfig] using a DSL builder block.
     */
    fun sslConfig(block: SSLConfigBuilder.() -> Unit) = apply {
        this.sslConfig = SSLConfigBuilder().apply(block).build()
    }

    /**
     * Sets the default connection timeout in milliseconds for all requests (defaults to 10,000 ms).
     */
    fun connectTimeout(ms: Int) = apply {
        this.connectTimeout = ms
    }

    /**
     * Sets the default connection timeout using [Duration] for all requests.
     */
    fun connectTimeout(duration: Duration) = apply {
        this.connectTimeout = duration.inWholeMilliseconds.toInt()
    }

    /**
     * Sets the default socket read timeout in milliseconds for all requests (defaults to 20,000 ms).
     */
    fun readTimeout(ms: Int) = apply {
        this.readTimeout = ms
    }

    /**
     * Sets the default socket read timeout using [Duration] for all requests.
     */
    fun readTimeout(duration: Duration) = apply {
        this.readTimeout = duration.inWholeMilliseconds.toInt()
    }

    /**
     * Sets the default call-level timeout ceiling in milliseconds for all requests (defaults to 0L, disabled).
     */
    fun callTimeout(ms: Long) = apply {
        this.callTimeout = ms
    }

    /**
     * Sets the default call-level timeout ceiling using [Duration] for all requests.
     */
    fun callTimeout(duration: Duration) = apply {
        this.callTimeout = duration.inWholeMilliseconds
    }

    /**
     * Builds and returns the configured [HttpClient].
     */
    fun build(): HttpClient {
        val allInterceptors = mutableListOf<com.gkhnakbs.gnetwork.interceptor.Interceptor>()
        // RetryInterceptor sits at the head of the pipeline to re-execute subsequent interceptors upon retry
        allInterceptors.add(RetryInterceptor(defaultConfig = retryConfig))

        // CacheInterceptor sits after retry: if response is fresh/cached, it short-circuits network calls
        cache?.let { allInterceptors.add(CacheInterceptor(it)) }

        allInterceptors.addAll(interceptors)

        return HttpClient(
            defaultHeaders = defaultHeaders.toMap(),
            baseUrl = baseUrl,
            interceptors = allInterceptors,
            sslConfig = sslConfig,
            authenticator = authenticator,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            callTimeout = callTimeout,
        )
    }
}