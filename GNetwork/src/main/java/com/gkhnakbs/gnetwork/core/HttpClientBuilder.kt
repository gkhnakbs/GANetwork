package com.gkhnakbs.gnetwork.core

import com.gkhnakbs.gnetwork.ssl.SSLConfig
import com.gkhnakbs.gnetwork.ssl.SSLConfigBuilder

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

class HttpClientBuilder {
    private val defaultHeaders = mutableMapOf<String, String>()
    private val interceptors = mutableListOf<com.gkhnakbs.gnetwork.interceptor.Interceptor>()
    var baseUrl: String = ""
    private var sslConfig: SSLConfig = SSLConfig.default()

    fun headers(block: MutableMap<String, String>.() -> Unit) {
        defaultHeaders.apply(block)
    }

    fun addInterceptor(interceptor: com.gkhnakbs.gnetwork.interceptor.Interceptor) = apply {
        interceptors += interceptor
    }

    fun interceptors(block: MutableList<com.gkhnakbs.gnetwork.interceptor.Interceptor>.() -> Unit) =
        apply {
            interceptors.apply(block)
        }

    /**
     * SSL/TLS yapılandırması ekle
     */
    fun sslConfig(config: SSLConfig) = apply {
        this.sslConfig = config
    }

    /**
     * SSL/TLS yapılandırması builder ile ekle
     */
    fun sslConfig(block: SSLConfigBuilder.() -> Unit) = apply {
        this.sslConfig = SSLConfigBuilder().apply(block).build()
    }

    fun build(): HttpClient {
        return HttpClient(
            defaultHeaders = defaultHeaders.toMap(),
            baseUrl = baseUrl,
            interceptors = interceptors.toList(),
            sslConfig = sslConfig
        )
    }
}