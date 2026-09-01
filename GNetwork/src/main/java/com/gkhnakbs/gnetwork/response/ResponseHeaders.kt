package com.gkhnakbs.gnetwork.response

import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Case-preserving, multi-value HTTP response headers.
 *
 * @property headers Map of header names to list of string values.
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
data class ResponseHeaders(
    val headers: Map<String, List<String>> = emptyMap(),
) {
    /** Returns the first value for [key], or null if absent. */
    fun get(key: String): String? = headers[key]?.firstOrNull()

    /** Returns all values associated with [key]. */
    fun getAll(key: String): List<String> = headers[key] ?: emptyList()

    /** Returns the first value matching [name] case-insensitively, or null if absent. */
    fun firstIgnoreCase(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/**
 * Extracts the [Charset] defined in the `Content-Type` header, if any.
 */
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

/**
 * Checks whether the `Content-Type` header indicates a JSON payload.
 */
@PublishedApi
internal fun ResponseHeaders.isJson(): Boolean {
    val ct = firstIgnoreCase("Content-Type")?.lowercase() ?: return false
    return ct.contains("application/json") || ct.contains("+json")
}

/**
 * Wraps [input] in a [GZIPInputStream] if `Content-Encoding` specifies gzip.
 */
@PublishedApi
internal fun wrapIfCompressed(input: InputStream, headers: ResponseHeaders): InputStream {
    val enc = headers.firstIgnoreCase("Content-Encoding")?.lowercase()
    return if (enc?.contains("gzip") == true) GZIPInputStream(input) else input
}