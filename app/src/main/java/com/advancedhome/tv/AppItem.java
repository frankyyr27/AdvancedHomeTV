package com.advancedhome.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Satu aplikasi yang bisa dibuka dari Home. */
final class AppItem {

    /** Aplikasi yang diutamakan masuk dock saat pertama kali, sesuai urutan ini jika terpasang. */
    private static final List<String> PRIORITY = Arrays.asList(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.spotify.tv.android",
            "com.google.android.apps.photos");

    final String label;
    final String pkg;
    final Intent launch;
    final Drawable art;

    AppItem(String label, String pkg, Intent launch, Drawable art) {
        this.label = label;
        this.pkg = pkg;
        this.launch = launch;
        this.art = art;
    }

    /** Daftar aplikasi TV dan aplikasi biasa yang terpasang, tanpa launcher ini sendiri. */
    static List<AppItem> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppItem> out = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || ai.packageName.equals(c.getPackageName()) || out.containsKey(ai.packageName)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setComponent(new ComponentName(ai.packageName, ai.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.put(ai.packageName, new AppItem(String.valueOf(ri.loadLabel(pm)),
                        ai.packageName, launch, ai.loadIcon(pm)));
            }
        }
        List<AppItem> list = new ArrayList<>(out.values());
        Collections.sort(list, new Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) {
                int pa = PRIORITY.indexOf(a.pkg), pb = PRIORITY.indexOf(b.pkg);
                if (pa >= 0 || pb >= 0) {
                    if (pa < 0) return 1;
                    if (pb < 0) return -1;
                    return pa - pb;
                }
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }
}
