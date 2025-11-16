package com.gkhnakbs.gnetwork.interceptor

/**
 * Basit Auth interceptor: sabit header veya token sağlayıcı ile ekleme yapar.
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

