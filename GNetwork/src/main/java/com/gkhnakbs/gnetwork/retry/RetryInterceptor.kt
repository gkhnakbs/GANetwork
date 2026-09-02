package com.gkhnakbs.gnetwork.retry

import com.gkhnakbs.gnetwork.interceptor.Interceptor
import com.gkhnakbs.gnetwork.interceptor.RawResponse
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Interceptor that automatically retries failed requests using exponential backoff and jitter.
 *
 * Checks per-request [com.gkhnakbs.gnetwork.request.HttpRequest.retryConfig] first, falling back to [defaultConfig].
 * If neither is configured, or if [RetryConfig.maxRetries] is 0, no retry is performed.
 *
 * @property defaultConfig Optional fallback [RetryConfig] applied when a request does not specify its own.
 *
 * Created by Gökhan Akbaş.
 */
class RetryInterceptor(
    private val defaultConfig: RetryConfig? = null,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val config = chain.request.retryConfig ?: defaultConfig ?: RetryConfig.NO_RETRY

        var response = chain.proceed(chain.request)
        var attempt = 0

        while (attempt < config.maxRetries && config.retryOn(chain.request, response)) {
            attempt++
            val delayMs = calculateDelay(attempt, config, response)
            if (delayMs > 0) {
                delay(delayMs)
            }
            response = chain.proceed(chain.request)
        }

        return response
    }

    private fun calculateDelay(attempt: Int, config: RetryConfig, response: RawResponse): Long {
        // Respect RFC 6585/7231 Retry-After header if present (e.g. on 429 Too Many Requests or 503)
        val retryAfterSeconds = response.headers.firstIgnoreCase("Retry-After")?.trim()?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return min(retryAfterSeconds * 1000L, config.maxDelayMs)
        }

        val exponential = config.initialDelayMs * config.backoffMultiplier.pow((attempt - 1).toDouble())
        val capped = min(exponential.toLong(), config.maxDelayMs)

        return if (config.jitter) {
            val jitterRange = (capped * 0.2).toLong().coerceAtLeast(1L)
            val jitterOffset = Random.nextLong(-jitterRange, jitterRange + 1)
            (capped + jitterOffset).coerceAtLeast(0L)
        } else {
            capped
        }
    }
}
