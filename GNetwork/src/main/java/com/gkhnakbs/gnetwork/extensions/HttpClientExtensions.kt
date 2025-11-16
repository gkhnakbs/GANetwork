package com.gkhnakbs.gnetwork.extensions

import com.gkhnakbs.gnetwork.core.HttpClient
import com.gkhnakbs.gnetwork.core.HttpClientBuilder
import com.gkhnakbs.gnetwork.core.HttpMethod
import com.gkhnakbs.gnetwork.request.HttpRequest
import com.gkhnakbs.gnetwork.request.HttpRequestBuilder
import com.gkhnakbs.gnetwork.response.HttpResponse

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

fun httpClient(block: HttpClientBuilder.() -> Unit): HttpClient {
    return HttpClientBuilder().apply(block).build()
}

fun httpRequest(block: HttpRequestBuilder.() -> Unit): HttpRequest {
    return HttpRequestBuilder().apply(block).build()
}

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