package com.gkhnakbs.gnetwork.interceptor

import com.gkhnakbs.gnetwork.request.HttpRequest

/**
 * Terminal interceptor that performs the actual network call at the end of the chain.
 *
 * Created by Gökhan Akbaş on 16/11/2025.
 */
class TerminalInterceptor(
    private val call: suspend (HttpRequest) -> RawResponse,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        return call(chain.request)
    }
}