package com.gkhnakbs.gnetwork.request

/**
 * Created by Gökhan Akbaş on 12/11/2025.
 */

enum class ContentType(val value: String) {
    JSON("application/json"),
    FORM_URL_ENCODED("application/x-www-form-urlencoded"),
    TEXT_PLAIN("text/plain")
}