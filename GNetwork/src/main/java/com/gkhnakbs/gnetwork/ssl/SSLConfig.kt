package com.gkhnakbs.gnetwork.ssl

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL/TLS yapılandırması
 */
data class SSLConfig(
    val sslSocketFactory: SSLSocketFactory? = null,
    val trustManager: X509TrustManager? = null,
    val hostnameVerifier: HostnameVerifier? = null,
    val certificatePinner: CertificatePinner? = null,
) {
    companion object {
        /**
         * Varsayılan SSL yapılandırması (sistem default)
         */
        fun default(): SSLConfig = SSLConfig()

        /**
         * Tüm sertifikaları kabul eden güvensiz yapılandırma
         * ⚠️ SADECE DEBUG/TEST için kullanın!
         */
        fun unsafeAllowAll(): SSLConfig {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                @Suppress("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                @Suppress("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
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
 * SSL yapılandırma builder
 */
class SSLConfigBuilder {
    private var sslSocketFactory: SSLSocketFactory? = null
    private var trustManager: X509TrustManager? = null
    private var hostnameVerifier: HostnameVerifier? = null
    private var certificatePinner: CertificatePinner? = null

    fun sslSocketFactory(factory: SSLSocketFactory, trustManager: X509TrustManager) = apply {
        this.sslSocketFactory = factory
        this.trustManager = trustManager
    }

    fun hostnameVerifier(verifier: HostnameVerifier) = apply {
        this.hostnameVerifier = verifier
    }

    fun certificatePinner(pinner: CertificatePinner) = apply {
        this.certificatePinner = pinner
    }

    /**
     * ⚠️ SADECE DEBUG için! Tüm sertifikaları kabul eder
     */
    fun trustAllCertificates() = apply {
        val config = SSLConfig.unsafeAllowAll()
        this.sslSocketFactory = config.sslSocketFactory
        this.trustManager = config.trustManager
        this.hostnameVerifier = config.hostnameVerifier
    }

    fun build(): SSLConfig {
        return SSLConfig(
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            hostnameVerifier = hostnameVerifier,
            certificatePinner = certificatePinner
        )
    }
}

