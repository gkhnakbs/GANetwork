package com.gkhnakbs.gnetwork.auth

import com.gkhnakbs.gnetwork.interceptor.RawResponse
import com.gkhnakbs.gnetwork.request.HttpRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe, Mutex-protected Bearer Token Authenticator.
 *
 * Prevents race conditions and refresh token stampedes when multiple concurrent requests
 * encounter a 401 Unauthorized response. Only the first coroutine executes [onRefreshToken],
 * while subsequent waiting requests detect the refreshed token and retry without re-invoking
 * the refresh routine.
 *
 * @property headerName The name of the authorization header (defaults to "Authorization").
 * @property tokenPrefix The prefix prepended to the token (defaults to "Bearer ").
 * @property currentToken Optional supplier for the current cached token to enable double-checked locking.
 * @property onRefreshToken Suspend callback invoked to obtain a new token given the expired token string.
 *
 * Created by Gökhan Akbaş.
 */
class BearerTokenAuthenticator(
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val currentToken: (suspend () -> String?)? = null,
    private val onRefreshToken: suspend (expiredToken: String?) -> String?,
) : Authenticator {

    private val mutex = Mutex()

    /**
     * Authenticates the 401 response by refreshing the token and updating request headers.
     */
    override suspend fun authenticate(request: HttpRequest, response: RawResponse): HttpRequest? {
        val requestAuthHeader = request.headers[headerName]

        return mutex.withLock {
            // Check-then-act: Eğer biz kilidi beklerken başka bir coroutine token'ı zaten yenilediyse,
            // tekrar refresh servisine gitmeden hemen yeni token ile isteği güncelle.
            val latestToken = currentToken?.invoke()
            val formattedLatestHeader = latestToken?.let { formatHeader(it) }

            if (!formattedLatestHeader.isNullOrBlank() && formattedLatestHeader != requestAuthHeader) {
                return@withLock request.copy(
                    headers = request.headers + (headerName to formattedLatestHeader)
                )
            }

            // Token henüz yenilenmemiş, sadece ilk gelen coroutine yenileme fonksiyonunu çalıştırır:
            val expiredToken = requestAuthHeader?.let {
                if (tokenPrefix.isNotEmpty() && it.startsWith(tokenPrefix, ignoreCase = true)) {
                    it.substring(tokenPrefix.length).trim()
                } else {
                    it.trim()
                }
            }

            val newToken = onRefreshToken(expiredToken) ?: return@withLock null
            val newHeaderValue = formatHeader(newToken)

            request.copy(
                headers = request.headers + (headerName to newHeaderValue)
            )
        }
    }

    private fun formatHeader(token: String): String {
        return if (tokenPrefix.isNotEmpty() && !token.startsWith(tokenPrefix, ignoreCase = true)) {
            "$tokenPrefix$token"
        } else {
            token
        }
    }
}
