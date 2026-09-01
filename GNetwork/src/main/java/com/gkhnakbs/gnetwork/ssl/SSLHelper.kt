package com.gkhnakbs.gnetwork.ssl

import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Utility functions for loading X.509 certificates and configuring custom SSL/TLS trust managers.
 */
object SSLHelper {

    /**
     * Parses an [X509Certificate] from a PEM-encoded input stream.
     */
    fun certificateFromPem(pemInputStream: InputStream): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(pemInputStream) as X509Certificate
    }

    /**
     * Creates an [X509TrustManager] trusting the provided [certificates].
     */
    fun createTrustManager(vararg certificates: X509Certificate): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certificates.forEachIndexed { index, cert ->
                setCertificateEntry("cert_$index", cert)
            }
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(keyStore)
        }

        val trustManagers = trustManagerFactory.trustManagers
        check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected trust managers: ${trustManagers.contentToString()}"
        }

        return trustManagers[0] as X509TrustManager
    }

    /**
     * Creates a TLS [SSLContext] initialized with the specified [trustManager].
     */
    fun createSSLContext(trustManager: X509TrustManager): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
    }

    /**
     * Creates an [SSLConfig] trusting only the provided [certificates].
     */
    fun createSSLConfig(vararg certificates: X509Certificate): SSLConfig {
        val trustManager = createTrustManager(*certificates)
        val sslContext = createSSLContext(trustManager)

        return SSLConfig(
            sslSocketFactory = sslContext.socketFactory,
            trustManager = trustManager
        )
    }
}

