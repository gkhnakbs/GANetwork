# GANetwork

Modern Kotlin/Android için basit, esnek ve genişletilebilir bir network kütüphanesi. Bu repo, hem kütüphaneyi (GNetwork) hem de onu kullanan küçük bir test uygulamasını (app) içerir.

- Kolay DSL: `httpClient {}`, `client.get<T>(...) {}`
- Interceptor zinciri: Logging, Auth ve kolay genişletilebilir mimari
- SSL/TLS: Özel TrustManager, HostnameVerifier ve Certificate Pinning
- GZip ve charset desteği; doğru gövde okuma
- Query parametrelerinin güvenli encode edilmesi (virgüller korunur)
- Net sonuç modeli: `HttpResponse.Success/Failure/Error`

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

---

## SSL/TLS Yapılandırması
- Özel `SSLSocketFactory` ve `X509TrustManager` ile kurumsal CA/self-signed sertifikalar
- `HostnameVerifier` özelleştirme (gerekirse)
- Certificate Pinning (public key SHA-256)

Örnek: Certificate Pinning
```kotlin
sslConfig {
    certificatePinner(
        CertificatePinner.builder()
            .add(
                "api.example.com",
                "sha256/PRIMARY_PIN_BASE64=",
                "sha256/BACKUP_PIN_BASE64="
            )
            .build()
    )
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
- RetryInterceptor (exponential backoff, idempotent metodlar)
- CacheInterceptor (ETag, Cache-Control, disk/memory)
- Multipart/Form-Data (dosya upload)
- Progress takibi (upload/download)
- TimeoutInterceptor (call-level timeout)
- Metrics/AnalyticsInterceptor
- Redirect yönetimi (307/308 method/body preservation)

---

## Sorun Giderme
- `,` (virgül) encode edilmeden gönderilir (open-meteo gibi API'lar için gereklidir).
- `Accept-Encoding: gzip` varsayılan olarak eklenir; yanıt `Content-Encoding: gzip` ise otomatik açılır.
- `charset` `Content-Type` üzerinden okunur; yoksa UTF-8 varsayılır.
- Coroutine iptalinde `disconnect()` çağrılır (cancel destekli).

---

## Lisans
Bu proje için lisans dosyasını ekleyin veya burada belirtin (örn. MIT).

