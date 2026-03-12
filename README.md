# KariD Log Manager

Android sistem loglarını kaydeden ve yapay zeka ile analiz eden uygulama.

---

## Özellikler

- **Hafif Kayıt** — Yalnızca hata, uyarı ve kritik logları kaydeder
- **Normal Kayıt** — Tüm uygulama loglarını kaydeder
- **Derin Kayıt** — Logcat, kernel (dmesg) ve SELinux loglarını kaydeder *(root gerektirir)*
- **Yapay Zeka Analizi** — Hafif kayıt dosyalarını Groq AI'ya göndererek sorunları analiz eder
- **Root Desteği** — Root varsa READ_LOGS izni otomatik olarak alınır
- **ADB Desteği** — Root olmayan cihazlar için ADB ile izin verilebilir
- **Türkçe / İngilizce** dil desteği
- **Açık / Koyu** tema desteği

---

## Ekran Görüntüleri

> Yakında eklenecek

---

## Gereksinimler

- Android 10 (API 29) ve üzeri
- READ_LOGS izni (root veya ADB ile)
- Derin kayıt için root yetkisi

---

## Kurulum

### APK ile (Önerilen)

[Releases](https://github.com/KariD-52347/KariD-Log-Manager/releases) sayfasından son sürümü indir ve yükle.

### Kaynak koddan derlemek için

1. Bu repoyu klonla:
```bash
git clone https://github.com/KariD-52347/KariD-Log-Manager.git
```

2. `local.properties` dosyasına kendi [Groq](https://console.groq.com) API keylerini ekle:
```
GROQ_API_KEY_1=gsk_...
GROQ_API_KEY_2=gsk_...
GROQ_API_KEY_3=gsk_...
```

3. Android Studio'da aç ve derle.

---

## READ_LOGS İzni Nasıl Verilir?

### Root ile
Uygulama açıldığında izni otomatik olarak alır. Magisk veya KernelSU gerektirir.

### ADB ile
1. Bilgisayara [ADB](https://developer.android.com/studio/releases/platform-tools) kur
2. Telefonda USB Hata Ayıklama'yı aç
3. Telefonu USB ile bağla
4. Şu komutu çalıştır:
```bash
adb shell pm grant com.karid.logmanager android.permission.READ_LOGS
```

---

## Kullanım

1. İzinleri ver (dosya, bildirim, READ_LOGS)
2. Ana ekranda kayıt türünü seç ve başlat
3. Sorunu birkaç kez tekrarla
4. Kaydı durdur
5. **Hatayı Yapay Zekaya Sor** butonuna bas
6. Kayıt dosyasını seç, sorunu kısaca açıkla ve gönder

---

## Teknolojiler

- Kotlin
- Material Design 3
- Groq API (llama-3.3-70b-versatile)
- OkHttp, Gson
- Android Foreground Service

---

## Lisans

MIT License — dilediğiniz gibi kullanabilirsiniz.
