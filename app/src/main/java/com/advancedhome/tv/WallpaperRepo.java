package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Wallpaper foto: NASA APOD (kunci DEMO_KEY), foto acak gratis (picsum.photos), backdrop film TMDB
 * (butuh kunci API Anda di tmdb_key.txt), atau foto sendiri dari folder wallpapers.
 * Satu foto disimpan di cache dan didekode seukuran layar dengan RGB_565 agar hemat memori.
 */
final class WallpaperRepo {

    static final String[] NAMES = {"Bawaan (gradien)", "NASA APOD (4K gratis)", "Foto acak 4K gratis",
            "TMDB (backdrop film)", "Folder lokal", "URL gambar sendiri"};

    interface Callback { void onBitmap(Bitmap b, String info); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean busy;

    private WallpaperRepo() { }

    private static File cacheFile(Context c) { return new File(c.getCacheDir(), "wall.jpg"); }

    /** Foto yang terakhir tersimpan, atau null. */
    static Bitmap cached(Context c, int w, int h) {
        File f = cacheFile(c);
        return f.isFile() ? decode(f, w, h) : null;
    }

    static void fetch(final Context c, final int src, final int w, final int h, final Callback cb) {
        if (busy || src <= 0) return;
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                String info = "";
                try {
                    File dst = cacheFile(c);
                    File tmp = new File(c.getCacheDir(), "wall.tmp");
                    if (src == 4) {
                        File dir = new File(c.getExternalFilesDir(null), "wallpapers");
                        File[] fs = dir.listFiles();
                        List<File> imgs = new ArrayList<>();
                        if (fs != null) for (File f : fs) {
                            String n = f.getName().toLowerCase();
                            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) imgs.add(f);
                        }
                        if (imgs.isEmpty()) { info = "Folder wallpapers kosong"; }
                        else { copy(new FileInputStream(imgs.get(new Random().nextInt(imgs.size()))), tmp); }
                    } else {
                        String url = pickUrl(c, src);
                        if (url == null) info = src == 3 ? "Isi kunci API TMDB di Wallpaper > Kunci API TMDB" : src == 5 ? "Isi alamat gambar di Wallpaper > URL gambar sendiri" : "Sumber tidak tersedia";
                        else download(url, tmp);
                    }
                    if (tmp.isFile() && tmp.length() > 0) {
                        bmp = decode(tmp, w, h);
                        if (bmp != null) {
                            if (dst.exists()) dst.delete();
                            tmp.renameTo(dst);
                        } else {
                            tmp.delete();
                            info = "Foto tidak bisa dibaca";
                        }
                    }
                } catch (Exception e) {
                    info = "Gagal mengambil foto (offline?)";
                }
                final Bitmap fb = bmp;
                final String fi = info;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        cb.onBitmap(fb, fi);
                    }
                });
            }
        }, "wallpaper").start();
    }

    static String tmdbKey(Context c) {
        String saved = Prefs.gets("tmdb_key", "");
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        try {
            File f = new File(c.getExternalFilesDir(null), "tmdb_key.txt");
            if (!f.isFile() || f.length() > 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String k = r.readLine();
            r.close();
            return k == null || k.trim().isEmpty() ? null : k.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String pickUrl(Context c, int src) throws Exception {
        if (src == 5) {
            String url = Prefs.gets("wp_url", "").trim();
            return url.startsWith("https://") || url.startsWith("http://") ? url : null;
        }
        if (src == 2) return "https://picsum.photos/3840/2160?random=" + System.currentTimeMillis();
        if (src == 1) {
            JSONArray a = new JSONArray(getText("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&count=10"));
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!"image".equals(o.optString("media_type"))) continue;
                String u = o.optString("hdurl", o.optString("url", ""));
                if (u.startsWith("https://")) urls.add(u);
            }
            return urls.isEmpty() ? null : urls.get(new Random().nextInt(urls.size()));
        }
        if (src == 3) {
            String key = tmdbKey(c);
            if (key == null) return null;
            JSONObject o = new JSONObject(getText("https://api.themoviedb.org/3/trending/movie/week?api_key=" + key));
            JSONArray a = o.getJSONArray("results");
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                String p = a.getJSONObject(i).optString("backdrop_path", "");
                if (p.startsWith("/")) paths.add(p);
            }
            return paths.isEmpty() ? null : "https://image.tmdb.org/t/p/original" + paths.get(new Random().nextInt(paths.size()));
        }
        return null;
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setConnectTimeout(8000);
            h.setReadTimeout(10000);
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null && sb.length() < 300000) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setConnectTimeout(8000);
            h.setReadTimeout(20000);
            h.setRequestProperty("User-Agent", "AdvancedHomeTV");
            copy(h.getInputStream(), to);
        } finally {
            h.disconnect();
        }
    }

    private static void copy(InputStream in, File to) throws Exception {
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 30L * 1024 * 1024) throw new Exception("terlalu besar");
            }
        } finally {
            out.close();
            in.close();
        }
    }

    private static Bitmap decode(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }
}
