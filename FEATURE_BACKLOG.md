# VortexDL - Feature Backlog & Future Improvements (FEATURE_BACKLOG.md)

Bu dosya, VortexDL uygulamasına ilerleyen aşamalarda (veya Phase 3 sonrası) eklenebilecek kullanıcı deneyimi, indirme performansı ve medya işleme özelliklerini takip etmek amacıyla oluşturulmuştur.

---

## 1. İndirme & Ağ Yönetimi (Download Engine Enhancements)
- [ ] **Paralel Parçalı İndirme (Multi-threaded Chunked Download):**
  - Tek bir dosyayı (büyük `.mp4`) 4 veya 8 HTTP `Range` parçasına bölerek paralel indirme ve dosya bitiminde birleştirme (indirme hızını maksimuma çıkarma).
- [ ] **İndirme Hız Limiti (Speed Limiter):**
  - Kullanıcının arka planda kota veya bant genişliğini koruması için maksimum indirme hızını (örn. 2 MB/s) sınırlandırma seçeneği.
- [ ] **Yalnızca Wi-Fi ile İndir (Wi-Fi Only Mode):**
  - Mobil veri kullanımını önlemek için Wi-Fi bağlantısı kesildiğinde indirmeleri otomatik duraklatma ve Wi-Fi gelince devam ettirme.
- [ ] **Otomatik Yeniden Deneme (Auto-retry with Exponential Backoff):**
  - Bağlantı kopmalarında veya geçici HTTP 50x/429 hatalarında belirli aralıklarla otomatik yeniden deneme.

---

## 2. Medya & Oynatıcı Özellikleri (Media & Player)
- [ ] **Dahili Medya Oynatıcı (In-App Player - Media3 / ExoPlayer):**
  - İndirilen videoları veya yakalanan akışları harici uygulamaya gerek kalmadan uygulama içinde Picture-in-Picture (PiP), altyazı ve arka planda ses desteği ile oynatma.
- [ ] **Altyazı Yakalama & İndirme (.vtt / .srt):**
  - Sayfadaki video ile ilişkili WebVTT ve SRT altyazı dosyalarını algılayıp videoyla birlikte kaydetme.
- [ ] **Yalnızca Ses İndirme (Audio Extraction / .mp3 / .m4a):**
  - Video akışlarından sadece ses kanalını ayrıştırıp kaydetme.

---

## 3. Tarayıcı Geliştirmeleri (Browser Experience)
- [ ] **Masaüstü / Mobil Görünüm Anahtarı (Desktop Mode Toggle):**
  - Tek dokunuşla User-Agent'ı Chrome Desktop / Mobile arasında değiştirme (mobil sitede video linki gizlenen sayfalar için).
- [ ] **Arama Motoru Seçimi:**
  - Adres çubuğu aramalarında varsayılan motoru belirleme (Google, DuckDuckGo, Bing, Brave Search, Yandex).
- [ ] **Yer İmleri & Geçmiş (Bookmarks & History):**
  - Sık ziyaret edilen video sitelerini kaydetme ve geçmişi yönetme.
- [ ] **Gelişmiş Reklam & Anti-Adblock Filtresi:**
  - EasyList formatında güncellenebilir reklam ve açılır pencere filtre kuralı desteği.

---

## 4. Dosya & Depolama Yönetimi (Storage & Organization)
- [ ] **Scoped Storage / İndirilenler Klasörü Seçimi:**
  - Videoları cihazın genel `Download/VortexDL` veya `Movies/VortexDL` klasörüne kaydederek galeri uygulamalarında doğrudan görünmesini sağlama.
- [ ] **Kategorilendirme & Etiketler:**
  - Videoları siteye, formata (HLS, MP4) veya kullanıcı etiketlerine göre klasörleme.
- [ ] **Paylaşım Menüsü Entegrasyonu:**
  - İndirilen dosyayı doğrudan WhatsApp, Telegram, Google Drive vb. uygulamalara tek tıkla gönderme.
