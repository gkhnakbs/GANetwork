package com.gkhnakbs.gnetwork.interceptor

import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.response.ResponseHeaders

/**
 * Interceptor mimarisi: OkHttp benzeri zincir yapısı
 */
interface Interceptor {
    suspend fun intercept(chain: Chain): RawResponse

    interface Chain {
        val request: HttpRequest
        suspend fun proceed(request: HttpRequest): RawResponse
    }
}

/**
 * Ağ katmanından dönen ham yanıt (parse edilmeden önce)
 */
data class RawResponse(
    val statusCode: Int,
    val message: String?,
    val headers: ResponseHeaders,
    val body: ByteArray,
)

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
