package com.gkhnakbs.gnetwork.response

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Represents the outcome of an HTTP execution.
 *
 * Can be one of:
 * - [HttpResponse.Success]: Successful response (2xx) containing the parsed body of type [T].
 * - [HttpResponse.Failure]: Unsuccessful HTTP response (4xx/5xx) with status code and error details.
 * - [HttpResponse.Error]: Client-side or IO exception occurred before receiving a valid response.
 */
sealed class HttpResponse<out T> {

    /**
     * Successful HTTP response with parsed data.
     *
     * @property body Deserialized response payload of type [T].
     * @property statusCode HTTP status code (200..299).
     * @property headers Case-insensitive response headers.
     * @property rawResponse Raw string body as returned by the server.
     */
    data class Success<T>(
        val body: T,
        val statusCode: Int = 200,
        val headers: ResponseHeaders = ResponseHeaders(),
        val rawResponse: String? = null,
    ) : HttpResponse<T>() {
        /**
         * Transforms the response payload from [T] to [R].
         */
        fun <R> map(transform: (T) -> R): Success<R> {
            return Success(
                body = transform(body),
                statusCode = statusCode,
                headers = headers,
                rawResponse = rawResponse
            )
        }
    }

    /**
     * HTTP response indicating an application or server error (non-2xx).
     *
     * @property statusCode HTTP error status code.
     * @property errorMessage Status message or description.
     * @property errorBody Unparsed raw error body if available.
     * @property headers Response headers.
     * @property exception Optional underlying exception.
     */
    data class Failure(
        val statusCode: Int,
        val errorMessage: String,
        val errorBody: String? = null,
        val headers: ResponseHeaders = ResponseHeaders(),
        val exception: Throwable? = null,
    ) : HttpResponse<Nothing>() {
        /** True if status code is in 400..499. */
        val isClientError: Boolean get() = statusCode in 400..499
        /** True if status code is in 500..599. */
        val isServerError: Boolean get() = statusCode in 500..599
    }

    /**
     * Network or client-side failure (e.g., timeout, connectivity loss, serialization failure).
     *
     * @property exception The thrown throwable.
     * @property message Error description.
     */
    data class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "Unknown error",
    ) : HttpResponse<Nothing>() {
        /** True if the error is caused by connection loss, host resolution, or IO issue. */
        val isNetworkError: Boolean
            get() =
                exception is UnknownHostException ||
                        exception is SocketTimeoutException ||
                        exception is IOException

        /** True if the failure is specifically a socket or call-level timeout. */
        val isTimeout: Boolean
            get() = exception is SocketTimeoutException ||
                    exception is TimeoutException ||
                    exception is TimeoutCancellationException
    }
}
