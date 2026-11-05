# AdvancedHomeTV

Launcher Android TV bergaya kaca (glass) dengan jam flip, dock selebar layar, mode siang
dan malam otomatis, dan kotak ayat harian. Proyek baru, tanpa dependensi pihak ketiga.

## Mendapatkan APK (tanpa Android Studio)

1. Buat repositori baru di GitHub (boleh privat), lalu unggah **seluruh isi folder ini**,
   termasuk folder `.github`.
2. Buka tab **Actions** di repositori. Workflow **Build APK** berjalan otomatis setiap ada push
   (atau jalankan manual lewat **Run workflow**).
3. Setelah hijau, buka run-nya, lalu unduh artefak **AdvancedHomeTV-debug-apk**. Isinya `app-debug.apk`.
4. Pasang ke TV: `adb connect <IP TV>` lalu `adb install -r app-debug.apk`.
5. Buka **Advanced Home TV** dari daftar aplikasi. Untuk menjadikannya layar Home, tekan tombol
   Home dan pilih Advanced Home TV (Selalu), kalau TV Anda menawarkan pilihan itu.

Kalau build gagal, salin log bagian yang merah dari tab Actions dan kirim ke saya.

## Build lokal (opsional)

Buka folder ini di Android Studio (Hedgehog atau lebih baru). Ia akan membuat Gradle wrapper
sendiri. Pilih Build > Build APK.

## Fitur

- Home gaya iOS dan tvOS: jam flip beranimasi, cuaca 7 hari, dock kaca, ikon kotak tumpul yang mengikuti mode terang atau gelap
- Dynamic Island kanan atas: Input (HDMI, AV, TV), Wi-Fi, Timer tidur, Pengaturan
- Pengaturan di tengah layar, terbuka seperti balon. Tombol Back mengembalikan sorotan ke baris sebelumnya
- Dark mode: Off atau Sunset to sunrise (dihitung dari lokasi, tanpa internet)
- System Sound sintetis (PS2, PS3, PS4, PS5, Nintendo, Xbox, SEGA, Retro). Suara klik bawaan sistem dimatikan
- Screensaver: jam digital, analog, flip, nixie, StandBy ala iOS (dua widget). Bisa juga dipilih sebagai screensaver sistem
- Wallpaper foto: NASA APOD, foto acak 4K gratis, TMDB, atau folder sendiri
- Timer tidur: 15, 30, 60 menit, atau custom
- Ayat harian Indonesia atau English, bisa dibaca satu pasal penuh
- Now Playing (kontrol media), monitor RAM dan Wi-Fi, kamera CCTV (PIP, eksperimental)
- Tahan OK pada ikon: Pindah, Hapus dari Home, Info. Pustaka Aplikasi untuk menambahkan kembali

## Menyiapkan fitur tertentu

**Mengisi teks di TV** (kunci API, URL gambar, alamat kamera): di pengaturan, baris bertanda "Belum diisi"
membuka kolom teks. Tekan OK pada kolom untuk memunculkan keyboard TV. Tersedia juga tombol Tempel
(dari clipboard), Hapus, Simpan, dan Batal. Kalau TV Anda tidak punya aplikasi keyboard, isi lewat berkas
(`tmdb_key.txt`, `camera.txt`) seperti di bawah.

**Suara klik bawaan TV masih terdengar?** Buka Pengaturan > System Sound > Matikan suara klik bawaan TV
(minta izin Ubah pengaturan sistem), atau lewat ADB:

    adb shell settings put system sound_effects_enabled 0


**Jadikan launcher utama** (Pengaturan > Jadikan Launcher Utama, atau lewat ADB):

    adb shell cmd package set-home-activity com.advancedhome.tv/.MainActivity

**Now Playing** butuh akses notifikasi:

    adb shell cmd notification allow_listener com.advancedhome.tv/com.advancedhome.tv.MediaListener

**Timer tidur mematikan layar** butuh layanan aksesibilitas Advanced Home TV Sleep. Aktifkan di
Pengaturan TV > Aksesibilitas. Apakah layar benar-benar mati saat dikunci tergantung merek TV.

**Kamera CCTV**: isi Pengaturan > Widget > Alamat kamera (rtsp://... atau tautan berbagi), atau buat `camera.txt` di folder `Android/data/com.advancedhome.tv/files/` berisi satu baris,
misalnya `rtsp://192.168.1.3:554/ALAMAT-DARI-KAMERA`. Lalu nyalakan Pengaturan > Widget > Kamera CCTV.
Uji alamatnya di VLC di komputer dulu (Media > Open Network Stream).

**Wallpaper TMDB**: buat `tmdb_key.txt` berisi kunci API TMDB Anda (gratis).
Produk ini memakai API TMDB tetapi tidak didukung atau disertifikasi oleh TMDB.

**Wallpaper dari foto sendiri**: taruh JPG atau PNG di folder `wallpapers` di folder file aplikasi
lalu pilih sumber Folder lokal.

**Ayat bahasa Indonesia sendiri**: buat `verses.txt` di folder file aplikasi, satu ayat per baris:

    Mazmur 23:1|TUHAN adalah gembalaku, takkan kekurangan aku.

Ayat Indonesia online memakai layanan komunitas yang tidak resmi (data Terjemahan Baru dari sabda.org),
jadi bisa berhenti kapan saja. Pastikan teks yang Anda pakai berlisensi untuk Anda gunakan.

## Tahap pengerjaan

- [x] Tahap 1: layar Home
- [x] Tahap 2: Island, pengaturan, jam flip, cuaca, ikon iOS, pindah/hapus aplikasi
- [x] Tahap 2B: Screensaver, Timer tidur, wallpaper foto, Now Playing, kamera PIP, ayat dan pembaca pasal
- [ ] Berikutnya: sesuai hasil uji di TV
