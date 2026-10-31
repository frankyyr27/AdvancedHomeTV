package com.advancedhome.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

/** Semua pengaturan pengguna (SharedPreferences). Panggil init() sekali di Application atau Activity. */
final class Prefs {

    private static SharedPreferences sp;

    private Prefs() { }

    static synchronized void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("ahtv", Context.MODE_PRIVATE);
    }

    static int geti(String k, int d) { return sp == null ? d : sp.getInt(k, d); }
    static boolean getb(String k, boolean d) { return sp == null ? d : sp.getBoolean(k, d); }
    static String gets(String k, String d) { return sp == null ? d : sp.getString(k, d); }
    static long getl(String k, long d) { return sp == null ? d : sp.getLong(k, d); }

    static void puti(String k, int v) { if (sp != null) sp.edit().putInt(k, v).apply(); }
    static void putb(String k, boolean v) { if (sp != null) sp.edit().putBoolean(k, v).apply(); }
    static void puts(String k, String v) { if (sp != null) sp.edit().putString(k, v).apply(); }
    static void putl(String k, long v) { if (sp != null) sp.edit().putLong(k, v).apply(); }

    /** Data sementara (cache cuaca) tidak ikut dicadangkan. */
    private static boolean transientKey(String k) { return k.startsWith("wx_"); }

    static String exportJson() {
        try {
            JSONObject o = new JSONObject();
            if (sp != null) {
                for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                    if (!transientKey(e.getKey())) o.put(e.getKey(), e.getValue());
                }
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Menerapkan cadangan. Mengembalikan jumlah pengaturan yang dipulihkan. */
    static int importJson(String json) throws Exception {
        if (sp == null) return 0;
        JSONObject o = new JSONObject(json);
        SharedPreferences.Editor ed = sp.edit();
        int n = 0;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (transientKey(k)) continue;
            Object v = o.get(k);
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof String) ed.putString(k, (String) v);
            else continue;
            n++;
        }
        ed.apply();
        return n;
    }
}
