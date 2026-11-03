package com.advancedhome.tv;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Teks Alkitab per pasal lewat jaringan.
 * Indonesia: API komunitas Beeble (data dari sabda.org, Terjemahan Baru). Tidak resmi, bisa saja berhenti.
 * English: bible-api.com (World English Bible, domain publik).
 * Hasil terakhir disimpan di memori agar membaca atau pindah pasal tidak mengulang permintaan.
 */
final class BibleApi {

    static final int LANG_ID = 0, LANG_EN = 1;

    /** Hasil: daftar {nomor ayat, teks}, atau null kalau gagal. */
    interface Callback { void onResult(List<String[]> verses); }

    static final class Ref {
        final String idn, en, abbr;
        final int chapter, verse;
        Ref(String idn, String en, String abbr, int chapter, int verse) {
            this.idn = idn; this.en = en; this.abbr = abbr; this.chapter = chapter; this.verse = verse;
        }
    }

    // nama Indonesia, nama Inggris untuk bible-api.com, singkatan TB
    private static final String[][] BOOKS = {
            {"Mazmur", "psalms", "Mzm"}, {"Filipi", "philippians", "Flp"}, {"Amsal", "proverbs", "Ams"},
            {"Matius", "matthew", "Mat"}, {"Yesaya", "isaiah", "Yes"}, {"Yosua", "joshua", "Yos"},
            {"Yohanes", "john", "Yoh"}, {"Roma", "romans", "Rm"}};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String cacheKey;
    private static List<String[]> cacheVal;

    private BibleApi() { }

    static String langLabel(int lang) { return lang == LANG_ID ? "TB (online)" : "WEB (online)"; }

    /** "Mazmur 23:1" menjadi Ref, atau null kalau kitabnya tidak dikenal. */
    static Ref parse(String ref) {
        try {
            int sp = ref.lastIndexOf(' ');
            if (sp <= 0) return null;
            String name = ref.substring(0, sp).trim();
            String cv = ref.substring(sp + 1).trim();
            int colon = cv.indexOf(':');
            int ch = Integer.parseInt(colon < 0 ? cv : cv.substring(0, colon));
            int v = colon < 0 ? 1 : Integer.parseInt(cv.substring(colon + 1).split("[-,]")[0]);
            for (String[] b : BOOKS) {
                if (b[0].equalsIgnoreCase(name)) return new Ref(b[0], b[1], b[2], ch, v);
            }
        } catch (Exception ignored) { }
        return null;
    }

    static void chapter(final Ref r, final int chapter, final int lang, final Callback cb) {
        final String key = lang + "|" + r.abbr + "|" + chapter;
        if (key.equals(cacheKey) && cacheVal != null) {
            cb.onResult(cacheVal);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                List<String[]> out = null;
                HttpURLConnection h = null;
                try {
                    String url = lang == LANG_ID
                            ? "https://beeble.vercel.app/api/v1/passage/" + r.abbr + "/" + chapter + "?ver=tb"
                            : "https://bible-api.com/" + r.en + "+" + chapter + "?translation=web";
                    h = (HttpURLConnection) new URL(url).openConnection();
                    h.setConnectTimeout(7000);
                    h.setReadTimeout(9000);
                    h.setRequestProperty("User-Agent", "AdvancedHomeTV");
                    BufferedReader br = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null && sb.length() < 400000) sb.append(line);
                    br.close();
                    out = parseVerses(sb.toString());
                } catch (Exception ignored) {
                    // offline atau layanan berhenti
                } finally {
                    if (h != null) h.disconnect();
                }
                final List<String[]> res = out;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (res != null) {
                            cacheKey = key;
                            cacheVal = res;
                        }
                        cb.onResult(res);
                    }
                });
            }
        }, "bible").start();
    }

    /** Membaca daftar ayat dengan toleran terhadap perbedaan nama kunci antar layanan. */
    private static List<String[]> parseVerses(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        JSONArray arr = o.optJSONArray("verses");
        if (arr == null) {
            JSONObject d = o.optJSONObject("data");
            if (d != null) arr = d.optJSONArray("verses");
        }
        if (arr == null) throw new Exception("format tidak dikenal");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            int n = v.optInt("verse", v.optInt("verse_number", v.optInt("number", 0)));
            String t = v.optString("content", v.optString("text", "")).trim().replace("\n", " ");
            if (!t.isEmpty()) out.add(new String[]{n > 0 ? String.valueOf(n) : "", t});
        }
        if (out.isEmpty()) throw new Exception("kosong");
        return out;
    }

    /** Teks satu ayat dari hasil pasal, atau null. */
    static String verseText(List<String[]> verses, int verse) {
        for (String[] v : verses) if (v[0].equals(String.valueOf(verse))) return v[1];
        return null;
    }
}
