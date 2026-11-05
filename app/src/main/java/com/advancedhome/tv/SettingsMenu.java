package com.advancedhome.tv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Pohon menu pengaturan, mengikuti susunan akar yang Anda tentukan, ditambah Ayat, Widget, Pustaka, dan Launcher Utama. */
final class SettingsMenu {

    interface Host {
        /** Dipanggil setelah ada pengaturan berubah. */
        void changed();
        void toast(String s);
        /** Berkas profil cadangan, atau null kalau penyimpanan tidak tersedia. */
        File profileFile();
        /** Halaman daftar aplikasi yang sedang tidak tampil di Home (untuk ditambahkan kembali). */
        Panel.Page libraryPage();
        /** Sumber wallpaper berubah, atau pengguna minta foto baru. */
        void wallpaperChanged();
        /** type -1: pakai jenis yang dipilih; selain itu paksa jenis tertentu hanya untuk pratinjau. */
        void previewSaver(int type);
        /** Membuka layar pengaturan sistem (action Intent). */
        void openSystem(String action);
        boolean isDefaultHome();
        void makeDefaultHome();
        void startSleep(int minutes);
        void cancelSleep();
        int sleepLeft();
        boolean sleepServiceOn();
        String cameraStatus();
        void readChapter();
        /** Membuka kolom input teks untuk pengaturan {@code key}; hasilnya disimpan oleh Host. */
        void promptText(String title, String hint, String key);
        /** Mematikan suara klik bawaan sistem TV (butuh izin Ubah pengaturan sistem). */
        void disableSystemSounds();
    }

    static final class City {
        final String name;
        final double lat, lon;
        City(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }

    static final City[] CITIES = {
            new City("Gorontalo", 0.5375, 123.0625), new City("Manado", 1.4748, 124.8421),
            new City("Makassar", -5.1477, 119.4327), new City("Jakarta", -6.2088, 106.8456),
            new City("Bandung", -6.9175, 107.6191), new City("Sumedang", -6.8595, 107.9240),
            new City("Yogyakarta", -7.7956, 110.3695), new City("Surabaya", -7.2575, 112.7521),
            new City("Denpasar", -8.6705, 115.2126), new City("Medan", 3.5952, 98.6722)};

    private static final int[] RIGHT_WIDGETS = {0, 1, 2, 4};
    private static final int[] SAVER_MINUTES = {0, 1, 2, 5, 10, 15, 30};

    private final Host host;
    private final Panel panel;

    SettingsMenu(Host host, Panel panel) {
        this.host = host;
        this.panel = panel;
    }

    static City city() {
        return CITIES[Math.max(0, Math.min(CITIES.length - 1, Prefs.geti("loc", 0)))];
    }

    Panel.Page root() {
        return new Panel.Page("Pengaturan")
                .add(sub("Wallpaper", wallpaper()))
                .add(sub("Jam & Tanggal", clock()))
                .add(sub("Dark mode", dark()))
                .add(sub("System Sound", sound()))
                .add(sub("Screensaver", screensaver()))
                .add(sub("Sleep Timer Quick Toggle", sleepPage()))
                .add(sub("One-Click Backup & Restore Profiles", backup()))
                .add(sub("Ayat Harian", versePage()))
                .add(sub("Widget", widgets()))
                .add(sub("Pustaka Aplikasi", host.libraryPage()))
                .add(new Panel.Row("Jadikan Launcher Utama")
                        .value(new Panel.Value() { @Override public String get() { return host.isDefaultHome() ? "Sudah aktif" : "Belum"; } })
                        .click(new Runnable() { @Override public void run() { host.makeDefaultHome(); } }));
    }

    // ---------- helper ----------

    private static Panel.Value text(final String s) {
        return new Panel.Value() { @Override public String get() { return s; } };
    }

    private Panel.Row sub(String label, final Panel.Page page) {
        return new Panel.Row(label).value(text("\u203a"))
                .click(new Runnable() { @Override public void run() { panel.push(page); } });
    }

    private Panel.Row toggle(String label, final String key, final boolean def) {
        return new Panel.Row(label).toggle()
                .value(new Panel.Value() { @Override public String get() { return Prefs.getb(key, def) ? "Nyala" : "Mati"; } })
                .step(new Panel.Step() { @Override public void step(int d) { Prefs.putb(key, !Prefs.getb(key, def)); host.changed(); } });
    }

    /** Baris yang berputar di antara nama pilihan dengan kiri atau kanan. */
    private Panel.Row cycle(String label, final String key, final String[] names, final int def, final boolean wallpaper) {
        return new Panel.Row(label)
                .value(new Panel.Value() {
                    @Override public String get() { return names[Math.max(0, Math.min(names.length - 1, Prefs.geti(key, def)))]; }
                })
                .step(new Panel.Step() {
                    @Override public void step(int d) {
                        Prefs.puti(key, (Prefs.geti(key, def) + d + names.length) % names.length);
                        if (wallpaper) host.wallpaperChanged();
                        host.changed();
                    }
                });
    }

    /** Baris yang membuka kolom input teks. Nilai ditampilkan ringkas; kunci rahasia disamarkan. */
    private Panel.Row input(String label, final String key, final String title, final String hint, final boolean secret) {
        return new Panel.Row(label)
                .value(new Panel.Value() {
                    @Override public String get() {
                        String v = Prefs.gets(key, "").trim();
                        if (v.isEmpty()) return "Belum diisi";
                        if (secret) return "\u2022\u2022\u2022\u2022" + v.substring(Math.max(0, v.length() - 4));
                        return v.length() > 22 ? v.substring(0, 14) + "\u2026" + v.substring(v.length() - 6) : v;
                    }
                })
                .click(new Runnable() { @Override public void run() { host.promptText(title, hint, key); } });
    }

    /** Baris pilihan: bertanda centang kalau nilainya terpilih. */
    private Panel.Row choice(final String key, final int val, String label, final int def) {
        return new Panel.Row(label)
                .value(new Panel.Value() { @Override public String get() { return Prefs.geti(key, def) == val ? "\u2713" : ""; } })
                .click(new Runnable() {
                    @Override public void run() {
                        Prefs.puti(key, val);
                        if (key.equals("sound")) Sound.play("ok");
                        host.changed();
                    }
                });
    }

    // ---------- halaman ----------

    private Panel.Page wallpaper() {
        return new Panel.Page("Wallpaper")
                .add(cycle("Sumber", "wp_src", WallpaperRepo.NAMES, 0, true))
                .add(new Panel.Row("Ganti foto sekarang").value(text("Segarkan"))
                        .click(new Runnable() { @Override public void run() { host.wallpaperChanged(); } }))
                .add(new Panel.Row("Redupkan latar")
                        .value(new Panel.Value() { @Override public String get() { return Prefs.geti("wp_dim", 25) + "%"; } })
                        .step(new Panel.Step() {
                            @Override public void step(int d) {
                                Prefs.puti("wp_dim", Math.max(0, Math.min(70, Prefs.geti("wp_dim", 25) + d * 5)));
                                host.changed();
                            }
                        }))
                .add(input("Kunci API TMDB", "tmdb_key", "Kunci API TMDB",
                        "Salin kunci API dari akun themoviedb.org (Settings > API). Tersimpan hanya di TV ini.", true))
                .add(input("URL gambar sendiri", "wp_url", "URL gambar",
                        "Alamat gambar yang diawali https://. Setelah disimpan, sumbernya otomatis memakai URL ini.", false))
                .add(new Panel.Row("Google Photos").value(text("Belum bisa"))
                        .click(new Runnable() {
                            @Override public void run() {
                                host.toast("Google Photos butuh login Google yang tidak tersedia untuk launcher. Pakai Folder lokal.");
                            }
                        }))
                .add(new Panel.Row("Tentang TMDB").value(text("Info"))
                        .click(new Runnable() {
                            @Override public void run() {
                                host.toast("Produk ini memakai API TMDB tetapi tidak didukung atau disertifikasi oleh TMDB.");
                            }
                        }));
    }

    private Panel.Page clock() {
        return new Panel.Page("Jam & Tanggal")
                .add(new Panel.Row("Format jam")
                        .value(new Panel.Value() { @Override public String get() { return Prefs.getb("h24", true) ? "24 jam" : "12 jam"; } })
                        .step(new Panel.Step() { @Override public void step(int d) { Prefs.putb("h24", !Prefs.getb("h24", true)); host.changed(); } }))
                .add(toggle("Tampilkan tanggal", "showDate", true))
                .add(new Panel.Row("Lokasi cuaca dan matahari")
                        .value(new Panel.Value() { @Override public String get() { return city().name; } })
                        .step(new Panel.Step() {
                            @Override public void step(int d) {
                                Prefs.puti("loc", (Prefs.geti("loc", 0) + d + CITIES.length) % CITIES.length);
                                host.changed();
                            }
                        }));
    }

    private Panel.Page dark() {
        return new Panel.Page("Dark mode")
                .add(choice("dark", 0, "Off", 1))
                .add(choice("dark", 1, "Sunset to sunrise", 1));
    }

    private Panel.Page sound() {
        Panel.Page p = new Panel.Page("System Sound");
        for (int i = 0; i < Sound.NAMES.length; i++) p.add(choice("sound", i, Sound.NAMES[i], 4).silent());
        p.add(new Panel.Row("Matikan suara klik bawaan TV").value(text("\u203a"))
                .click(new Runnable() { @Override public void run() { host.disableSystemSounds(); } }));
        p.add(new Panel.Row("Volume efek suara").silent()
                .value(new Panel.Value() { @Override public String get() { return Prefs.geti("vol", 60) + "%"; } })
                .step(new Panel.Step() {
                    @Override public void step(int d) {
                        Prefs.puti("vol", Math.max(0, Math.min(100, Prefs.geti("vol", 60) + d * 10)));
                        Sound.play("ok");
                    }
                }));
        return p;
    }

    private Panel.Page screensaver() {
        Panel.Page p = new Panel.Page("Screensaver");
        for (int i = 0; i < SaverLayer.TYPES.length; i++) p.add(choice("ss_type", i, SaverLayer.TYPES[i], 0));
        p.add(sub("Pengaturan StandBy ala iOS", standby()));
        p.add(new Panel.Row("Mulai setelah")
                .value(new Panel.Value() {
                    @Override public String get() { int m = Prefs.geti("ss_min", 5); return m == 0 ? "Mati" : m + " menit"; }
                })
                .step(new Panel.Step() {
                    @Override public void step(int d) {
                        int cur = Prefs.geti("ss_min", 5), i = 0;
                        for (int k = 0; k < SAVER_MINUTES.length; k++) if (SAVER_MINUTES[k] == cur) i = k;
                        Prefs.puti("ss_min", SAVER_MINUTES[(i + d + SAVER_MINUTES.length) % SAVER_MINUTES.length]);
                        host.changed();
                    }
                }));
        p.add(new Panel.Row("Pratinjau").value(text("Putar"))
                .click(new Runnable() { @Override public void run() { host.previewSaver(-1); } }));
        p.add(new Panel.Row("Pilih screensaver sistem (Google Photos, dll.)").value(text("\u203a"))
                .click(new Runnable() { @Override public void run() { host.openSystem("android.settings.DREAM_SETTINGS"); } }));
        return p;
    }

    private Panel.Page standby() {
        return new Panel.Page("StandBy ala iOS")
                .add(new Panel.Row("Widget kiri")
                        .value(new Panel.Value() { @Override public String get() { return SaverLayer.WIDGETS[Math.max(0, Math.min(4, Prefs.geti("sb_l", 0)))]; } })
                        .step(new Panel.Step() {
                            @Override public void step(int d) { Prefs.puti("sb_l", (Prefs.geti("sb_l", 0) + d + 5) % 5); host.changed(); }
                        }))
                .add(new Panel.Row("Widget kanan")
                        .value(new Panel.Value() { @Override public String get() { return SaverLayer.WIDGETS[Math.max(0, Math.min(4, Prefs.geti("sb_r", 4)))]; } })
                        .step(new Panel.Step() {
                            @Override public void step(int d) {
                                int cur = Prefs.geti("sb_r", 4), i = 0;
                                for (int k = 0; k < RIGHT_WIDGETS.length; k++) if (RIGHT_WIDGETS[k] == cur) i = k;
                                Prefs.puti("sb_r", RIGHT_WIDGETS[(i + d + RIGHT_WIDGETS.length) % RIGHT_WIDGETS.length]);
                                host.changed();
                            }
                        }))
                .add(cycle("Gaya", "sb_style", SaverLayer.STYLES, 0, false))
                .add(new Panel.Row("Preview standby").value(text("Putar"))
                        .click(new Runnable() {
                            @Override public void run() { host.previewSaver(4); }
                        }));
    }

    /** Dibuka juga dari ikon Timer di Dynamic Island. */
    Panel.Page sleepPage() {
        Panel.Page p = new Panel.Page("Timer Tidur");
        int[] mins = {15, 30, 60};
        for (final int m : mins) {
            p.add(new Panel.Row(m + " menit").value(text("Mulai"))
                    .click(new Runnable() { @Override public void run() { startSleep(m); } }));
        }
        p.add(new Panel.Row("Custom")
                .value(new Panel.Value() { @Override public String get() { return Prefs.geti("sleep_custom", 90) + " menit"; } })
                .step(new Panel.Step() {
                    @Override public void step(int d) {
                        Prefs.puti("sleep_custom", Math.max(5, Math.min(240, Prefs.geti("sleep_custom", 90) + d * 5)));
                    }
                })
                .click(new Runnable() { @Override public void run() { startSleep(Prefs.geti("sleep_custom", 90)); } }));
        p.add(new Panel.Row("Batalkan timer")
                .value(new Panel.Value() { @Override public String get() { int l = host.sleepLeft(); return l > 0 ? "Sisa " + l + " mnt" : "Tidak aktif"; } })
                .click(new Runnable() {
                    @Override public void run() {
                        host.cancelSleep();
                        host.toast("Timer tidur dibatalkan");
                    }
                }));
        p.add(new Panel.Row("Layanan Sleep (Aksesibilitas)")
                .value(new Panel.Value() { @Override public String get() { return host.sleepServiceOn() ? "Aktif" : "Belum aktif"; } })
                .click(new Runnable() { @Override public void run() { host.openSystem("android.settings.ACCESSIBILITY_SETTINGS"); } }));
        return p;
    }

    private void startSleep(int minutes) {
        host.startSleep(minutes);
        panel.close();
        host.toast("TV akan tidur dalam " + minutes + " menit"
                + (host.sleepServiceOn() ? "" : ". Aktifkan Layanan Sleep dulu agar layar benar-benar mati"));
    }

    /** Dibuka dari Pengaturan dan dari ayat di Home. */
    Panel.Page versePage() {
        return new Panel.Page("Ayat Harian")
                .add(toggle("Tampilkan ayat di Home", "verse", true))
                .add(cycle("Bahasa", "vlang", new String[]{"Indonesia", "English"}, 0, false))
                .add(new Panel.Row("Baca satu pasal penuh").value(text("\u203a"))
                        .click(new Runnable() {
                            @Override public void run() {
                                panel.close();
                                host.readChapter();
                            }
                        }));
    }

    private Panel.Page widgets() {
        return new Panel.Page("Widget")
                .add(toggle("Now Playing", "np_on", true))
                .add(new Panel.Row("Beri akses notifikasi (untuk Now Playing)").value(text("\u203a"))
                        .click(new Runnable() { @Override public void run() { host.openSystem("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"); } }))
                .add(toggle("Monitor sistem", "mon_on", true))
                .add(toggle("Kamera CCTV (PIP)", "cam_on", false))
                .add(input("Alamat kamera", "cam_url", "Alamat kamera",
                        "rtsp://... atau tautan berbagi (https://...). Tautan berbagi memberi akses ke kamera Anda, jangan dibagikan.", false))
                .add(new Panel.Row("Jenis alamat")
                        .value(new Panel.Value() { @Override public String get() { return host.cameraStatus(); } }));
    }

    private Panel.Page backup() {
        final File f = host.profileFile();
        return new Panel.Page("Cadangan dan Pemulihan Profil")
                .add(new Panel.Row("Cadangkan sekarang").value(text("Simpan"))
                        .click(new Runnable() {
                            @Override public void run() {
                                try {
                                    if (f == null) throw new Exception("tidak ada penyimpanan");
                                    FileOutputStream o = new FileOutputStream(f);
                                    o.write(Prefs.exportJson().getBytes("UTF-8"));
                                    o.close();
                                    host.toast("Profil tersimpan: " + f.getName());
                                } catch (Exception e) {
                                    host.toast("Gagal menyimpan profil");
                                }
                            }
                        }))
                .add(new Panel.Row("Pulihkan dari cadangan")
                        .value(new Panel.Value() { @Override public String get() { return f != null && f.isFile() ? "Ada" : "Belum ada"; } })
                        .click(new Runnable() {
                            @Override public void run() {
                                try {
                                    if (f == null || !f.isFile() || f.length() > 100000) throw new Exception("tidak ada berkas");
                                    byte[] b = new byte[(int) f.length()];
                                    FileInputStream in = new FileInputStream(f);
                                    int off = 0, n;
                                    while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                                    in.close();
                                    int n2 = Prefs.importJson(new String(b, "UTF-8"));
                                    host.changed();
                                    host.toast("Profil dipulihkan (" + n2 + " pengaturan)");
                                } catch (Exception e) {
                                    host.toast("Tidak ada cadangan yang bisa dipulihkan");
                                }
                            }
                        }));
    }
}
