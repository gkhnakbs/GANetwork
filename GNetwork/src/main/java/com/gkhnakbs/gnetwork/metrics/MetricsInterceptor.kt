package com.gkhnakbs.gnetwork.metrics

import com.gkhnakbs.gnetwork.interceptor.Interceptor
import com.gkhnakbs.gnetwork.interceptor.RawResponse

/**
 * Interceptor that measures HTTP request execution latency, data transfer volumes,
 * and caching status, dispatching [NetworkMetrics] to the provided [listener].
 *
 * @param listener Callback invoked when an HTTP operation finishes.
 *
 * Created by Gökhan Akbaş.
 */
class MetricsInterceptor(
    private val listener: MetricsListener,
) : Interceptor {

    /**
     * Secondary constructor accepting a trailing lambda.
     */
    constructor(block: (NetworkMetrics) -> Unit) : this(MetricsListener { block(it) })

    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val request = chain.request
        val startNs = System.nanoTime()
        val sentBytes = (request.rawBody?.size ?: request.body?.toByteArray(Charsets.UTF_8)?.size ?: 0).toLong()

        var rawResponse: RawResponse? = null
        var error: Throwable? = null

        try {
            val response = chain.proceed(request)
            rawResponse = response
            return response
        } catch (e: Throwable) {
            error = e
            throw e
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val receivedBytes = rawResponse?.body?.size?.toLong() ?: 0L
            val statusCode = rawResponse?.statusCode ?: -1
            val cacheHeader = rawResponse?.headers?.firstIgnoreCase("X-GANetwork-Cache")
            val isFromCache = cacheHeader == "HIT" || cacheHeader == "CONDITIONAL_HIT"

            val metrics = NetworkMetrics(
                url = request.url,
                method = request.method,
                statusCode = statusCode,
                durationMs = durationMs,
                sentBytes = sentBytes,
                receivedBytes = receivedBytes,
                isSuccessful = statusCode in 200..299,
                isFromCache = isFromCache,
                exception = error,
            )
            // Ensure third-party telemetry / logging failures never disrupt ongoing network operations
            runCatching { listener.onMetricsCollected(metrics) }
        }
    }

    companion object {
        /**
         * Creates a [MetricsInterceptor] that prints formatted metric summaries via [logger].
         *
         * @param logger Logging function (defaults to [println]).
         */
        fun logging(logger: (String) -> Unit = ::println): MetricsInterceptor =
            MetricsInterceptor { metrics -> logger(metrics.toFormattedString()) }
    }
}
