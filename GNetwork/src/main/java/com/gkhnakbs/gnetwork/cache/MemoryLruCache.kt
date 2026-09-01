package com.gkhnakbs.gnetwork.cache

import java.util.Collections
import java.util.LinkedHashMap

/**
 * In-memory, thread-safe LRU (Least Recently Used) cache implementation.
 *
 * Evicts the oldest accessed entries when the total byte size exceeds [maxSize].
 * Pure Kotlin/JVM compatible with zero external Android dependencies.
 *
 * @param maxSize Maximum storage capacity in bytes (defaults to 10 MB).
 *
 * Created by Gökhan Akbaş.
 */
class MemoryLruCache(
    override val maxSize: Long = 10 * 1024 * 1024L,
) : Cache {

    private val lock = Any()
    private var currentSize: Long = 0L

    // LinkedHashMap with access-order = true keeps recently accessed elements at the end
    private val map = LinkedHashMap<String, CachedResponse>(16, 0.75f, true)

    override val size: Long
        get() = synchronized(lock) { currentSize }

    /**
     * Retrieves a cached response by [key], updating its access order.
     */
    override fun get(key: String): CachedResponse? = synchronized(lock) {
        map[key]
    }

    /**
     * Stores a [response] in the cache under [key], evicting older entries if necessary.
     */
    override fun put(key: String, response: CachedResponse) {
        val entrySize = calculateEntrySize(key, response)
        if (entrySize > maxSize) {
            // Entry itself is larger than the entire cache capacity
            return
        }

        synchronized(lock) {
            val existing = map.put(key, response)
            if (existing != null) {
                currentSize -= calculateEntrySize(key, existing)
            }
            currentSize += entrySize

            trimToSize(maxSize)
        }
    }

    /**
     * Removes the entry corresponding to [key] and updates the cache size.
     */
    override fun remove(key: String): Boolean = synchronized(lock) {
        val removed = map.remove(key) ?: return false
        currentSize -= calculateEntrySize(key, removed)
        true
    }

    /**
     * Clears all cached entries and resets size counter.
     */
    override fun clear() = synchronized(lock) {
        map.clear()
        currentSize = 0L
    }

    private fun trimToSize(targetSize: Long) {
        while (currentSize > targetSize && map.isNotEmpty()) {
            val oldestKey = map.keys.firstOrNull() ?: break
            val oldestValue = map.remove(oldestKey) ?: break
            currentSize -= calculateEntrySize(oldestKey, oldestValue)
        }
    }

    private fun calculateEntrySize(key: String, response: CachedResponse): Long {
        var headerBytes = 0L
        response.headers.headers.forEach { (name, values) ->
            headerBytes += name.length + values.sumOf { it.length }
        }
        return key.length.toLong() + response.body.size.toLong() + headerBytes + 64L
    }
}
