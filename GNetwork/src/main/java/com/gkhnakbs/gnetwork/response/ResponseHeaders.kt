package com.gkhnakbs.gnetwork.response

import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

data class ResponseHeaders(
    val headers: Map<String, List<String>> = emptyMap(),
) {
    fun get(key: String): String? = headers[key]?.firstOrNull()
    fun getAll(key: String): List<String> = headers[key] ?: emptyList()
}

@PublishedApi
internal fun ResponseHeaders.firstIgnoreCase(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

@PublishedApi
internal fun ResponseHeaders.contentCharset(): Charset? {
    val ct = firstIgnoreCase("Content-Type") ?: return null
    val charset = ct.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter("=", "")
        ?.trim()
    return runCatching { if (!charset.isNullOrBlank()) Charset.forName(charset) else null }.getOrNull()
}

@PublishedApi
internal fun ResponseHeaders.isJson(): Boolean {
    val ct = firstIgnoreCase("Content-Type")?.lowercase() ?: return false
    return ct.contains("application/json") || ct.contains("+json")
}

@PublishedApi
internal fun wrapIfCompressed(input: InputStream, headers: ResponseHeaders): InputStream {
    val enc = headers.firstIgnoreCase("Content-Encoding")?.lowercase()
    return if (enc?.contains("gzip") == true) GZIPInputStream(input) else input
}