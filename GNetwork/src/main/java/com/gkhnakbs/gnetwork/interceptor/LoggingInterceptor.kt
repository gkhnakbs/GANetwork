package com.gkhnakbs.gnetwork.interceptor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * Interceptor that logs outgoing requests and incoming responses with customizable detail levels.
 *
 * Supports formatted JSON output, HTTP headers, request/response body, and latency measurements.
 *
 * @property logger Output function consuming log messages (defaults to `println`).
 * @property level Granularity of logging ([Level.NONE], [Level.BASIC], [Level.HEADERS], [Level.BODY]).
 * @property charset Character encoding used to decode response bodies (defaults to UTF-8).
 */
class LoggingInterceptor(
    private val logger: (String) -> Unit = { println(it) },
    private val level: Level = Level.BASIC,
    private val charset: Charset = Charsets.UTF_8,
) : Interceptor {

    /**
     * Controls the detail level of network logs.
     */
    enum class Level {
        /** No logging. */
        NONE,
        /** Logs request method, URL, response status code, and latency. */
        BASIC,
        /** Logs basic details plus request and response headers. */
        HEADERS,
        /** Logs basic details, headers, and formatted request/response bodies. */
        BODY
    }

    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        if (level == Level.NONE) return chain.proceed(chain.request)

        val req = chain.request

        // ═══════════ REQUEST ═══════════
        logSeparator("REQUEST")
        log("${req.method.name} ${req.url}")

        if (level >= Level.HEADERS && req.headers.isNotEmpty()) {
            logSection("Headers")
            req.headers.forEach { (k, v) ->
                log("  $k: $v")
            }
        }

        if (level >= Level.BODY && !req.body.isNullOrEmpty()) {
            logSection("Request Body")
            val formattedBody = formatBody(req.body)
            log(formattedBody.prependIndent("  "))
        }

        // Timing başlat
        val start = System.nanoTime()
        val resp = chain.proceed(req)
        val tookMs = (System.nanoTime() - start) / 1_000_000

        // ═══════════ RESPONSE ═══════════
        logSeparator("RESPONSE")
        log("${getStatusEmoji(resp.statusCode)} ${resp.statusCode} ${resp.message ?: ""} (${tookMs}ms)")

        if (level >= Level.HEADERS && resp.headers.headers.isNotEmpty()) {
            logSection("Headers")
            resp.headers.headers.forEach { (k, v) ->
                log("  $k: ${v.joinToString(", ")}")
            }
        }

        if (level >= Level.BODY && resp.body.isNotEmpty()) {
            logSection("Response Body")
            val text = withContext(Dispatchers.Default) {
                resp.body.toString(charset)
            }
            val formattedBody = formatBody(text)
            log(formattedBody.take(10_000).prependIndent("  "))
        }

        logEnd()

        return resp
    }

    private fun logSeparator(title: String) {
        log("╔═══════════════════════════════════════════════════════════════")
        log("║ $title")
        log("╠═══════════════════════════════════════════════════════════════")
    }

    private fun logSection(title: String) {
        log("┌─ $title")
    }

    private fun logEnd() {
        log("╚═══════════════════════════════════════════════════════════════")
        log("") // Boş satır ekle
    }

    private fun getStatusEmoji(statusCode: Int): String {
        return when (statusCode) {
            in 200..299 -> "✓" // Başarılı
            in 300..399 -> "↪" // Yönlendirme
            in 400..499 -> "⚠" // Client hatası
            in 500..599 -> "✗" // Server hatası
            else -> "?"
        }
    }

    private fun formatBody(body: String): String {
        // JSON ise güzel formatla
        if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
            return try {
                prettyPrintJson(body)
            } catch (e: Exception) {
                body
            }
        }
        return body
    }

    private fun prettyPrintJson(json: String): String {
        val result = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false

        for (char in json) {
            when {
                escape -> {
                    result.append(char)
                    escape = false
                }
                char == '\\' && inString -> {
                    result.append(char)
                    escape = true
                }
                char == '"' -> {
                    result.append(char)
                    inString = !inString
                }
                !inString && (char == '{' || char == '[') -> {
                    result.append(char)
                    result.append('\n')
                    indent++
                    result.append("  ".repeat(indent))
                }
                !inString && (char == '}' || char == ']') -> {
                    result.append('\n')
                    indent--
                    result.append("  ".repeat(indent))
                    result.append(char)
                }
                !inString && char == ',' -> {
                    result.append(char)
                    result.append('\n')
                    result.append("  ".repeat(indent))
                }
                !inString && char == ':' -> {
                    result.append(char)
                    result.append(' ')
                }
                char.isWhitespace() && !inString -> {
                    // JSON'daki gereksiz boşlukları atla
                }
                else -> {
                    result.append(char)
                }
            }
        }

        return result.toString()
    }

    private fun log(message: String) {
        logger(message)
    }
}
