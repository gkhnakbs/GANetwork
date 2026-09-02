package com.gkhnakbs.gnetwork.ssl

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL/TLS security configuration for HTTPS connections.
 *
 * @property sslSocketFactory Custom [SSLSocketFactory] for socket creation.
 * @property trustManager Custom [X509TrustManager] validating server certificates.
 * @property hostnameVerifier Custom [HostnameVerifier] validating hostnames.
 * @property certificatePinner Optional [CertificatePinner] enforcing public key pinning.
 */
data class SSLConfig(
    val sslSocketFactory: SSLSocketFactory? = null,
    val trustManager: X509TrustManager? = null,
    val hostnameVerifier: HostnameVerifier? = null,
    val certificatePinner: CertificatePinner? = null,
) {
    companion object {
        /**
         * Default SSL configuration utilizing platform/system certificates and verification.
         */
        fun default(): SSLConfig = SSLConfig()

        /**
         * Insecure configuration that trusts all certificates and hostnames without validation.
         *
         * ⚠️ USE ONLY FOR LOCAL DEBUGGING/TESTING!
         * ⚠️ NEVER USE IN PRODUCTION ENVIRONMENTS!
         */
        @Suppress("CustomX509TrustManager")
        fun unsafeAllowAll(): SSLConfig {
            val trustAllCerts = arrayOf<TrustManager>(
                @Suppress("TrustAllX509TrustManager")
                object : X509TrustManager {
                    @Suppress("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    }

                    @Suppress("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }

            val hostnameVerifier = HostnameVerifier { _, _ -> true }

            return SSLConfig(
                sslSocketFactory = sslContext.socketFactory,
                trustManager = trustAllCerts[0] as X509TrustManager,
                hostnameVerifier = hostnameVerifier
            )
        }
    }
}

/**
 * DSL builder for configuring [SSLConfig].
 */
class SSLConfigBuilder {
    private var sslSocketFactory: SSLSocketFactory? = null
    private var trustManager: X509TrustManager? = null
    private var hostnameVerifier: HostnameVerifier? = null
    private var certificatePinner: CertificatePinner? = null

    /**
     * Sets a custom [SSLSocketFactory] and its corresponding [X509TrustManager].
     */
    fun sslSocketFactory(factory: SSLSocketFactory, trustManager: X509TrustManager) = apply {
        this.sslSocketFactory = factory
        this.trustManager = trustManager
    }

    /**
     * Sets a custom [HostnameVerifier].
     */
    fun hostnameVerifier(verifier: HostnameVerifier) = apply {
        this.hostnameVerifier = verifier
    }

    /**
     * Sets a [CertificatePinner] for public key pinning.
     */
    fun certificatePinner(pinner: CertificatePinner) = apply {
        this.certificatePinner = pinner
    }

    /**
     * Configures a [CertificatePinner] using a DSL builder.
     */
    fun certificatePinner(block: CertificatePinner.Builder.() -> Unit) = apply {
        this.certificatePinner = CertificatePinner.builder().apply(block).build()
    }

    /**
     * ⚠️ DEBUG ONLY: Disables all certificate and hostname validation.
     */
    fun trustAllCertificates() = apply {
        val config = SSLConfig.unsafeAllowAll()
        this.sslSocketFactory = config.sslSocketFactory
        this.trustManager = config.trustManager
        this.hostnameVerifier = config.hostnameVerifier
    }

    /**
     * Builds and returns the configured [SSLConfig].
     */
    fun build(): SSLConfig {
        return SSLConfig(
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            hostnameVerifier = hostnameVerifier,
            certificatePinner = certificatePinner
        )
    }
}

