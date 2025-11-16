package com.gkhnakbs.gnetwork.core

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE");

    companion object {
        val HttpMethod.allowsBody: Boolean
            get() = when (this) {
                GET -> false
                else -> true
            }
    }
}

