package com.gkhnakbs.gnetwork.auth

import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.request.HttpRequest

/**
 * Handles authentication challenges when encountering a 401 Unauthorized response.
 *
 * Implementations can refresh expired credentials and return an updated [HttpRequest] to retry,
 * or return `null` to accept the 401 response without retrying.
 *
 * Created by Gökhan Akbaş.
 */
fun interface Authenticator {
    /**
     * Called when the server returns a 401 Unauthorized HTTP response.
     *
     * @param request The original [HttpRequest] that resulted in 401 Unauthorized.
     * @param response The raw 401 [RawResponse] received from the server.
     * @return The updated [HttpRequest] to retry with, or `null` if the request cannot be authenticated.
     */
    suspend fun authenticate(request: HttpRequest, response: RawResponse): HttpRequest?

    companion object {
        /**
         * A no-op authenticator that does not retry unauthenticated requests.
         */
        val NONE = Authenticator { _, _ -> null }
    }
}
