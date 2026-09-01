package com.gkhnakbs.gnetwork.request

/**
 * Common HTTP Content-Type MIME types.
 *
 * Created by Gökhan Akbaş on 12/11/2025.
 */
enum class ContentType(val value: String) {
    /** JSON payload (`application/json`). */
    JSON("application/json"),
    /** URL-encoded form data (`application/x-www-form-urlencoded`). */
    FORM_URL_ENCODED("application/x-www-form-urlencoded"),
    /** Plain text (`text/plain`). */
    TEXT_PLAIN("text/plain")
}