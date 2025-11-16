package com.gkhnakbs.gnetwork.request

import com.gkhnakbs.gnetwork.core.HttpMethod
import java.net.URLEncoder

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

class HttpRequestBuilder {
    var url: String = ""
    var method: HttpMethod = HttpMethod.GET
    private val headers = mutableMapOf<String, String>()
    private val queryParams = mutableMapOf<String, String>()
    private var body: String? = null
    private var contentType: ContentType? = null

    // Header ekleme fonksiyonları
    fun header(key: String, value: String) {
        headers[key] = value
    }

    fun headers(block: MutableMap<String, String>.() -> Unit) {
        headers.apply(block)
    }

    // Query Parameters
    fun queryParam(key: String, value: String) {
        queryParams[key] = value
    }

    fun queryParam(key: String, value: Any) {
        queryParams[key] = value.toString()
    }

    fun queryParams(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) ->
            queryParams[key] = value
        }
    }

    fun queryParams(block: MutableMap<String, String>.() -> Unit) {
        queryParams.apply(block)
    }

    // JSON Body
    fun jsonBody(json: String) {
        body = json
        contentType = ContentType.JSON
        header("Content-Type", ContentType.JSON.value)
    }

    fun jsonBody(block: JsonBodyBuilder.() -> Unit) {
        val builder = JsonBodyBuilder().apply(block)
        body = builder.build()
        contentType = ContentType.JSON
        header("Content-Type", ContentType.JSON.value)
    }

    // Form URL Encoded Body
    fun formBody(vararg pairs: Pair<String, String>) {
        val formData = pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        body = formData
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    fun formBody(block: FormBodyBuilder.() -> Unit) {
        val builder = FormBodyBuilder().apply(block)
        body = builder.build()
        contentType = ContentType.FORM_URL_ENCODED
        header("Content-Type", ContentType.FORM_URL_ENCODED.value)
    }

    // Text/Plain Body
    fun textBody(text: String) {
        body = text
        contentType = ContentType.TEXT_PLAIN
        header("Content-Type", ContentType.TEXT_PLAIN.value)
    }

    // Build fonksiyonu
    fun build(): HttpRequest {
        require(url.isNotEmpty()) { "URL cannot be empty" }

        val finalUrl = buildFullUrl(url, queryParams)

        return HttpRequest(
            url = finalUrl,
            method = method,
            headers = headers.toMap(),
            body = body,
            contentType = contentType
        )
    }

    private fun buildFullUrl(url: String, queryParams: Map<String, String> = emptyMap()): String {
        return if (queryParams.isNotEmpty()) {
            val queryString = queryParams.entries.joinToString("&") { (key, value) ->
                val encKey = URLEncoder.encode(key, "UTF-8")
                // Virgülü encode etme - bazı API'lar (open-meteo gibi) virgülü ayraç olarak kullanır
                val encVal = URLEncoder.encode(value, "UTF-8").replace("%2C", ",")
                "${encKey}=${encVal}"
            }
            if (url.contains("?")) {
                "$url&$queryString"
            } else {
                "$url?$queryString"
            }
        } else {
            url
        }
    }
}

// JSON Body Builder
class JsonBodyBuilder {
    private val fields = mutableMapOf<String, Any?>()

    infix fun String.to(value: Any?) {
        fields[this] = value
    }

    fun field(key: String, value: Any?) {
        fields[key] = value
    }

    fun build(): String {
        val jsonFields = fields.entries.joinToString(",\n  ") { (key, value) ->
            val jsonValue = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is Number, is Boolean -> value.toString()
                else -> "\"$value\""
            }
            "\"$key\": $jsonValue"
        }
        return "{\n  $jsonFields\n}"
    }
}

// Form Body Builder
class FormBodyBuilder {
    private val fields = mutableListOf<Pair<String, String>>()

    fun field(key: String, value: String) {
        fields.add((key to value))
    }

    fun build(): String {
        return fields.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
    }
}