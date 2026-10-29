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

- [x] Tahap 1: layar Home (jam flip, dock 5 aplikasi, baris aplikasi, tema siang/malam, ayat contoh)
- [ ] Tahap 2: cuaca + perkiraan 5 hari (Open-Meteo), Dynamic Island, dialog Sistem (RAM, memori, fps)
- [ ] Tahap 3: Pengaturan di tengah layar (transparansi kaca, wallpaper, screensaver, suara, cadangan)
- [ ] Tahap 4: ayat dari YouVersion, wallpaper dari galeri/TMDB, bersihkan cache
