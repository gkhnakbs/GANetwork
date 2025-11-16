package com.gkhnakbs.gnetwork.response

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class HttpResponse<out T> {

    data class Success<T>(
        val body: T,
        val statusCode: Int = 200,
        val headers: ResponseHeaders = ResponseHeaders(),
        val rawResponse: String? = null,
    ) : HttpResponse<T>() {
        fun <R> map(transform: (T) -> R): Success<R> {
            return Success(
                body = transform(body),
                statusCode = statusCode,
                headers = headers,
                rawResponse = rawResponse
            )
        }
    }

    data class Failure(
        val statusCode: Int,
        val errorMessage: String,
        val errorBody: String? = null,
        val headers: ResponseHeaders = ResponseHeaders(),
        val exception: Throwable? = null,
    ) : HttpResponse<Nothing>() {
        val isClientError: Boolean get() = statusCode in 400..499
        val isServerError: Boolean get() = statusCode in 500..599
    }

    data class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "Unknown error",
    ) : HttpResponse<Nothing>() {
        val isNetworkError: Boolean
            get() =
                exception is UnknownHostException ||
                        exception is SocketTimeoutException ||
                        exception is IOException

        val isTimeout: Boolean get() = exception is SocketTimeoutException
    }
}
