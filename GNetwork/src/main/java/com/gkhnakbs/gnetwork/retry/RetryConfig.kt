package com.gkhnakbs.gnetwork.retry

import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.request.HttpRequest

/**
 * Configuration options for retrying failed HTTP requests.
 *
 * @property maxRetries Maximum number of retry attempts (defaults to 3). Set to 0 to disable retrying.
 * @property initialDelayMs Initial delay before the first retry attempt in milliseconds (defaults to 1000 ms).
 * @property maxDelayMs Maximum ceiling for backoff delay in milliseconds (defaults to 10,000 ms).
 * @property backoffMultiplier Exponential factor applied to delay between subsequent attempts (defaults to 2.0).
 * @property jitter Whether to add random jitter to backoff delay to mitigate stampedes (defaults to true).
 * @property retryOn Predicate determining whether a given request and response qualify for a retry.
 *
 * Created by Gökhan Akbaş.
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10000L,
    val backoffMultiplier: Double = 2.0,
    val jitter: Boolean = true,
    val retryOn: (request: HttpRequest, response: RawResponse) -> Boolean = { _, response ->
        // Default: Network failure (statusCode == -1), 5xx server errors, or 429 Too Many Requests
        response.statusCode == -1 || response.statusCode in 500..599 || response.statusCode == 429
    },
) {
    companion object {
        /**
         * Default retry configuration with 3 attempts and exponential backoff.
         */
        val DEFAULT = RetryConfig()

        /**
         * Disabled retry configuration with 0 attempts.
         */
        val NO_RETRY = RetryConfig(maxRetries = 0)
    }
}

/**
 * DSL builder for constructing [RetryConfig] instances.
 */
class RetryConfigBuilder {
    var maxRetries: Int = 3
    var initialDelayMs: Long = 1000L
    var maxDelayMs: Long = 10000L
    var backoffMultiplier: Double = 2.0
    var jitter: Boolean = true
    private var retryOnPredicate: ((HttpRequest, RawResponse) -> Boolean)? = null

    /**
     * Sets a custom condition predicate for triggering a retry.
     */
    fun retryOn(predicate: (request: HttpRequest, response: RawResponse) -> Boolean) {
        this.retryOnPredicate = predicate
    }

    /**
     * Builds the configured [RetryConfig].
     */
    fun build(): RetryConfig {
        return if (retryOnPredicate != null) {
            RetryConfig(
                maxRetries = maxRetries,
                initialDelayMs = initialDelayMs,
                maxDelayMs = maxDelayMs,
                backoffMultiplier = backoffMultiplier,
                jitter = jitter,
                retryOn = retryOnPredicate!!
            )
        } else {
            RetryConfig(
                maxRetries = maxRetries,
                initialDelayMs = initialDelayMs,
                maxDelayMs = maxDelayMs,
                backoffMultiplier = backoffMultiplier,
                jitter = jitter
            )
        }
    }
}
