# SSL/TLS Yapılandırması Kullanım Kılavuzu

GNetwork kütüphanesi, güçlü SSL/TLS yapılandırma özellikleri sunar.

## 📚 İçindekiler

1. [Temel Kullanım](#temel-kullanım)
2. [Certificate Pinning](#certificate-pinning)
3. [Özel Sertifika](#özel-sertifika)
4. [Self-Signed Sertifika (Debug)](#self-signed-sertifika-debug)
5. [Tüm Sertifikaları Kabul Et (Tehlikeli)](#tüm-sertifikaları-kabul-et-tehlikeli)

---

## Temel Kullanım

### Varsayılan SSL Yapılandırması

```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com"
    // Varsayılan sistem SSL yapılandırması kullanılır
}
```

---

## Certificate Pinning

Belirli sertifikaları veya public key'leri zorunlu kılarak MITM saldırılarını önler.

### SHA-256 Pin Nasıl Bulunur?

#### OpenSSL ile:
```bash
openssl s_client -connect api.example.com:443 | \
openssl x509 -pubkey -noout | \
openssl pkey -pubin -outform der | \
openssl dgst -sha256 -binary | \
base64
```

#### Chrome DevTools ile:
1. Chrome'da siteyi aç
2. F12 > Security sekmesi
3. "View certificate" > Details
4. Public key bilgisini kopyala

### Kullanım Örneği:

```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com"
    
    sslConfig {
        certificatePinner(
            CertificatePinner.builder()
                .add(
                    "api.example.com",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=" // Backup pin
                )
                .build()
        )
    }
}
```

### Wildcard Domain Desteği:

```kotlin
certificatePinner(
    CertificatePinner.builder()
        .add("*.example.com", "sha256/...")
        .add("api.example.com", "sha256/...") // Özel domain
        .build()
)
```

---

## Özel Sertifika

Kendi CA sertifikanızı kullanmak için:

```kotlin
// 1. Sertifika dosyasını assets'e koy: assets/my_certificate.pem

val client = httpClient {
    baseUrl = "https://myserver.com"
    
    sslConfig {
        val certificate = context.assets.open("my_certificate.pem").use { stream ->
            SSLHelper.certificateFromPem(stream)
        }
        
        val trustManager = SSLHelper.createTrustManager(certificate)
        val sslContext = SSLHelper.createSSLContext(trustManager)
        
        sslSocketFactory(sslContext.socketFactory, trustManager)
    }
}
```

### Kısa Yol:

```kotlin
val client = httpClient {
    baseUrl = "https://myserver.com"
    
    sslConfig(
        context.assets.open("my_certificate.pem").use { stream ->
            val certificate = SSLHelper.certificateFromPem(stream)
            SSLHelper.createSSLConfig(certificate)
        }
    )
}
```

---

## Self-Signed Sertifika (Debug)

**⚠️ SADECE DEVELOPMENT/TEST ortamında kullanın!**

```kotlin
val client = httpClient {
    baseUrl = "https://localhost:8443"
    
    if (BuildConfig.DEBUG) {
        sslConfig {
            trustAllCertificates() // ⚠️ Production'da ASLA kullanma!
        }
    }
}
```

### Hostname Verification'ı Devre Dışı Bırak (Debug):

```kotlin
sslConfig {
    trustAllCertificates()
    hostnameVerifier { hostname, session -> true } // ⚠️ Tehlikeli!
}
```

---

## Tüm Sertifikaları Kabul Et (Tehlikeli)

**🚨 ASLA PRODUCTION'DA KULLANMAYIN!**

```kotlin
val unsafeClient = httpClient {
    baseUrl = "https://test-server.local"
    sslConfig(SSLConfig.unsafeAllowAll())
}
```

---

## Gelişmiş Kullanım

### Certificate Pinning + Custom CA

```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com"
    
    sslConfig {
        // Özel CA sertifikası
        val caCert = context.assets.open("ca.pem").use { 
            SSLHelper.certificateFromPem(it) 
        }
        val trustManager = SSLHelper.createTrustManager(caCert)
        val sslContext = SSLHelper.createSSLContext(trustManager)
        
        sslSocketFactory(sslContext.socketFactory, trustManager)
        
        // Certificate pinning ekle
        certificatePinner(
            CertificatePinner.builder()
                .add("api.example.com", "sha256/...")
                .build()
        )
    }
}
```

### Multi-Domain Certificate Pinning

```kotlin
val pinner = CertificatePinner.builder()
    .add("api.example.com", "sha256/pin1", "sha256/pin1_backup")
    .add("cdn.example.com", "sha256/pin2", "sha256/pin2_backup")
    .add("*.example.com", "sha256/wildcard_pin")
    .build()

val client = httpClient {
    baseUrl = "https://api.example.com"
    sslConfig { certificatePinner(pinner) }
}
```

---

## Hata Yönetimi

### Certificate Pinning Başarısız Olursa:

```kotlin
client.get<String>("https://api.example.com/data")
    .onError { error ->
        if (error.exception is SSLPeerUnverifiedException) {
            Log.e("SSL", "Certificate pinning failed!", error.exception)
            // Kullanıcıya güvenlik uyarısı göster
        }
    }
```

---

## Güvenlik Best Practices

### ✅ Yapılması Gerekenler:

1. **Production'da Certificate Pinning kullan**
2. **Backup pin'ler ekle** (sertifika rotasyonu için)
3. **Pin'leri güncel tut**
4. **Wildcard yerine spesifik domain kullan** (mümkünse)
5. **SSL hatalarını logla ve takip et**

### ❌ Yapılmaması Gerekenler:

1. **Production'da `trustAllCertificates()` kullanma**
2. **Production'da `SSLConfig.unsafeAllowAll()` kullanma**
3. **Hostname verification'ı production'da devre dışı bırakma**
4. **Tek pin kullanma** (backup olmadan)
5. **SSL hatalarını sessizce yutma**

---

## Örnek: Tam Yapılandırma

```kotlin
class NetworkModule(private val context: Context) {
    
    fun provideHttpClient(): HttpClient {
        return httpClient {
            baseUrl = "https://api.production.com"
            
            headers {
                this["User-Agent"] = "MyApp/1.0.0"
            }
            
            addInterceptor(AuthInterceptor { getToken() })
            addInterceptor(LoggingInterceptor(level = LoggingInterceptor.Level.BASIC))
            
            // SSL Yapılandırması
            if (BuildConfig.DEBUG) {
                // Debug: Self-signed sertifika kabul et
                sslConfig {
                    trustAllCertificates()
                }
            } else {
                // Production: Certificate pinning
                sslConfig {
                    certificatePinner(
                        CertificatePinner.builder()
                            .add(
                                "api.production.com",
                                "sha256/PRIMARY_PIN_HERE=",
                                "sha256/BACKUP_PIN_HERE="
                            )
                            .build()
                    )
                }
            }
        }
    }
    
    private suspend fun getToken(): String? {
        return TokenManager.getAccessToken()
    }
}
```

---

## Test Etme

### Certificate Pinning'i Test Et:

```kotlin
@Test
fun `certificate pinning should fail with wrong pin`() = runTest {
    val client = httpClient {
        baseUrl = "https://google.com"
        sslConfig {
            certificatePinner(
                CertificatePinner.builder()
                    .add("google.com", "sha256/WRONG_PIN_HERE=")
                    .build()
            )
        }
    }
    
    val response = client.get<String>("/")
    assertTrue(response is HttpResponse.Error)
    assertTrue(response.exception is SSLPeerUnverifiedException)
}
```

---

## Sık Sorulan Sorular

### Q: Certificate pinning zorunlu mu?
**A:** Production uygulamalar için şiddetle tavsiye edilir, özellikle hassas verilerle çalışıyorsanız.

### Q: Pin'ler ne sıklıkla güncellenmeli?
**A:** Sertifika rotasyonu planınıza bağlı. Genellikle 1-2 yıl. Backup pin'ler ekleyerek geçişi kolaylaştırın.

### Q: Wildcard pin güvenli mi?
**A:** Spesifik domain pin'leri kadar güvenli değil, ama çok sayıda subdomain varsa pratik olabilir.

### Q: Development'ta nasıl test ederim?
**A:** `BuildConfig.DEBUG` ile debug modda `trustAllCertificates()` kullanın.

---

## Daha Fazla Bilgi

- [Android Network Security Config](https://developer.android.com/training/articles/security-config)
- [OWASP Certificate Pinning](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- [SSL/TLS Best Practices](https://github.com/ssllabs/research/wiki/SSL-and-TLS-Deployment-Best-Practices)

