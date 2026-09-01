package com.gkhnakbs.gnetwork.interceptor

/**
 * Interceptor that appends an authentication token to outgoing requests.
 *
 * @property headerName The name of the authentication header (defaults to "Authorization").
 * @property tokenProvider Suspend function providing the latest authentication token, or null if unauthenticated.
 */
class AuthInterceptor(
    private val headerName: String = "Authorization",
    private val tokenProvider: suspend () -> String?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val token = tokenProvider()
        val request = if (!token.isNullOrBlank() && headerName.isNotBlank()) {
            chain.request.copy(headers = chain.request.headers + (headerName to token))
        } else chain.request
        return chain.proceed(request)
    }
}

