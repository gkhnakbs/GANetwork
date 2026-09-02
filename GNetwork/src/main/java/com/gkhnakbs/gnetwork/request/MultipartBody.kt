package com.gkhnakbs.gnetwork.request

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Represents an individual part within a multipart/form-data HTTP request body.
 */
sealed class MultipartPart {
    /**
     * Text form field part.
     *
     * @property name The field name.
     * @property value The string field value.
     */
    data class FormField(
        val name: String,
        val value: String,
    ) : MultipartPart()

    /**
     * Binary file or byte stream part.
     *
     * @property name The form field name.
     * @property filename The original file name.
     * @property bytes The raw binary content of the file.
     * @property contentType The MIME type (e.g., "image/jpeg", "application/pdf").
     */
    data class FilePart(
        val name: String,
        val filename: String,
        val bytes: ByteArray,
        val contentType: String,
    ) : MultipartPart() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FilePart) return false
            if (name != other.name) return false
            if (filename != other.filename) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (contentType != other.contentType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + filename.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + contentType.hashCode()
            return result
        }
    }
}

/**
 * Utility for automatically inferring MIME content types based on file extensions.
 */
object MimeTypeHelper {
    private val extensionToMime = mapOf(
        // Images
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",

        // Documents
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",

        // Text & Code
        "txt" to "text/plain",
        "csv" to "text/csv",
        "html" to "text/html",
        "htm" to "text/html",
        "json" to "application/json",
        "xml" to "application/xml",

        // Media
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",

        // Archives
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed"
    )

    /**
     * Incurs the MIME content type from the provided [filename] or file path.
     *
     * @param filename The name or path of the file.
     * @return Detected MIME type or "application/octet-stream" as a fallback.
     */
    fun detectMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extensionToMime[extension] ?: "application/octet-stream"
    }
}

/**
 * DSL builder for constructing multipart/form-data request payloads.
 */
class MultipartBodyBuilder {
    private val parts = mutableListOf<MultipartPart>()

    /**
     * Unique boundary delimiter separating body parts (RFC 7578 / RFC 2046).
     */
    val boundary: String = "----GANetworkBoundary" + UUID.randomUUID().toString().replace("-", "")

    /**
     * Adds a plain text form field part.
     *
     * @param name Field name.
     * @param value String field value.
     */
    fun part(name: String, value: String) {
        parts.add(MultipartPart.FormField(name, value))
    }

    /**
     * Adds a binary file part using an in-memory byte array.
     *
     * @param name Form field name.
     * @param filename File name (e.g., "avatar.jpg").
     * @param bytes Raw binary content.
     * @param contentType Optional MIME type. If null, automatically inferred from [filename].
     */
    fun part(name: String, filename: String, bytes: ByteArray, contentType: String? = null) {
        val mime = contentType ?: MimeTypeHelper.detectMimeType(filename)
        parts.add(MultipartPart.FilePart(name, filename, bytes, mime))
    }

    /**
     * Adds a file part from a [File] object on disk.
     *
     * @param name Form field name.
     * @param file The file on disk to upload.
     * @param contentType Optional MIME type. If null, automatically inferred from [File.getName].
     */
    fun part(name: String, file: File, contentType: String? = null) {
        require(file.exists() && file.isFile) { "File does not exist or is a directory: ${file.absolutePath}" }
        val mime = contentType ?: MimeTypeHelper.detectMimeType(file.name)
        parts.add(MultipartPart.FilePart(name, file.name, file.readBytes(), mime))
    }

    /**
     * Serializes all configured parts into an RFC-compliant multipart/form-data byte array.
     *
     * @return Serialized binary payload.
     */
    fun build(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val crlf = "\r\n".toByteArray(Charsets.UTF_8)
        val dashDash = "--".toByteArray(Charsets.UTF_8)
        val boundaryBytes = boundary.toByteArray(Charsets.UTF_8)

        for (part in parts) {
            outputStream.write(dashDash)
            outputStream.write(boundaryBytes)
            outputStream.write(crlf)

            when (part) {
                is MultipartPart.FormField -> {
                    val safeName = part.name.replace("\"", "%22")
                    val header = "Content-Disposition: form-data; name=\"$safeName\"\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.write(part.value.toByteArray(Charsets.UTF_8))
                    outputStream.write(crlf)
                }
                is MultipartPart.FilePart -> {
                    val safeName = part.name.replace("\"", "%22")
                    val safeFilename = part.filename.replace("\"", "%22")
                    val header = "Content-Disposition: form-data; name=\"$safeName\"; filename=\"$safeFilename\"\r\n" +
                            "Content-Type: ${part.contentType}\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.write(part.bytes)
                    outputStream.write(crlf)
                }
            }
        }

        // Closing boundary: --boundary--\r\n
        outputStream.write(dashDash)
        outputStream.write(boundaryBytes)
        outputStream.write(dashDash)
        outputStream.write(crlf)

        return outputStream.toByteArray()
    }
}
