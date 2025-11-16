# Third-Party Notices

Bu dosya, projede kullanılan üçüncü taraf bağımlılıkları ve lisanslarını bilgi amaçlı listeler. 
Liste, `gradle/libs.versions.toml` ve `build.gradle.kts` dosyalarındaki sürümlere göre hazırlanmıştır.

Not: Bu liste bilgilendirme amaçlıdır; resmi lisans metinleri için ilgili projelerin depolarını ve dağıtımlarını referans alınız.

---

## GNetwork (Kütüphane) Bağımlılıkları

1) kotlinx-serialization-json
- Grup/Ad: org.jetbrains.kotlinx:kotlinx-serialization-json
- Sürüm: 1.9.0
- Lisans: Apache License 2.0
- Kaynak: https://github.com/Kotlin/kotlinx.serialization
- Lisans Metni: https://www.apache.org/licenses/LICENSE-2.0

2) kotlinx-coroutines-core
- Grup/Ad: org.jetbrains.kotlinx:kotlinx-coroutines-core
- Sürüm: 1.10.2
- Lisans: Apache License 2.0
- Kaynak: https://github.com/Kotlin/kotlinx.coroutines
- Lisans Metni: https://www.apache.org/licenses/LICENSE-2.0

---

## app (Demo/Test Uygulaması) Bağımlılıkları

Aşağıdaki bağımlılıklar demo uygulaması içindir; kütüphanenin (GNetwork) dağıtımında yer almaz.

AndroidX / Compose (genel):
- Lisans: Apache License 2.0
- Genel Lisans Metni: https://www.apache.org/licenses/LICENSE-2.0

Bileşenler (seçilmiş):
- androidx.core:core-ktx — 1.17.0 — Apache-2.0
- androidx.lifecycle:lifecycle-runtime-ktx — 2.9.4 — Apache-2.0
- androidx.activity:activity-compose — 1.11.0 — Apache-2.0
- androidx.compose:compose-bom — 2025.11.00 — Apache-2.0
- androidx.compose:ui, ui-graphics, ui-tooling, ui-tooling-preview, ui-test-junit4, ui-test-manifest — Apache-2.0
- androidx.compose.material3:material3 — Apache-2.0
- androidx.test.ext:junit — 1.3.0 — Apache-2.0
- androidx.test.espresso:espresso-core — 3.7.0 — Apache-2.0

Test:
- junit:junit — 4.13.2 — Eclipse Public License 1.0 (EPL-1.0)
  - Projede test kapsamındadır; kütüphane dağıtımında yer almaz.
  - EPL-1.0: https://www.eclipse.org/legal/epl-v10.html

---

## Notlar ve Yükümlülükler

- Apache License 2.0 (Apache-2.0):
  - Lisans metni ürünle birlikte sağlanmalıdır (genelde bu dosyada link verilmesi ve LICENSE dosyası yeterlidir).
  - NOTICE dosyası varsa korunmalıdır. AndroidX ve Kotlinx projelerinde gerekebilecek NOTICE içeriklerini yayınlarken dahil etmeyi değerlendirin.

- Eclipse Public License 1.0 (EPL-1.0):
  - Bu projede yalnızca test bağımlılığıdır. Ürün dağıtımında yer almadığı sürece ek bir yükümlülük doğurmaz.

- Bu listeyi güncel tutmak için otomasyon önerisi:
  - Gradle License Report Plugin: https://github.com/jk1/Gradle-License-Report
  - Rapor komutu: `./gradlew licenseReport` (konfigürasyon sonrası)

---

Son güncelleme: 2025-11-17

