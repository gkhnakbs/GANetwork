package com.gkhnakbs.gnetwork.cache

import com.gkhnakbs.gnetwork.response.ResponseHeaders
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Persistent disk-based LRU (Least Recently Used) cache implementation.
 *
 * Stores HTTP responses across app launches and device reboots using a two-file structure:
 * - `${sha256(key)}.meta`: Contains URL, HTTP status code, message, timestamps, and headers.
 * - `${sha256(key)}.body`: Contains the raw binary response body bytes.
 *
 * Writes are atomic (via temporary `.tmp` files) to prevent corrupted cache entries during crashes.
 * Thread-safe and pure Kotlin/JVM compatible.
 *
 * @property directory The filesystem directory used for storing cache files.
 * @property maxSize Maximum cache storage capacity in bytes (defaults to 50 MB).
 *
 * Created by Gökhan Akbaş.
 */
class DiskLruCache(
    val directory: File,
    override val maxSize: Long = 50 * 1024 * 1024L,
) : Cache {

    private val lock = Any()
    private var currentSize: Long = 0L
    private val entries = LinkedHashMap<String, DiskEntry>(16, 0.75f, true)

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        initialize()
    }

    override val size: Long
        get() = synchronized(lock) { currentSize }

    /**
     * Retrieves a cached response by its [key] from disk.
     *
     * @param key The cache key (typically the request URL).
     * @return The reconstructed [CachedResponse], or null if missing, corrupted, or expired.
     */
    override fun get(key: String): CachedResponse? = synchronized(lock) {
        val entry = entries[key] ?: return null
        val metaFile = File(directory, "${entry.hash}.meta")
        val bodyFile = File(directory, "${entry.hash}.body")

        if (!metaFile.exists() || !bodyFile.exists()) {
            remove(key)
            return null
        }

        val meta = readMetadata(metaFile)?.second ?: run {
            remove(key)
            return null
        }

        val bodyBytes = try {
            bodyFile.readBytes()
        } catch (e: Exception) {
            remove(key)
            return null
        }

        // Update file access timestamps
        val now = System.currentTimeMillis()
        metaFile.setLastModified(now)
        bodyFile.setLastModified(now)

        CachedResponse(
            statusCode = meta.statusCode,
            message = meta.message,
            headers = meta.headers,
            body = bodyBytes,
            receivedAtMillis = meta.receivedAtMillis,
        )
    }

    /**
     * Atomically stores [response] to disk under [key], evicting oldest entries if necessary.
     *
     * @param key The cache key (typically the request URL).
     * @param response The response data to persist.
     */
    override fun put(key: String, response: CachedResponse) {
        val hash = hashKey(key)
        val metaTmp = File(directory, "$hash.meta.tmp")
        val bodyTmp = File(directory, "$hash.body.tmp")
        val metaFile = File(directory, "$hash.meta")
        val bodyFile = File(directory, "$hash.body")

        try {
            writeMetadata(metaTmp, key, response)
            bodyTmp.writeBytes(response.body)

            val entrySize = metaTmp.length() + bodyTmp.length()
            if (entrySize > maxSize) {
                metaTmp.delete()
                bodyTmp.delete()
                return
            }

            synchronized(lock) {
                atomicMove(metaTmp, metaFile)
                atomicMove(bodyTmp, bodyFile)

                val existing = entries.put(key, DiskEntry(key = key, hash = hash, size = entrySize))
                if (existing != null) {
                    currentSize -= existing.size
                }
                currentSize += entrySize

                trimToSize(maxSize)
            }
        } catch (e: Exception) {
            metaTmp.delete()
            bodyTmp.delete()
        }
    }

    /**
     * Removes a cached entry from memory and disk.
     *
     * @param key The cache key to remove.
     * @return True if the entry was removed, false otherwise.
     */
    override fun remove(key: String): Boolean = synchronized(lock) {
        val entry = entries.remove(key) ?: return false
        currentSize -= entry.size
        File(directory, "${entry.hash}.meta").delete()
        File(directory, "${entry.hash}.body").delete()
        true
    }

    /**
     * Clears all cached files and resets size counter.
     */
    override fun clear() {
        synchronized(lock) {
            entries.clear()
            currentSize = 0L
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun initialize() {
        synchronized(lock) {
            entries.clear()
            currentSize = 0L

            val metaFiles = directory.listFiles { _, name -> name.endsWith(".meta") } ?: return
            // Sort by lastModified to reconstruct LRU access order across restarts
            metaFiles.sortBy { it.lastModified() }

            for (metaFile in metaFiles) {
                val hash = metaFile.name.removeSuffix(".meta")
                val bodyFile = File(directory, "$hash.body")
                if (!bodyFile.exists()) {
                    metaFile.delete()
                    continue
                }

                val meta = readMetadata(metaFile)
                if (meta == null) {
                    metaFile.delete()
                    bodyFile.delete()
                    continue
                }

                val entrySize = metaFile.length() + bodyFile.length()
                val entry = DiskEntry(key = meta.first, hash = hash, size = entrySize)
                entries[meta.first] = entry
                currentSize += entrySize
            }

            trimToSize(maxSize)
        }
    }

    private fun trimToSize(targetSize: Long) {
        val iterator = entries.entries.iterator()
        while (currentSize > targetSize && iterator.hasNext()) {
            val entry = iterator.next().value
            iterator.remove()
            currentSize -= entry.size
            File(directory, "${entry.hash}.meta").delete()
            File(directory, "${entry.hash}.body").delete()
        }
    }

    private fun atomicMove(source: File, target: File) {
        if (target.exists()) {
            target.delete()
        }
        if (!source.renameTo(target)) {
            // Fallback for filesystems that reject rename across mounts
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun hashKey(key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeMetadata(file: File, key: String, response: CachedResponse) {
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(key)
            writer.newLine()
            writer.write(response.receivedAtMillis.toString())
            writer.newLine()
            writer.write(response.statusCode.toString())
            writer.newLine()
            writer.write(response.message ?: "")
            writer.newLine()

            val allHeaders = response.headers.headers
            val headerLines = mutableListOf<Pair<String, String>>()
            for ((name, values) in allHeaders) {
                for (value in values) {
                    headerLines.add(name to value)
                }
            }
            writer.write(headerLines.size.toString())
            writer.newLine()
            for ((name, value) in headerLines) {
                writer.write("$name: $value")
                writer.newLine()
            }
        }
    }

    private fun readMetadata(file: File): Pair<String, Metadata>? {
        return try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val key = reader.readLine() ?: return null
                val receivedAtMillis = reader.readLine()?.toLongOrNull() ?: return null
                val statusCode = reader.readLine()?.toIntOrNull() ?: return null
                val message = reader.readLine()?.takeIf { it.isNotEmpty() }
                val headerCount = reader.readLine()?.toIntOrNull() ?: 0
                val headersMap = mutableMapOf<String, MutableList<String>>()

                for (i in 0 until headerCount) {
                    val line = reader.readLine() ?: break
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val name = line.substring(0, colonIndex).trim()
                        val value = line.substring(colonIndex + 1).trim()
                        headersMap.getOrPut(name) { mutableListOf() }.add(value)
                    }
                }

                key to Metadata(
                    receivedAtMillis = receivedAtMillis,
                    statusCode = statusCode,
                    message = message,
                    headers = ResponseHeaders(headersMap)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class DiskEntry(
        val key: String,
        val hash: String,
        val size: Long,
    )

    private data class Metadata(
        val receivedAtMillis: Long,
        val statusCode: Int,
        val message: String?,
        val headers: ResponseHeaders,
    )
}
