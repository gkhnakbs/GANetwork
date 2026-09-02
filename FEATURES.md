# GANetwork Feature Specification & Architecture Guide

A lightweight, production-ready, pure Kotlin/JVM HTTP client library built from scratch on standard Java `HttpURLConnection` and Kotlin Coroutines. Designed for high performance, modularity, and zero unnecessary dependencies on Android and JVM platforms.

---

## Table of Contents
1. [Core Engine & Coroutines Architecture](#1-core-engine--coroutines-architecture)
2. [Interceptor Pipeline](#2-interceptor-pipeline)
3. [Authentication & Token Refresh](#3-authentication--token-refresh)
4. [Resilience & Retry Engine](#4-resilience--retry-engine)
5. [HTTP Caching Architecture](#5-http-caching-architecture)
6. [Payloads & Multipart Uploads](#6-payloads--multipart-uploads)
7. [Upload & Download Progress Tracking](#7-upload--download-progress-tracking)
8. [Timeout & Call-Level Ceiling Management](#8-timeout--call-level-ceiling-management)
9. [Performance Metrics & Diagnostics](#9-performance-metrics--diagnostics)
10. [SSL/TLS Security & Certificate Pinning](#10-ssltls-security--certificate-pinning)

---

## 1. Core Engine & Coroutines Architecture

* **Zero Heavy HTTP Dependencies:** Built directly on top of standard `java.net.HttpURLConnection`, avoiding large binary overhead from third-party engines.
* **Coroutines First & Cooperative Cancellation:** All network operations execute asynchronously on `Dispatchers.IO` using `suspendCancellableCoroutine`. Cancellation signals (`CancellationException`) are propagated cleanly, and `connection.disconnect()` immediately aborts underlying sockets to prevent resource leaks.
* **Multi-Format Deserialization:**
  - **Dynamic Models:** Any custom data class annotated with `@Serializable` is automatically parsed using `kotlinx.serialization.json.Json`.
  - **Raw Text (`String`):** Unparsed plain text/HTML without JSON decoding overhead.
  - **Binary Downloads (`ByteArray`):** Direct binary payload retrieval for images, PDFs, audio, and documents.
  - **Empty Responses (`Unit`):** Zero-overhead handling for endpoints returning `204 No Content` or empty bodies.
* **Type-Safe Result Hierarchy:**
  - `HttpResponse.Success<T>`: HTTP 2xx with deserialized typed body, headers, and raw string response.
  - `HttpResponse.Failure<T>`: HTTP non-2xx (4xx, 5xx) with status code, message, headers, and raw error body.
  - `HttpResponse.Error<T>`: Network or system exceptions (DNS failures, connection aborts, socket & call timeouts via `isTimeout`).
* **Ergonomic Functional Extensions (`HttpResponseExtensions`):**
  - Chainable handlers: `.onSuccess { ... }`, `.onFailure { ... }`, `.onError { ... }`
  - Safe unwrapping: `.getOrNull()`, `.getOrDefault(default)`, `.getOrThrow()`
  - Transformations: `.map { ... }`, `.fold(onSuccess, onFailure, onError)`

```kotlin
val client = httpClient {
    baseUrl = "https://api.example.com/"
}

// 1. Typed JSON request with fluent chaining:
client.get<UserDto>("users/1001")
    .onSuccess { user -> println("User loaded: ${user.name}") }
    .onFailure { fail -> println("Server error ${fail.statusCode}: ${fail.errorMessage}") }
    .onError { err -> if (err.isTimeout) println("Request timed out!") }

// 2. Direct binary download (ByteArray):
val imageBytes: ByteArray? = client.get<ByteArray>("avatar.png").getOrNull()

// 3. Void endpoint (Unit / 204 No Content):
client.post<Unit>("auth/logout").onSuccess { println("Logged out successfully") }
```

---

## 2. Interceptor Pipeline

Implements the **Chain of Responsibility** pattern, enabling modular request/response transformation, logging, header injection, caching, and diagnostics.

* **Components:**
  - `Interceptor`: Contract receiving `Interceptor.Chain` and returning `RawResponse`.
  - `RealInterceptorChain`: Immutable execution state holding the ordered interceptor list and current index.
  - `TerminalInterceptor`: Terminal chain node executing the actual socket connection.
  - `Interceptor.Chain.proceed(request = this.request)`: Advances the chain with a default parameter, enabling pure observation interceptors to simply call `chain.proceed()`.
* **Execution Flow:**
  `Client -> RetryInterceptor -> CacheInterceptor -> Custom Interceptors -> TerminalInterceptor (Network)`
* **Safe Diagnostics (`LoggingInterceptor`):**
  - Displays formatted JSON bodies, request/response headers, and response duration emojis.
  - Safely detects binary/multipart payloads (`ByteArray`, file uploads, images, audio, PDFs) and logs payload summaries (`[Binary payload: 245 KB (image/png)]`) without memory spikes or console corruption.

```kotlin
// Example: Modifying headers in an interceptor:
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val updatedRequest = chain.request.copy(
            headers = chain.request.headers + ("X-Api-Key" to apiKey)
        )
        return chain.proceed(updatedRequest)
    }
}

// Example: Pure observer interceptor (using default parameter):
class LatencyObserver : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): RawResponse {
        val start = System.currentTimeMillis()
        val response = chain.proceed() // defaults to chain.request
        println("Completed in ${System.currentTimeMillis() - start}ms")
        return response
    }
}
```

---

## 3. Authentication & Token Refresh

Solves 401 Unauthorized handling cleanly with thread-safe, mutex-protected token refresh.

* **Mutex Synchronization:** When multiple concurrent requests fail with 401, a shared `Mutex` ensures **only one** refresh request hits the authentication server. Subsequent requests wait for the mutex and reuse the newly acquired token.
* **Case-Insensitive Header Sanitization:** Safely matches authorization headers regardless of casing (`Authorization`, `authorization`) and strips existing headers before attaching refreshed tokens, eliminating duplicate header corruption.
* **Session Expiry Hook (`onAuthFailed`):** Triggers a callback when refresh tokens expire or are rejected (`null`), allowing apps to clear credentials and navigate to login.
* **Infinite Loop Protection:** Automatic single-retry limit prevents endless 401 retry cycles when credentials are permanently revoked.
* **Implementations:**
  - `Authenticator.NONE`: Default no-op behavior.
  - `BearerTokenAuthenticator`: Built-in provider supporting custom auth headers, token prefixes, double-checked locking token supplier, `onAuthFailed` session expiry, and suspend refresh blocks.

```kotlin
val client = httpClient {
    tokenAuthenticator(
        currentToken = { tokenStorage.getAccessToken() },
        onAuthFailed = {
            sessionManager.clearSession()
            navigator.navigateToLogin()
        },
        onRefreshToken = { expiredToken ->
            authService.refreshToken(expiredToken)
        }
    )
}
```

---

## 4. Resilience & Retry Engine

Guards against transient network drops and temporary server hiccups using exponential backoff with full randomized jitter.

* **Strict Opt-in by Default:** By default, retries are disabled (`maxRetries = 0`). Safe by design for non-idempotent operations.
* **Server-Driven Rate Limit Respect (`Retry-After`):** When encountering `429 Too Many Requests` or `503 Service Unavailable`, respects the server's `Retry-After` header delay instead of retrying prematurely, adhering to RFC 6585 and RFC 7231.
* **Exponential Backoff & Full Jitter:** Automatically spaces out subsequent attempts with randomized jitter to prevent thundering herd spikes.
* **Retry Criteria:**
  - Transient HTTP Statuses: 408 (Request Timeout), 429 (Too Many Requests), 500, 502, 503, 504.
  - Transient IO Exceptions: `SocketTimeoutException`, `ConnectException`, `UnknownHostException`.
* **Configurable Scope & Kotlin Duration:** Configure client-wide defaults using numeric milliseconds or Kotlin `Duration` (`1.seconds`, `10.seconds`), and override per-request.

```kotlin
// Client level with Kotlin Duration:
val client = httpClient {
    retryConfig {
        maxRetries = 3
        initialDelay(500.milliseconds)
        maxDelay(5.seconds)
        backoffMultiplier = 2.0
        useJitter = true
    }
}

// Request level override:
client.get<ApiResponse>("critical-data") {
    retry { maxRetries = 5 }
}
```

---

## 5. HTTP Caching Architecture

Dual-engine caching adhering to RFC HTTP caching specifications (`ETag`, `Cache-Control`, `304 Not Modified`).

* **Features:**
  - `CachePolicy.DEFAULT`: Returns fresh cached response; sends conditional headers (`If-None-Match`, `If-Modified-Since`) if stale; merges headers on 304.
  - `CachePolicy.FORCE_NETWORK`: Bypasses cache, forces network fetch, updates cache if cacheable (ideal for pull-to-refresh).
  - `CachePolicy.FORCE_CACHE`: Strictly serves from local cache; returns 504 Gateway Timeout if missing or stale (ideal for offline mode).
  - `RFC 7234 Section 4.4 Invalidation`: Automatically invalidates the cached URL when state-modifying requests (`POST`, `PUT`, `DELETE`) succeed (2xx), preventing stale data desynchronization.
  - Diagnostic Header: Tags responses with `X-GANetwork-Cache: HIT`, `MISS`, or `CONDITIONAL_HIT`.

### Storage Options

| Strategy | Engine Class | Storage Medium | Persistence |
|---|---|---|---|
| **In-Memory** | `MemoryLruCache` | JVM Heap / RAM | Lost on process termination |
| **Persistent Disk** | `DiskLruCache` | Local Filesystem (`File`) | Retained across restarts and reboots |

* **`DiskLruCache` Technical Highlights:**
  - **Two-File Architecture:** `${sha256(key)}.meta` (status, headers, timestamps) + `${sha256(key)}.body` (raw binary bytes).
  - **Crash-Safe Atomic Writes:** Writes to temporary `.tmp` files before renaming to prevent corrupted files during sudden power loss or process crashes.
  - **Startup Recovery:** Rebuilds in-memory LRU tracking on launch by scanning file metadata sorted by `lastModified`.

```kotlin
// In-Memory:
httpClient { memoryCache(maxSizeBytes = 10 * 1024 * 1024L) }

// Persistent Disk:
httpClient { diskCache(File(context.cacheDir, "http_cache"), maxSizeBytes = 50 * 1024 * 1024L) }
```

---

## 6. Payloads & Multipart Uploads

Comprehensive support for transferring JSON, URL-encoded forms, raw binary data, and standard multipart file uploads.

* **Data Types Supported:**
  - Structured JSON objects / arrays via DSL (`jsonBody`).
  - Form URL-encoded key-value pairs (`formBody`).
  - Plain text strings (`textBody`).
  - Raw binary byte arrays (`binaryBody(ByteArray)`).
  - Single-file binary uploads with auto-MIME detection (`fileBody(File)`).
* **Multipart/Form-Data (RFC 7578 / RFC 2046):**
  - Text form fields: `part(name, value)`.
  - Files (`java.io.File`): Automatic MIME detection by extension (`.pdf` -> `application/pdf`, `.jpg` -> `image/jpeg`).
  - Raw binary parts: `part(name, filename, bytes, contentType)`.
  - Automatic boundary generation avoiding boundary collisions.
  - Quote sanitization (`%22`) in part headers preventing header injection.
  - Preserves binary integrity by avoiding UTF-8 string encoding mutations on binary parts.

```kotlin
// 1. Multipart multi-part upload:
val response = client.post<UploadResponse>("api/v1/documents") {
    multipartBody {
        part("userId", "10045")
        part("documentType", "STUDENT_CERTIFICATE")
        part("certificate", File(context.cacheDir, "doc.pdf"))
        part("signature", "sign.png", signatureBytes, ContentType.IMAGE_PNG)
    }
}

// 2. Direct single file upload (e.g. to AWS S3 / Cloud Storage):
client.put<Unit>("storage/uploads/avatar.png") {
    fileBody(File(context.cacheDir, "avatar.png"))
}
```

---

## 7. Upload & Download Progress Tracking

Real-time streaming observation for both outgoing payloads and incoming responses.

* **Chunked Streaming:** Reads and writes in 8 KB buffers, publishing periodic progress without buffering entire streams in unnecessary intermediate memory.
* **Rich `Progress` Model:**
  - `bytesTransferred: Long`: Total bytes transferred so far.
  - `totalBytes: Long`: Expected byte length (or -1 if `Content-Length` is missing / chunked transfer).
  - `percentage: Int`: Computed integer percentage (0..100) or -1.
  - `fraction: Float`: Normalized float (0.0f..1.0f) tailored directly for Jetpack Compose `LinearProgressIndicator`.
  - `isCompleted: Boolean`: Guaranteed completion flag (`true` when complete, including chunked streams).
  - `formattedTransferred`: Human-readable transferred size (e.g. `1.2 MB`, `450 KB`).
  - `formattedTotal`: Human-readable total size (e.g. `10.5 MB` or `Unknown`).
* **Chunked Stream Completion Guarantee:** When downloading streams with unknown `Content-Length`, an automatic completion event is fired upon EOF so UI bars gracefully dismiss.
* **Zero Overhead:** When listeners are not registered, standard single-pass reads/writes are utilized.

```kotlin
// Compose integration example:
client.get<ByteArray>("download/package.zip") {
    onDownloadProgress { progress ->
        progressState.value = progress.fraction
        statusText.value = "${progress.formattedTransferred} / ${progress.formattedTotal}"
        if (progress.isCompleted) {
            println("Download finished!")
        }
    }
}
```

---

## 8. Timeout & Call-Level Ceiling Management

Granular socket configuration and total request lifecycle guarantees.

* **Timeout Layers:**
  - `connectTimeout`: Maximum duration to establish the TCP/TLS socket connection (`DEFAULT_CONNECT_TIMEOUT_MS = 10_000 ms`).
  - `readTimeout`: Maximum duration of inactivity between two consecutive data packets (`DEFAULT_READ_TIMEOUT_MS = 20_000 ms`).
  - `callTimeout`: **Hard ceiling** on the entire HTTP transaction (`DEFAULT_CALL_TIMEOUT_MS = 0L`, disabled). Bounded via Coroutines `withTimeout`, cancelling the call and immediately closing the socket upon expiration.
* **Input Safety:** Automatically guards against negative values via `.coerceAtLeast(0)` across all builder setters, preventing socket-level `IllegalArgumentException` crashes.
* **Units:** Accepts milliseconds (`Int`/`Long`) or Kotlin `kotlin.time.Duration` (`15.seconds`, `2.minutes`).
* **Inheritance & Overrides:** Client-level defaults apply automatically, and can be overridden per request.

```kotlin
// Client level defaults:
val client = httpClient {
    connectTimeout(10.seconds)
    readTimeout(20.seconds)
    callTimeout(30.seconds)
}

// Request level override:
client.post<UploadResponse>("heavy-upload") {
    callTimeout(3.minutes)
    readTimeout(60.seconds)
}
```

---

## 9. Performance Metrics & Diagnostics

Non-intrusive monitoring of network execution latency, data transfer volumes, and caching efficiency.

* **`NetworkMetrics` Data Attributes:**
  - `url`: Target endpoint.
  - `method`: HTTP verb (GET, POST, etc.).
  - `statusCode`: HTTP status or -1 on network failure.
  - `durationMs`: High-precision monotonic roundtrip duration (measured via `System.nanoTime()`, immune to device clock shifts).
  - `sentBytes` / `receivedBytes`: Transfer volumes.
  - `formattedSent` / `formattedReceived`: Pre-formatted human-readable strings (e.g. `450 B`, `1.5 MB`).
  - `isSuccessful`: True if status in 200..299.
  - `isFromCache`: True if fulfilled by local memory or disk cache.
  - `exception`: Associated exception if failed.
* **Fault-Tolerant Telemetry Dispatch:** `listener.onMetricsCollected` is isolated with `runCatching` to guarantee that third-party APM or logging errors never crash or disrupt ongoing network requests.
* **Integration:** Standalone `MetricsInterceptor` pluggable into any client pipeline, with built-in formatted logger for console and Android Logcat.

```kotlin
val client = httpClient {
    // 1. Built-in formatted logger:
    addInterceptor(MetricsInterceptor.logging { Log.d("NetworkStats", it) })

    // 2. Custom APM integration (Firebase / Datadog):
    addInterceptor(MetricsInterceptor { metrics ->
        FirebasePerformance.getInstance()
            .newHttpMetric(metrics.url, metrics.method.name)
            .apply {
                setHttpResponseCode(metrics.statusCode)
                setRequestPayloadSize(metrics.sentBytes)
                setResponsePayloadSize(metrics.receivedBytes)
                stop()
            }
    })
}
```

---

## 10. SSL/TLS Security & Certificate Pinning

Enterprise-grade cryptographic protections and pinning support for secure communications.

* **Custom Trust Management:** Easily plug custom `SSLSocketFactory`, `X509TrustManager`, and `HostnameVerifier` for corporate CAs or internal gateways.
* **Certificate Pinning (`CertificatePinner`):**
  - SHA-256 public key hash pinning against hostnames (`sha256/<base64>`).
  - Hierarchical wildcard domain support (e.g. `*.example.com` correctly matches `api.example.com` and nested subdomains).
  - Multiple pin entries per domain for seamless zero-downtime key rotations.
  - Fail-secure validation: Throws `IllegalArgumentException` on malformed pin definitions to prevent accidental security bypass, and throws `SSLPeerUnverifiedException` with socket abort upon pin mismatch.
  - Fluent DSL configuration directly within `sslConfig`.

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

---

## Architecture Summary Matrix

| Category | Primary Classes | Key DSL Methods |
|---|---|---|
| **Core Engine** | `HttpClient`, `HttpRequest`, `HttpResponse` | `httpClient { ... }`, `client.get`, `client.post` |
| **Interceptors** | `Interceptor`, `Interceptor.Chain`, `RealInterceptorChain` | `addInterceptor(...)` |
| **Auth & Mutex** | `Authenticator`, `BearerTokenAuthenticator` | `tokenAuthenticator { ... }` |
| **Retry & Jitter** | `RetryConfig`, `RetryInterceptor` | `retryConfig { ... }`, `retry { ... }` |
| **Memory Cache** | `MemoryLruCache`, `CacheInterceptor`, `CachePolicy` | `memoryCache(...)`, `forceNetwork()`, `forceCache()` |
| **Disk Cache** | `DiskLruCache` | `diskCache(directory, maxSizeBytes)` |
| **Multipart Upload** | `MultipartBodyBuilder`, `MultipartPart`, `MimeTypeHelper` | `multipartBody { part(...) }` |
| **Progress Tracking** | `Progress`, `ProgressListener` | `onUploadProgress { ... }`, `onDownloadProgress { ... }` |
| **Timeouts** | `HttpClient`, `HttpRequest` | `connectTimeout(...)`, `readTimeout(...)`, `callTimeout(...)` |
| **Metrics & APM** | `NetworkMetrics`, `MetricsInterceptor`, `MetricsListener` | `MetricsInterceptor.logging(...)` |
| **SSL & Pinning** | `SSLConfig`, `CertificatePinner` | `sslConfig { certificatePinner(...) }` |
