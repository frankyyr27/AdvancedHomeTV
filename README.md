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

## Tahap pengerjaan

- [x] Tahap 1: layar Home (jam, dock, baris aplikasi, tema siang/malam)
- [x] Tahap 2 (sebagian):
  - Dynamic Island kanan atas: Input (HDMI, AV, TV), Wi-Fi, Pengaturan
  - Pengaturan kaca di tengah layar, terbuka seperti balon dari tombolnya
    (Dark mode, Jam & Tanggal, System Sound, Backup/Restore, Pustaka Aplikasi, Ayat Harian)
  - Jam flip beranimasi, cuaca hari ini + 7 hari (Open-Meteo), monitor RAM/penyimpanan/Wi-Fi/ping
  - Ikon aplikasi gaya iOS, membesar saat fokus tanpa terpotong
  - Tahan OK pada ikon: Pindah, Hapus dari Home, Info aplikasi
  - Ayat harian offline (KJV, domain publik) atau ayat Anda sendiri lewat `verses.txt`
- [ ] Tahap 2B: Screensaver (jam digital/analog/flip/nixie, StandBy ala iOS, Google Photos),
      Sleep Timer, sumber wallpaper (TMDB, 4K gratis, Google Photos), widget Now Playing
- [ ] Tahap 3: Live Camera PIP (butuh alamat RTSP kamera yang sudah dites)

## Ayat dalam bahasa Indonesia

Buat berkas `verses.txt` di folder `Android/data/com.advancedhome.tv/files/`, satu ayat per baris:

    Mazmur 23:1|TUHAN adalah gembalaku, takkan kekurangan aku.

Selama berkas itu ada, ayat harian diambil dari sana. Pastikan teksnya berlisensi untuk Anda pakai.
