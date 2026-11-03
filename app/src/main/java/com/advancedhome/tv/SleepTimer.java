package com.advancedhome.tv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Timer tidur: alarm sistem yang tetap berjalan walau Home tidak sedang tampil. */
final class SleepTimer {
    private static final String KEY = "sleep_end";

    private SleepTimer() { }

    static void start(Context c, int minutes) {
        long end = System.currentTimeMillis() + minutes * 60000L;
        Prefs.putl(KEY, end);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending(c));
    }

    static void cancel(Context c) {
        Prefs.putl(KEY, 0);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c));
    }

    /** Sisa menit (dibulatkan ke atas), 0 kalau tidak ada timer. */
    static int minutesLeft() {
        long e = Prefs.getl(KEY, 0);
        long d = e - System.currentTimeMillis();
        return e <= 0 || d <= 0 ? 0 : (int) ((d + 59999) / 60000);
    }

    /** Dipanggil alarm saat waktunya habis: coba matikan layar, kalau layanan belum aktif tampilkan layar hitam. */
    static void fire(Context c) {
        Prefs.init(c);
        Prefs.putl(KEY, 0);
        if (SleepService.sleepNow()) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("blackout", true));
        } catch (Exception ignored) {
            // Android membatasi peluncuran dari latar belakang; tanpa layanan Sleep tidak ada yang bisa dilakukan
        }
    }

    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, 7, new Intent(c, SleepReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
