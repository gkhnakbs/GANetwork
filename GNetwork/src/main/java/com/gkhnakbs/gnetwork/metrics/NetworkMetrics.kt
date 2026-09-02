package com.gkhnakbs.gnetwork.metrics

import com.gkhnakbs.gnetwork.core.HttpMethod
import java.util.Locale

/**
 * Snapshot of performance and network diagnostic metrics for an HTTP operation.
 *
 * @property url Target URL or endpoint of the request.
 * @property method The HTTP method (GET, POST, PUT, DELETE).
 * @property statusCode The HTTP status code received, or -1 if the network request failed.
 * @property durationMs Total roundtrip duration of the request in milliseconds.
 * @property sentBytes Number of bytes sent in the request body (0 for empty body).
 * @property receivedBytes Number of bytes received in the response body.
 * @property isSuccessful True if [statusCode] is in the 200..299 range.
 * @property isFromCache True if the response was served directly or revalidated from local cache.
 * @property exception Optional exception thrown if the network call failed.
 *
 * Created by Gökhan Akbaş.
 */
data class NetworkMetrics(
    val url: String,
    val method: HttpMethod,
    val statusCode: Int,
    val durationMs: Long,
    val sentBytes: Long,
    val receivedBytes: Long,
    val isSuccessful: Boolean,
    val isFromCache: Boolean,
    val exception: Throwable? = null,
) {
    /**
     * Formatted summary string suitable for console logs or debugging.
     */
    fun toFormattedString(): String {
        val cacheTag = if (isFromCache) "[CACHE] " else ""
        val statusStr = if (statusCode > 0) statusCode.toString() else "FAILED"
        return "[GANetwork Metrics] $cacheTag$method $url -> $statusStr (${durationMs}ms | Sent: ${formatBytes(sentBytes)} | Recv: ${formatBytes(receivedBytes)})"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * Functional interface for receiving collected [NetworkMetrics].
 */
fun interface MetricsListener {
    /**
     * Invoked upon completion of an HTTP request.
     *
     * @param metrics Collected diagnostic metrics.
     */
    fun onMetricsCollected(metrics: NetworkMetrics)
}
