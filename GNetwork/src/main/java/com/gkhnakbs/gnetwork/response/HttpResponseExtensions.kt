package com.gkhnakbs.gnetwork.response

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

inline fun <T> HttpResponse<T>.onSuccess(action: (T) -> Unit): HttpResponse<T> {
    if (this is HttpResponse.Success) action(this.body)
    return this
}

inline fun <T> HttpResponse<T>.onFailure(action: (HttpResponse.Failure) -> Unit): HttpResponse<T> {
    if (this is HttpResponse.Failure) action(this)
    return this
}

inline fun <T> HttpResponse<T>.onError(action: (HttpResponse.Error) -> Unit): HttpResponse<T> {
    if (this is HttpResponse.Error) action(this)
    return this
}

fun <T> HttpResponse<T>.getOrNull(): T? = when (this) {
    is HttpResponse.Success -> this.body
    else -> null
}

fun <T> HttpResponse<T>.getOrDefault(default: T): T = when (this) {
    is HttpResponse.Success -> this.body
    else -> default
}

fun <T> HttpResponse<T>.getOrThrow(): T = when (this) {
    is HttpResponse.Success -> this.body
    is HttpResponse.Failure -> throw Exception("HTTP Error $statusCode: $errorMessage")
    is HttpResponse.Error -> throw exception
}

inline fun <T, R> HttpResponse<T>.map(transform: (T) -> R): HttpResponse<R> = when (this) {
    is HttpResponse.Success -> HttpResponse.Success(
        body = transform(this.body),
        statusCode = this.statusCode,
        headers = this.headers,
        rawResponse = this.rawResponse
    )

    is HttpResponse.Failure -> this
    is HttpResponse.Error -> this
}

inline fun <T, R> HttpResponse<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (HttpResponse.Failure) -> R,
    onError: (HttpResponse.Error) -> R,
): R = when (this) {
    is HttpResponse.Success -> onSuccess(this.body)
    is HttpResponse.Failure -> onFailure(this)
    is HttpResponse.Error -> onError(this)
}