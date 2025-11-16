package com.gkhnakbs.gnetwork.ssl

import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Sertifika pinning - belirli sertifikaları veya public key'leri zorunlu kılar
 */
class CertificatePinner private constructor(
    private val pins: Map<String, List<Pin>>,
) {
    /**
     * Verilen hostname için sertifika zincirini doğrular
     * @throws SSLPeerUnverifiedException pin match bulunamazsa
     */
    fun check(hostname: String, certificates: List<Certificate>) {
        val cleanHostname = hostname.lowercase().trim()
        val hostPins = pins[cleanHostname] ?: pins["*.$cleanHostname"] ?: return

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

    class Builder {
        private val pins = mutableMapOf<String, MutableList<Pin>>()

        /**
         * SHA-256 hash ile pin ekle
         * Format: "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
         */
        fun add(hostname: String, vararg pinHashes: String) = apply {
            val cleanHostname = hostname.lowercase().trim()
            val hostPins = pins.getOrPut(cleanHostname) { mutableListOf() }

            for (pinHash in pinHashes) {
                val pin = parsePin(pinHash)
                if (pin != null) {
                    hostPins.add(pin)
                }
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

        fun build(): CertificatePinner {
            return CertificatePinner(pins.toMap())
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}

