package com.gkhnakbs.gnetwork.progress

import java.util.Locale

/**
 * Snapshot of data transfer progress for upload or download operations.
 *
 * @property bytesTransferred Total number of bytes transferred so far.
 * @property totalBytes Total number of bytes expected to transfer, or -1 if unknown (e.g. chunked transfer).
 *
 * Created by Gökhan Akbaş.
 */
data class Progress(
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    /**
     * Completion percentage from 0 to 100, or -1 if [totalBytes] is unknown.
     */
    val percentage: Int
        get() = when {
            totalBytes > 0 -> ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100)
            totalBytes == 0L -> 100
            else -> -1
        }

    /**
     * Completion fraction from 0.0f to 1.0f, or -1.0f if [totalBytes] is unknown.
     */
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalBytes == 0L -> 1f
            else -> -1f
        }

    /**
     * Indicates whether the entire payload has been completely transferred.
     */
    val isCompleted: Boolean
        get() = totalBytes >= 0 && bytesTransferred >= totalBytes

    /**
     * Human-readable string representation of [bytesTransferred] (e.g. "450 B", "1.2 MB").
     */
    val formattedTransferred: String
        get() = formatBytes(bytesTransferred)

    /**
     * Human-readable string representation of [totalBytes] (e.g. "5.0 MB" or "Unknown").
     */
    val formattedTotal: String
        get() = if (totalBytes >= 0) formatBytes(totalBytes) else "Unknown"

    companion object {
        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/**
 * Functional interface for observing data transfer progress updates.
 */
fun interface ProgressListener {
    /**
     * Invoked periodically during data transfer.
     *
     * @param progress Current progress details.
     */
    fun onProgress(progress: Progress)
}
