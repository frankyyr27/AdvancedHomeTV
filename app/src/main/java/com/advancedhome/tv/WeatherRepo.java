package com.advancedhome.tv;

import android.content.Context;
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
import java.util.Locale;

/** Cuaca saat ini dan 7 hari ke depan dari Open-Meteo (gratis, tanpa API key). Disimpan agar tetap tampil saat offline. */
final class WeatherRepo {

    static final class Day { int code; double max, min; }
    static final class Data { double temp; int code; final List<Day> days = new ArrayList<>(); }
    interface Callback { void onData(Data d); }

    private static final long MAX_AGE = 30 * 60 * 1000L;
    private static final long STALE = 6 * 60 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean busy;

    /** Data tersimpan untuk lokasi ini, atau null kalau tidak ada atau sudah terlalu lama. */
    static Data cached(double lat, double lon) {
        try {
            if (!key(lat, lon).equals(Prefs.gets("wx_key", ""))) return null;
            if (System.currentTimeMillis() - Prefs.getl("wx_at", 0) > STALE) return null;
            return parse(Prefs.gets("wx_json", ""));
        } catch (Exception e) {
            return null;
        }
    }

    static void refresh(Context c, final double lat, final double lon, final Callback cb) {
        final String key = key(lat, lon);
        if (busy) return;
        if (key.equals(Prefs.gets("wx_key", "")) && System.currentTimeMillis() - Prefs.getl("wx_at", 0) < MAX_AGE) return;
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                Data d = null;
                String json = null;
                HttpURLConnection h = null;
                try {
                    String url = String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                    + "&current=temperature_2m,weather_code"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                    + "&timezone=auto&forecast_days=7", lat, lon);
                    h = (HttpURLConnection) new URL(url).openConnection();
                    h.setConnectTimeout(6000);
                    h.setReadTimeout(6000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String s;
                    while ((s = r.readLine()) != null) sb.append(s);
                    r.close();
                    json = sb.toString();
                    d = parse(json);
                } catch (Exception ignored) {
                } finally {
                    if (h != null) h.disconnect();
                }
                final Data res = d;
                final String raw = json;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (res == null) return;
                        Prefs.puts("wx_json", raw);
                        Prefs.puts("wx_key", key);
                        Prefs.putl("wx_at", System.currentTimeMillis());
                        cb.onData(res);
                    }
                });
            }
        }, "weather").start();
    }

    private static String key(double lat, double lon) {
        return String.format(Locale.US, "%.4f,%.4f", lat, lon);
    }

    static Data parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Data d = new Data();
        JSONObject cur = o.getJSONObject("current");
        d.temp = cur.getDouble("temperature_2m");
        d.code = cur.getInt("weather_code");
        JSONObject dl = o.getJSONObject("daily");
        JSONArray codes = dl.getJSONArray("weather_code");
        JSONArray max = dl.getJSONArray("temperature_2m_max");
        JSONArray min = dl.getJSONArray("temperature_2m_min");
        for (int i = 0; i < codes.length(); i++) {
            Day day = new Day();
            day.code = codes.getInt(i);
            day.max = max.getDouble(i);
            day.min = min.getDouble(i);
            d.days.add(day);
        }
        return d;
    }

    /** Kelompok kondisi: 0 cerah, 1 berawan sebagian, 2 berawan, 3 kabut, 4 hujan, 5 salju, 6 badai petir. */
    static int kind(int c) {
        if (c == 0) return 0;
        if (c <= 2) return 1;
        if (c == 3) return 2;
        if (c == 45 || c == 48) return 3;
        if (c >= 95) return 6;
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return 5;
        return 4;
    }

    static final String[] NAMES = {"Cerah", "Cerah berawan", "Berawan", "Berkabut", "Hujan", "Salju", "Badai petir"};
}
