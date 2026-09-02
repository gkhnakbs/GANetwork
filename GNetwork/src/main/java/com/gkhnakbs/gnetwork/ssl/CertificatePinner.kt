package com.gkhnakbs.gnetwork.ssl

import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Enforces certificate pinning against known SHA-256 public key hashes to prevent MITM attacks.
 *
 * @property pins Map of hostnames to expected [Pin] public key fingerprints.
 */
class CertificatePinner private constructor(
    private val pins: Map<String, List<Pin>>,
) {
    /**
     * Verifies that at least one certificate in [certificates] matches the configured pin for [hostname].
     *
     * @param hostname Target server host.
     * @param certificates Peer certificate chain presented during the TLS handshake.
     * @throws SSLPeerUnverifiedException If no certificate matches any pinned public key hash.
     */
    fun check(hostname: String, certificates: List<Certificate>) {
        val hostPins = findPinsForHost(hostname)
        if (hostPins.isEmpty()) return

        for (certificate in certificates) {
            if (certificate !is X509Certificate) continue

            val publicKeyHash = sha256(certificate.publicKey.encoded)

            for (pin in hostPins) {
                if (pin.hash.contentEquals(publicKeyHash)) {
                    return // Pin match bulundu
                }
            }
        }

        // Hiçbir pin match bulunamadı
        throw SSLPeerUnverifiedException(
            "Certificate pinning failure!\n" +
            "  Peer: $hostname\n" +
            "  Pinned: ${hostPins.joinToString { it.toString() }}\n" +
            "  Found: ${certificates.joinToString { sha256Hash(it) }}"
        )
    }

    /**
     * Resolves all matching pins for a given host, evaluating exact match and hierarchical wildcard patterns (e.g. *.example.com).
     */
    internal fun findPinsForHost(hostname: String): List<Pin> {
        val cleanHostname = hostname.lowercase().trim()
        val matchingPins = mutableListOf<Pin>()

        // 1. Exact hostname match
        pins[cleanHostname]?.let { matchingPins.addAll(it) }

        // 2. Wildcard pattern matching across parent domains (e.g. api.example.com -> *.example.com)
        var dotIndex = cleanHostname.indexOf('.')
        while (dotIndex != -1 && dotIndex < cleanHostname.length - 1) {
            val wildcardPattern = "*." + cleanHostname.substring(dotIndex + 1)
            pins[wildcardPattern]?.let { matchingPins.addAll(it) }
            dotIndex = cleanHostname.indexOf('.', dotIndex + 1)
        }

        return matchingPins
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun sha256Hash(certificate: Certificate): String {
        if (certificate !is X509Certificate) return "unknown"
        val hash = sha256(certificate.publicKey.encoded)
        return "sha256/${hash.toBase64()}"
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    /**
     * Represents a single public key cryptographic pin.
     *
     * @property hashAlgorithm Algorithm used for the pin (typically "sha256").
     * @property hash Byte array of the digest.
     */
    data class Pin(val hashAlgorithm: String, val hash: ByteArray) {
        override fun toString(): String = "$hashAlgorithm/${hash.toBase64()}"

        private fun ByteArray.toBase64(): String {
            return Base64.getEncoder().encodeToString(this)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pin) return false
            if (hashAlgorithm != other.hashAlgorithm) return false
            if (!hash.contentEquals(other.hash)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = hashAlgorithm.hashCode()
            result = 31 * result + hash.contentHashCode()
            return result
        }
    }

    /**
     * Builder for constructing [CertificatePinner] instances.
     */
    class Builder {
        private val pins = mutableMapOf<String, MutableList<Pin>>()

        /**
         * Adds one or more SHA-256 pins for the specified [hostname].
         * Format: `"sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="`
         */
        fun add(hostname: String, vararg pinHashes: String) = apply {
            val cleanHostname = hostname.lowercase().trim()
            val hostPins = pins.getOrPut(cleanHostname) { mutableListOf() }

            for (pinHash in pinHashes) {
                val pin = requireNotNull(parsePin(pinHash)) {
                    "Invalid pin format: '$pinHash'. Expected format: 'sha256/<base64-encoded-hash>'"
                }
                hostPins.add(pin)
            }
        }

        private fun parsePin(pinHash: String): Pin? {
            val parts = pinHash.split("/", limit = 2)
            if (parts.size != 2) return null

            val algorithm = parts[0].lowercase()
            if (algorithm != "sha256") return null

            val hash = try {
                Base64.getDecoder().decode(parts[1])
            } catch (e: IllegalArgumentException) {
                return null
            }

            return Pin(algorithm, hash)
        }

        /**
         * Builds the configured [CertificatePinner].
         */
        fun build(): CertificatePinner {
            return CertificatePinner(pins.toMap())
        }
    }

    companion object {
        /**
         * Creates a new [Builder].
         */
        fun builder(): Builder = Builder()
    }
}

