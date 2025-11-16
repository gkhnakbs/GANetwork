package com.gkhnakbs.gnetwork.interceptor

import com.gkhnakbs.gnetwork.request.HttpRequest

/**
 * Terminal interceptor: gerçek network çağrısını yapar
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