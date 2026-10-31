package com.advancedhome.tv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Pohon menu pengaturan, mengikuti susunan akar yang Anda tentukan. */
final class SettingsMenu {

    interface Host {
        /** Dipanggil setelah ada pengaturan berubah. */
        void changed();
        void toast(String s);
        /** Berkas profil cadangan, atau null kalau penyimpanan tidak tersedia. */
        File profileFile();
        /** Halaman daftar aplikasi yang sedang tidak tampil di Home (untuk ditambahkan kembali). */
        Panel.Page libraryPage();
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

    private static final String SOON = "Segera hadir";

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
                .add(sub("Screensaver", soon("Screensaver", "Jam (digital, analog, flip, nixie)", "Google Photos", "StandBy ala iOS")))
                .add(sub("Sleep Timer Quick Toggle", soon("Sleep Timer", "15 menit", "30 menit", "60 menit", "Custom")))
                .add(sub("One-Click Backup & Restore Profiles", backup()))
                .add(sub("Pustaka Aplikasi", host.libraryPage()))
                .add(new Panel.Row("Ayat Harian").toggle()
                        .value(new Panel.Value() { @Override public String get() { return Prefs.getb("verse", true) ? "Nyala" : "Mati"; } })
                        .step(new Panel.Step() { @Override public void step(int d) { Prefs.putb("verse", !Prefs.getb("verse", true)); host.changed(); } }));
    }

    private Panel.Row sub(String label, final Panel.Page page) {
        return new Panel.Row(label).value(new Panel.Value() { @Override public String get() { return "\u203a"; } })
                .click(new Runnable() { @Override public void run() { panel.push(page); } });
    }

    private Panel.Page soon(String title, String... items) {
        Panel.Page p = new Panel.Page(title);
        for (final String s : items) {
            p.add(new Panel.Row(s).value(new Panel.Value() { @Override public String get() { return SOON; } })
                    .click(new Runnable() { @Override public void run() { host.toast(s + ": " + SOON.toLowerCase()); } }));
        }
        return p;
    }

    private Panel.Page wallpaper() {
        return soon("Wallpaper", "TMDB", "Sumber wallpaper 4K gratis", "Google Photos");
    }

    private Panel.Page clock() {
        return new Panel.Page("Jam & Tanggal")
                .add(new Panel.Row("Format jam")
                        .value(new Panel.Value() { @Override public String get() { return Prefs.getb("h24", true) ? "24 jam" : "12 jam"; } })
                        .step(new Panel.Step() { @Override public void step(int d) { Prefs.putb("h24", !Prefs.getb("h24", true)); host.changed(); } }))
                .add(new Panel.Row("Tampilkan tanggal").toggle()
                        .value(new Panel.Value() { @Override public String get() { return Prefs.getb("showDate", true) ? "Nyala" : "Mati"; } })
                        .step(new Panel.Step() { @Override public void step(int d) { Prefs.putb("showDate", !Prefs.getb("showDate", true)); host.changed(); } }))
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
        Panel.Page p = new Panel.Page("Dark mode");
        p.add(choice("dark", 0, "Off"));
        p.add(choice("dark", 1, "Sunset to sunrise"));
        return p;
    }

    private Panel.Page sound() {
        Panel.Page p = new Panel.Page("System Sound");
        for (int i = 0; i < Sound.NAMES.length; i++) p.add(choice("sound", i, Sound.NAMES[i]));
        p.add(new Panel.Row("Volume efek suara")
                .value(new Panel.Value() { @Override public String get() { return Prefs.geti("vol", 60) + "%"; } })
                .step(new Panel.Step() {
                    @Override public void step(int d) {
                        Prefs.puti("vol", Math.max(0, Math.min(100, Prefs.geti("vol", 60) + d * 10)));
                        Sound.play("ok");
                    }
                }));
        return p;
    }

    /** Baris pilihan: bertanda centang kalau nilainya terpilih. */
    private Panel.Row choice(final String key, final int val, String label) {
        final int def = key.equals("sound") ? 4 : 1;
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

    private Panel.Page backup() {
        final File f = host.profileFile();
        return new Panel.Page("Cadangan dan Pemulihan Profil")
                .add(new Panel.Row("Cadangkan sekarang").value(new Panel.Value() { @Override public String get() { return "Simpan"; } })
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
                .add(new Panel.Row("Pulihkan dari cadangan").value(new Panel.Value() { @Override public String get() { return f != null && f.isFile() ? "Ada" : "Belum ada"; } })
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
