package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.core.HttpMethod

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

data class HttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val connectTimeout: Int = 10000,
    val readTimeout: Int = 20000,
    val body: String? = null,
    val contentType: ContentType? = null,
)