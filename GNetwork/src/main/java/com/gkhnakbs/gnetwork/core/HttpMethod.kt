package com.gkhnakbs.gnetwork.core

/**
 * Supported HTTP request methods.
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE");

    companion object {
        /**
         * Indicates whether this HTTP method permits an outgoing request body.
         */
        val HttpMethod.allowsBody: Boolean
            get() = when (this) {
                GET -> false
                else -> true
            }
    }
}

