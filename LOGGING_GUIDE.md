# 📊 LoggingInterceptor - Yeni Görünüm

## ✨ Özellikler

### 1. **Profesyonel Box-Drawing Karakterler**
- `╔═══╗` üst kenar
- `║` yan kenarlar  
- `╠═══╣` bölüm ayırıcı
- `╚═══╝` alt kenar
- `┌─` alt bölüm başlığı

### 2. **Status Code Emoji İşaretleri**
- `✓` 2xx (Başarılı)
- `↪` 3xx (Yönlendirme)
- `⚠` 4xx (Client Hatası)
- `✗` 5xx (Server Hatası)
- `?` Diğer

### 3. **Otomatik JSON Formatlama**
- JSON body'ler otomatik güzel formatlanır
- İç içe objeler girintili gösterilir
- 10.000 karakter limiti (çok büyük yanıtlar için)

### 4. **Hiyerarşik Yapı**
- Request ve Response net ayrılmış
- Headers ve Body alt bölümler
- Her şey düzenli girintili

---

## 📝 Örnek Çıktılar

### BASIC Level (Sadece URL ve Status)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
GET https://api.open-meteo.com/v1/forecast?latitude=38.643976&longitude=34.734958
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✓ 200 OK (234ms)
╚═══════════════════════════════════════════════════════════════
```

### HEADERS Level (Headers Dahil)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
POST https://api.example.com/users
┌─ Headers
  Content-Type: application/json
  Authorization: Bearer eyJhbGc...
  User-Agent: GNetwork/1.0
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✓ 201 Created (456ms)
┌─ Headers
  Content-Type: application/json; charset=utf-8
  Content-Length: 142
  Date: Sun, 17 Nov 2025 10:30:00 GMT
╚═══════════════════════════════════════════════════════════════
```

### BODY Level (Body Dahil - JSON Formatlanmış)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
POST https://api.example.com/users
┌─ Headers
  Content-Type: application/json
  Authorization: Bearer eyJhbGc...
┌─ Request Body
  {
    "name": "Gökhan Akbaş",
    "email": "gokhan@example.com",
    "age": 30,
    "city": "Ankara"
  }
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✓ 201 Created (456ms)
┌─ Headers
  Content-Type: application/json; charset=utf-8
  Content-Length: 142
┌─ Response Body
  {
    "id": "12345",
    "name": "Gökhan Akbaş",
    "email": "gokhan@example.com",
    "age": 30,
    "city": "Ankara",
    "created_at": "2025-11-17T10:30:00Z"
  }
╚═══════════════════════════════════════════════════════════════
```

### Hata Durumu (4xx)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
GET https://api.example.com/users/999
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
⚠ 404 Not Found (123ms)
┌─ Response Body
  {
    "error": "User not found",
    "code": "USER_NOT_FOUND"
  }
╚═══════════════════════════════════════════════════════════════
```

### Server Hatası (5xx)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
POST https://api.example.com/payment
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✗ 500 Internal Server Error (5432ms)
┌─ Response Body
  {
    "error": "Database connection failed",
    "trace_id": "abc-123-xyz"
  }
╚═══════════════════════════════════════════════════════════════
```

### Weather API Örneği (Gerçek)
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
GET https://api.open-meteo.com/v1/forecast?latitude=38.643976&longitude=34.734958&hourly=temperature_2m&current=temperature_2m,relative_humidity_2m
┌─ Headers
  accept: */*
  accept-encoding: gzip
  accept-language: en
  connection: Keep-Alive
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✓ 200 OK (234ms)
┌─ Headers
  content-type: application/json; charset=utf-8
  content-encoding: gzip
  date: Sun, 17 Nov 2025 10:30:00 GMT
┌─ Response Body
  {
    "latitude": 38.643976,
    "longitude": 34.734958,
    "generationtime_ms": 0.234,
    "current": {
      "time": "2025-11-17T10:30",
      "temperature_2m": 15.4,
      "relative_humidity_2m": 65
    },
    "current_units": {
      "temperature_2m": "°C",
      "relative_humidity_2m": "%"
    },
    "hourly": {
      "time": [
        "2025-11-17T00:00",
        "2025-11-17T01:00",
        "2025-11-17T02:00"
      ],
      "temperature_2m": [
        12.5,
        13.2,
        14.1
      ]
    }
  }
╚═══════════════════════════════════════════════════════════════
```

---

## 🎨 Görsel Hiyerarşi

```
╔═══ REQUEST ═══════════════════════════════════════════════════
║ [METHOD] [URL]
╠═══════════════════════════════════════════════════════════════
┌─ Headers
  key: value
  key: value
┌─ Request Body
  { json içeriği girintili }
╚═══════════════════════════════════════════════════════════════

╔═══ RESPONSE ══════════════════════════════════════════════════
║ [EMOJI] [STATUS] [MESSAGE] ([TIME]ms)
╠═══════════════════════════════════════════════════════════════
┌─ Headers
  key: value, value
┌─ Response Body
  { json içeriği girintili }
╚═══════════════════════════════════════════════════════════════
```

---

## 🔧 Kullanım

### MainActivity'de Kullanımı:
```kotlin
val client = httpClient {
    baseUrl = "https://api.open-meteo.com/"
    
    addInterceptor(
        LoggingInterceptor(
            logger = { Log.d("GNetwork", it) },
            level = LoggingInterceptor.Level.BODY // veya BASIC, HEADERS
        )
    )
}
```

### Log Seviyeleri:

| Level | İçerik |
|-------|--------|
| **NONE** | Log yok |
| **BASIC** | Sadece URL ve status code |
| **HEADERS** | BASIC + request/response headers |
| **BODY** | HEADERS + request/response body (JSON formatlanmış) |

---

## 📱 Android Logcat'te Görünüm

```
D/GNetwork: ╔═══════════════════════════════════════════════════════════════
D/GNetwork: ║ REQUEST
D/GNetwork: ╠═══════════════════════════════════════════════════════════════
D/GNetwork: GET https://api.open-meteo.com/v1/forecast?latitude=38.643976...
D/GNetwork: ┌─ Headers
D/GNetwork:   accept: */*
D/GNetwork:   accept-encoding: gzip
D/GNetwork: ╚═══════════════════════════════════════════════════════════════
D/GNetwork: 
D/GNetwork: ╔═══════════════════════════════════════════════════════════════
D/GNetwork: ║ RESPONSE
D/GNetwork: ╠═══════════════════════════════════════════════════════════════
D/GNetwork: ✓ 200 OK (234ms)
D/GNetwork: ┌─ Response Body
D/GNetwork:   {
D/GNetwork:     "latitude": 38.643976,
D/GNetwork:     "current": {
D/GNetwork:       "temperature_2m": 15.4
D/GNetwork:     }
D/GNetwork:   }
D/GNetwork: ╚═══════════════════════════════════════════════════════════════
D/GNetwork: 
```

---

## 💡 Avantajlar

### Önceki Versiyon:
```
→ GET https://api.example.com/users
→ H: Authorization: Bearer ...
→ Body: {"name":"John"}
← 200 (234ms)
← H: Content-Type: application/json
← Body: {"id":"123","name":"John","email":"john@example.com"}
```

### Yeni Versiyon:
```
╔═══════════════════════════════════════════════════════════════
║ REQUEST
╠═══════════════════════════════════════════════════════════════
POST https://api.example.com/users
┌─ Headers
  Authorization: Bearer ...
┌─ Request Body
  {
    "name": "John"
  }
╚═══════════════════════════════════════════════════════════════

╔═══════════════════════════════════════════════════════════════
║ RESPONSE
╠═══════════════════════════════════════════════════════════════
✓ 200 OK (234ms)
┌─ Response Body
  {
    "id": "123",
    "name": "John",
    "email": "john@example.com"
  }
╚═══════════════════════════════════════════════════════════════
```

---

## 🎯 Sonuç

Yeni logging format:
- ✅ **Daha okunabilir** - Box karakterlerle net ayrım
- ✅ **Daha profesyonel** - Emoji status göstergeleri
- ✅ **Daha kullanışlı** - JSON otomatik formatlanıyor
- ✅ **Daha organize** - Hiyerarşik yapı
- ✅ **Debug dostu** - Logcat'te kolayca takip edilebilir

---

**Yapılan İyileştirmeler:**
1. Box-drawing karakterler (╔═╗ ║ ╚═╝)
2. Status code emoji'leri (✓ ⚠ ✗)
3. Otomatik JSON pretty-print
4. Hiyerarşik bölümler (Headers, Body)
5. Her log satırı düzenli girintili
6. Request ve Response net ayrılmış
7. Timing bilgisi daha görünür
