package com.gkhnakbs.gnetwork.extensions

import com.gkhnakbs.gnetwork.core.HttpClient
import com.gkhnakbs.gnetwork.core.HttpClientBuilder
import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.request.HttpRequestBuilder
import com.gkhnakbs.gnetwork.response.HttpResponse

/**
 * DSL and convenience extension functions for [HttpClient] and [HttpRequest].
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */

/**
 * Creates and configures an [HttpClient] instance using a DSL builder.
 */
fun httpClient(block: HttpClientBuilder.() -> Unit): HttpClient {
    return HttpClientBuilder().apply(block).build()
}

/**
 * Creates and configures an [HttpRequest] instance using a DSL builder.
 */
fun httpRequest(block: HttpRequestBuilder.() -> Unit): HttpRequest {
    return HttpRequestBuilder().apply(block).build()
}

/**
 * Performs a GET request to [url], deserializing the response into [T].
 *
 * @param url Target endpoint URL or relative path.
 * @param block Optional builder for headers and query parameters.
 */
suspend inline fun <reified T> HttpClient.get(
    url: String,
    noinline block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse<T> {
    val request = httpRequest {
        block()
        this.url = url
        this.method = HttpMethod.GET
    }
    return this.execute(request)
}

/**
 * Performs a POST request to [url], deserializing the response into [T].
 *
 * @param url Target endpoint URL or relative path.
 * @param block Builder for body, headers, and query parameters.
 */
suspend inline fun <reified T> HttpClient.post(
    url: String,
    noinline block: HttpRequestBuilder.() -> Unit,
): HttpResponse<T> {
    val request = httpRequest {
        block()
        this.url = url
        this.method = HttpMethod.POST
    }
    return this.execute(request)
}

/**
 * Performs a PUT request to [url], deserializing the response into [T].
 *
 * @param url Target endpoint URL or relative path.
 * @param block Builder for body, headers, and query parameters.
 */
suspend inline fun <reified T> HttpClient.put(
    url: String,
    noinline block: HttpRequestBuilder.() -> Unit,
): HttpResponse<T> {
    val request = httpRequest {
        block()
        this.url = url
        this.method = HttpMethod.PUT
    }
    return this.execute(request)
}

/**
 * Performs a DELETE request to [url], deserializing the response into [T].
 *
 * @param url Target endpoint URL or relative path.
 * @param block Optional builder for headers and query parameters.
 */
suspend inline fun <reified T> HttpClient.delete(
    url: String,
    noinline block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse<T> {
    val request = httpRequest {
        block()
        this.url = url
        this.method = HttpMethod.DELETE
    }
    return this.execute(request)
}