package com.gkhnakbs.gnetwork.interceptor

import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.response.ResponseHeaders

/**
 * Observes, modifies, and intercepts outgoing HTTP requests and incoming responses.
 */
interface Interceptor {
    /**
     * Intercepts an outgoing request and returns a [RawResponse].
     */
    suspend fun intercept(chain: Chain): RawResponse

    /**
     * Represents the execution chain for an [Interceptor].
     */
    interface Chain {
        /**
         * The current [HttpRequest] being processed.
         */
        val request: HttpRequest

        /**
         * Forwards the [request] to the next interceptor in the pipeline.
         */
        suspend fun proceed(request: HttpRequest): RawResponse
    }
}

/**
 * Unparsed raw HTTP response received from the network connection.
 *
 * @property statusCode HTTP status code (e.g., 200, 404, 500).
 * @property message HTTP status message (e.g., "OK", "Not Found").
 * @property headers Case-insensitive response headers.
 * @property body Raw byte array of the response body.
 */
data class RawResponse(
    val statusCode: Int,
    val message: String?,
    val headers: ResponseHeaders,
    val body: ByteArray,
)

/**
 * Concrete implementation of [Interceptor.Chain] that advances through a list of interceptors.
 */
internal class RealInterceptorChain(
    private val interceptors: List<Interceptor>,
    private val index: Int,
    override val request: HttpRequest,
) : Interceptor.Chain {
    override suspend fun proceed(request: HttpRequest): RawResponse {
        return if (index < interceptors.size) {
            val next = RealInterceptorChain(interceptors, index + 1, request)
            interceptors[index].intercept(next)
        } else {
            error("No network interceptor in chain. Ensure a terminal interceptor is added.")
        }
    }
}
