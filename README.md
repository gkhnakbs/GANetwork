# GANetwork

Modern Kotlin/Android için basit, esnek ve genişletilebilir bir network kütüphanesi. Bu repo, hem kütüphaneyi (GNetwork) hem de onu kullanan küçük bir test uygulamasını (app) içerir.

- Kolay DSL: `httpClient {}`, `client.get<T>(...) {}`
- Interceptor zinciri: Logging, Auth ve kolay genişletilebilir mimari
- SSL/TLS: Özel TrustManager, HostnameVerifier ve Certificate Pinning
- GZip ve charset desteği; doğru gövde okuma
- Net sonuç modeli: `HttpResponse.Success/Failure/Error`
- 📖 Detaylı İngilizce mimari ve özellik rehberi için: [FEATURES.md](./FEATURES.md)

---

## İçindekiler
- [Demo/Test Uygulaması](#demotest-uygulaması)
- [Modül Yapısı](#modül-yapısı)
- [Kurulum](#kurulum)
- [Hızlı Başlangıç](#hızlı-başlangıç)
- [Kullanım Örnekleri](#kullanım-örnekleri)
  - [GET](#get)
  - [POST - JSON Gövde](#post---json-gövde)
  - [Query Parametreleri ve Header'lar](#query-parametreleri-ve-headerlar)
- [Interceptor'lar](#interceptorlar)
  - [LoggingInterceptor](#logginginterceptor)
  - [AuthInterceptor](#authinterceptor)
- [SSL/TLS Yapılandırması](#ssltls-yapılandırması)
- [HTTP Yanıt Modeli](#http-yanıt-modeli)
- [Yol Haritası](#yol-haritası)
- [Sorun Giderme](#sorun-giderme)
- [Lisans](#lisans)

---

## Demo/Test Uygulaması
`app` modülü, kütüphaneyi kullanarak [open-meteo](https://open-meteo.com/) üzerinden hava durumu verisi çeker ve ekranda gösterir.

Özellikler:
- `httpClient` DSL ile `baseUrl`, headers ve interceptor kurulumu
- `client.get<WeatherResponse<CurrentUnits>>(...)` örneği
- Compose ile basit bir UI ve butona basıldığında veri çekme

Çalıştırma:
1. Projeyi Android Studio ile açın
2. Cihaz/Emülatör seçin
3. `app` modülünü çalıştırın

---

## Modül Yapısı
- `GNetwork/` — Network kütüphanesi (ana odak)
- `app/` — Kütüphaneyi kullanan örnek Android uygulaması

---

## Kurulum
Bu repo bir çoklu-modül Android projesi olarak hazır gelir. Kütüphaneyi doğrudan bu repo içinde kullanabilirsiniz.

Başka bir projeye dahil etmek isterseniz (örn. monorepo):
- settings.gradle(.kts) içine `include(":GNetwork")`
- app build.gradle(.kts): `implementation(project(":GNetwork"))`

> Not: Kütüphane Kotlin Serialization kullanır; kendi projenizde de uygun Kotlin/Gradle sürümleri olmalıdır.

---

## Hızlı Başlangıç

```kotlin
val client = httpClient {
    baseUrl = "https://api.open-meteo.com/"

    // Varsayılan header'lar
    headers {
        this["accept"] = "*/*"
        this["accept-encoding"] = "gzip"
        this["accept-language"] = "en"
    }

    // Interceptor zinciri (sıra önemlidir)
    addInterceptor(AuthInterceptor { /* token sağlayın veya null dönün */ null })
    addInterceptor(LoggingInterceptor(level = LoggingInterceptor.Level.BODY))
}
```

---

## Kullanım Örnekleri

### GET
```kotlin
val resp = client.get<WeatherResponse<CurrentUnits>>("v1/forecast") {
    queryParam("latitude", "38.643976")
    queryParam("longitude", "34.734958")
    queryParam("hourly", "temperature_2m")
    queryParam("current", "temperature_2m,relative_humidity_2m") // virgüller korunur
}

resp.onSuccess { data ->
    // data.current?.temperature_2m vb.
}.onFailure { http ->
    // http.statusCode, http.errorBody
}.onError { err ->
    // err.exception (timeout, bağlantı, vb.)
}
```

### POST - JSON Gövde
```kotlin
val resp = client.post<MyResponse>("users") {
    jsonBody {
        "name" to "Gökhan Akbaş"
        "email" to "gokhan@example.com"
        "age" to 30
    }
}
```

### Query Parametreleri ve Header'lar
```kotlin
val resp = client.get<String>("search") {
    queryParams(
        "q" to "hava durumu",
        "page" to "1",
    )
    header("X-Trace-Id", "abc-123")
}
```

---

## Interceptor'lar
Interceptor mimarisi OkHttp benzeri bir zincir mantığıyla çalışır. İstek, eklediğiniz interceptor'lar üzerinden terminal (ağ) katmanına iner.

### LoggingInterceptor
Okunabilir, şık log formatı ve seviyeler:
- `NONE`: Log yok
- `BASIC`: Metot, URL, durum kodu, süre
- `HEADERS`: + istek/yanıt header'ları
- `BODY`: + istek/yanıt gövdeleri (JSON pretty-print)

Örnek:
```kotlin
addInterceptor(
    LoggingInterceptor(
        logger = { Log.d("GNetwork", it) },
        level = LoggingInterceptor.Level.BODY
    )
)
```

Daha fazla örnek ve ekran görüntüsü: [LOGGING_GUIDE.md](./LOGGING_GUIDE.md)

### AuthInterceptor
Token veya header eklemek için kullanılır. Token sağlayıcınız `suspend` fonksiyon olabilir.
```kotlin
addInterceptor(AuthInterceptor(headerName = "Authorization") { tokenProvider() })
```

### Token Authenticator (401 Otomatik Yenileme)
Sunucudan 401 Unauthorized yanıtı geldiğinde, token'ı arka planda otomatik yenileyip isteği yineler. Mutex tabanlı eşzamanlılık koruması sayesinde aynı anda birden fazla istek 401 alsa bile yenileme fonksiyonu yalnızca **1 kez** çalıştırılır. Yenileme başarısız olduğunda ise `onAuthFailed` callback'i üzerinden kullanıcı oturumu güvenle sonlandırılabilir:
```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com/"

    tokenAuthenticator(
        currentToken = { userPreferences.getAccessToken() },
        onRefreshToken = { expiredToken ->
            val newSession = authApi.refreshToken(expiredToken)
            userPreferences.saveAccessToken(newSession.accessToken)
            newSession.accessToken // Başarılıysa yeni token döner, başarısızsa null döner
        },
        onAuthFailed = {
            // Refresh token geçersizse oturumu kapatıp Login ekranına yönlendir
            userPreferences.clear()
            navigator.navigateToLogin()
        }
    )
}
```

### Retry Yapılandırması (Yeniden Deneme)
Ağ kopmaları (-1), 5xx sunucu hataları veya 429 Too Many Requests durumlarında **exponential backoff** ve **random jitter** ile otomatik yeniden deneme yapar. Sunucudan `Retry-After` başlığı geldiğinde sunucunun talep ettiği bekleme süresine (RFC 6585 & 7231) tam uyum sağlar.
> **Not:** Varsayılan olarak hem istemci hem de metot seviyesinde retry kapalıdır (`noRetry`). İstediğiniz yerde opt-in olarak açabilirsiniz.

#### 1. İstemci Düzeyinde Varsayılan Retry (Milisaniye veya Kotlin `Duration`):
```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com/"

    retryConfig {
        maxRetries = 3
        initialDelay(500.milliseconds)
        maxDelay(5.seconds)
        backoffMultiplier = 2.0
        useJitter = true
    }
}
```

#### 2. İstek (Metot) Düzeyinde Retry Açma / Ezme:
```kotlin
// İstemcide retry olmasa bile bu istek için retry açar:
client.get<WeatherResponse>("weather") {
    retry(maxRetries = 5, initialDelayMs = 500L)
}

// İstemcide retry açık olsa bile bu istek için retry'ı tamamen kapatır:
client.post<PaymentResponse>("checkout") {
    noRetry()
}
```

### HTTP Önbellekleme (Cache & ETag)
Standart HTTP `Cache-Control` (`max-age`, `no-store`) ve `ETag` (`If-None-Match`, `304 Not Modified`) kurallarına göre çalışan bellek içi (In-Memory LRU) ve kalıcı disk (DiskLruCache) önbellekleme desteği sunar. RFC 7234 Section 4.4 uyarınca `POST`, `PUT`, `DELETE` gibi mutasyon istekleri başarılı olduğunda ilgili URL'nin önbelleği otomatik olarak temizlenir.
> **Not:** Varsayılan olarak önbellekleme tamamen kapalıdır (`null`). İhtiyaç duyulan istemcilerde `memoryCache(...)` veya `diskCache(...)` ile kolayca açılabilir.

#### 1. İstemci Düzeyinde Önbellek Tanımlama:
```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com/"

    // Seçenek A: Bellek içi (In-Memory) 10 MB LRU önbellek (RAM):
    memoryCache(maxSizeBytes = 10 * 1024 * 1024L)

    // Seçenek B: Kalıcı Disk (DiskLruCache) 50 MB önbellek (Uygulama kapansa da saklanır):
    // diskCache(File(context.cacheDir, "http_cache"), maxSizeBytes = 50 * 1024 * 1024L)
}
```

#### 2. İstek (Metot) Düzeyinde Önbellek Politikası (CachePolicy):
```kotlin
// Pull-to-Refresh: Önbelleği pas geç, doğrudan ağdan en günceli getir ve önbelleğe yaz
client.get<WeatherResponse>("weather") {
    forceNetwork()
}

// Çevrimdışı Mod: Sadece önbellekten oku, ağa gitme (yoksa 504 Gateway Timeout döner)
client.get<WeatherResponse>("weather") {
    forceCache()
}
```

### Payloads & Multipart / Form-Data (Dosya ve Görsel Yükleme)
Dosya (`java.io.File`), görsel bayt dizileri (`ByteArray`), metin form alanları ve JSON nesnelerini gönderme desteği sunar. Dosya uzantısına göre MIME tipleri otomatik tespit edilir:

```kotlin
// 1. Çok Parçalı (Multipart) Form Yüklemesi:
val pdfFile = File(context.cacheDir, "ogrenci_belgesi.pdf")

val response = client.post<UploadResponse>("api/v1/student/upload") {
    multipartBody {
        part("studentId", "2024105012")
        part("documentType", "STUDENT_CERTIFICATE")
        part("documentFile", pdfFile)
        part("avatar", "selfie.jpg", imageBytes, ContentType.IMAGE_JPEG)
    }
}

// 2. Doğrudan Tekil Dosya Yükleme (ör. AWS S3 veya Cloud Storage):
client.put<Unit>("storage/uploads/avatar.png") {
    fileBody(File(context.cacheDir, "avatar.png"))
}
```

### Progress Tracking (İlerleme Dinleyicisi)
Büyük dosya yüklemeleri (upload) veya yanıt indirmeleri (download) sırasında kullanıcı arayüzünü (Jetpack Compose `LinearProgressIndicator` / ProgressBar) anlık olarak güncellemek için kullanılır. `Progress` nesnesi üzerinden aktarılan bayt, toplam bayt, yüzde, hazır biçimlendirilmiş metinler (`formattedTransferred`, `formattedTotal`) ve tamamlama bilgisine erişebilirsiniz:

```kotlin
// Compose / UI Entegrasyonu:
client.get<ByteArray>("download/package.zip") {
    onDownloadProgress { progress ->
        progressFraction.value = progress.fraction // Compose LinearProgressIndicator için 0.0f..1.0f
        statusText.value = "${progress.formattedTransferred} / ${progress.formattedTotal}" // ör. "1.2 MB / 10.5 MB"
        if (progress.isCompleted) {
            Log.d("Download", "İndirme tamamlandı!")
        }
    }
}
```

### Zaman Aşımı Yönetimi (Timeouts & Call Timeout)
Bağlantı (`connectTimeout`), okuma (`readTimeout`) ve tüm çağrıyı kapsayan tavan süreyi (`callTimeout`) hem istemci düzeyinde varsayılan olarak hem de istek bazında belirleyebilirsiniz. Sayısal milisaniye veya Kotlin `Duration` desteği mevcuttur. Girişler otomatik olarak `.coerceAtLeast(0)` ile negatif değerlere karşı korunur.

```kotlin
// 1. İstemci Düzeyinde Varsayılan Timeout'lar:
val client = httpClient {
    baseUrl = "https://api.example.com/"
    connectTimeout(10.seconds)
    readTimeout(20.seconds)
    callTimeout(30.seconds) // Toplam tavan süre (DNS + TLS + Retry + Body); aşılırsa soket anında kapatılır
}

// 2. İstek Düzeyinde Özelleştirme (Override):
client.post<UploadResponse>("api/v1/heavy-upload") {
    multipartBody { part("video", videoFile) }
    
    // Yalnızca bu istek için zaman aşımlarını uzatıyoruz:
    callTimeout(2.minutes)
    readTimeout(60.seconds)
}
```

### Performans ve Ağ Metrikleri (Metrics & Diagnostics)
Ağ çağrılarının toplam süresini nanosaniye hassasiyetinde (`System.nanoTime()` ile saat kaymalarından etkilenmeden), giden ve gelen bayt boyutlarını (`formattedSent`, `formattedReceived`), durum kodunu ve yanıtın önbellekten gelip gelmediğini (`isFromCache`) ölçümleyen `MetricsInterceptor` sunar. Üçüncü taraf APM hataları ağ isteklerini asla aksatmayacak şekilde izole edilmiştir (`runCatching`).

```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com/"

    // 1. Konsol veya Logcat için Hazır Formatlı Loglayıcı:
    addInterceptor(MetricsInterceptor.logging { Log.d("NetworkMetrics", it) })

    // 2. Özel Analitik / APM Entegrasyonu (Firebase Performance, Datadog vb.):
    addInterceptor(MetricsInterceptor { metrics ->
        Log.d(
            "Metrics",
            "${metrics.method} ${metrics.url} -> ${metrics.statusCode} " +
            "(${metrics.durationMs}ms, Önbellek: ${metrics.isFromCache}, Giden: ${metrics.formattedSent}, Gelen: ${metrics.formattedReceived})"
        )
    })
}
```

---

## SSL/TLS Yapılandırması
- Özel `SSLSocketFactory` ve `X509TrustManager` ile kurumsal CA/self-signed sertifikalar
- `HostnameVerifier` özelleştirme (gerekirse)
- Certificate Pinning: SHA-256 public key hash sabitleme, hiyerarşik wildcard alan adı (`*.example.com`) eşleştirme ve katı pin doğrulama

Örnek: Akıcı DSL ile Certificate Pinning
```kotlin
val client = httpClient {
    sslConfig {
        certificatePinner {
            add("api.example.com", "sha256/PRIMARY_PIN_BASE64=", "sha256/BACKUP_PIN_BASE64=")
            add("*.backend.org", "sha256/WILDCARD_PIN_BASE64=")
        }
    }
}
```

Debug (sadece geliştirme/test için):
```kotlin
sslConfig { trustAllCertificates() } // Production'da KULLANMAYIN!
```

Detaylı rehber: [SSL_TLS_GUIDE.md](./SSL_TLS_GUIDE.md)

---

## HTTP Yanıt Modeli
```kotlin
sealed class HttpResponse<out T> {
    data class Success<T>(val body: T, val statusCode: Int, val headers: ResponseHeaders, val rawResponse: String?)
    data class Failure(val statusCode: Int, val errorMessage: String, val errorBody: String?, val headers: ResponseHeaders)
    data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error")
}
```
Ergonomi yardımcıları:
- `onSuccess {}`, `onFailure {}`, `onError {}`
- `getOrNull()`, `getOrDefault(default)`, `getOrThrow()`

---

## Yol Haritası
- [x] TokenAuthenticator (Authenticator arayüzü, BearerTokenAuthenticator ve 401 Mutex retry)
- [x] RetryInterceptor (Exponential backoff, jitter, client ve istek bazlı özelleştirilebilir retry)
- [x] CacheInterceptor (ETag, Cache-Control, In-Memory LRU Cache, 304 Not Modified, CachePolicy)
- [x] DiskLruCache (Kalıcı dosya tabanlı, uygulama yeniden başlatılsa bile saklanan çift dosyalı LRU önbellek)
- [x] Multipart/Form-Data (Dosya, görsel, binary ve form alanları yükleme desteği)
- [x] Progress takibi (Upload ve download anlık ilerleme dinleyicisi: yüzde, bayt, tamamlama)
- [x] Timeout Yönetimi (Client ve request düzeyinde connect, read ve callTimeout; Kotlin Duration desteği)
- [x] Metrics/AnalyticsInterceptor (İstek süreleri, giden/gelen bayt boyutları, cache teşhisi ve loglama interceptor'ı)
- [ ] Redirect yönetimi (307/308 method/body preservation)

---

## Sorun Giderme
- `,` (virgül) encode edilmeden gönderilir (open-meteo gibi API'lar için gereklidir).
- `Accept-Encoding: gzip` varsayılan olarak eklenir; yanıt `Content-Encoding: gzip` ise otomatik açılır.
- `charset` `Content-Type` üzerinden okunur; yoksa UTF-8 varsayılır.
- Coroutine iptalinde `disconnect()` çağrılır (cancel destekli).

---

## Üçüncü Taraf Lisansları
Bu projede kullanılan bağımlılıkların lisansları için: [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)

---

## Lisans
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

Bu proje MIT lisansı ile lisanslanmıştır. Ayrıntılar için [LICENSE](./LICENSE) dosyasına bakınız.
