package com.advancedhome.tv;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * Layanan aksesibilitas kosong. Satu-satunya fungsinya: mengunci layar (GLOBAL_ACTION_LOCK_SCREEN)
 * saat Timer tidur habis. Tidak membaca isi layar. Harus diaktifkan di Pengaturan TV > Aksesibilitas.
 * Apakah TV Anda benar-benar mematikan layar saat dikunci, tergantung merek, jadi perlu diuji.
 */
public class SleepService extends AccessibilityService {

    private static SleepService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    static boolean enabled() { return instance != null; }

    static boolean sleepNow() {
        SleepService s = instance;
        return s != null && Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
