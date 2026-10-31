package com.advancedhome.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.TypedValue;
import android.widget.TextView;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/** Monitor kecil: RAM terpakai, penyimpanan tersisa, status Wi-Fi, dan ping. Hanya berjalan saat Home tampil. */
final class SysMonitor extends TextView {
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile int ping = -1;
    private long lastPing;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            update();
            h.postDelayed(this, 2000);
        }
    };

    SysMonitor(Context c, float u) {
        super(c);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * u);
        setSingleLine(true);
        setIncludeFontPadding(false);
    }

    void start() {
        h.removeCallbacks(loop);
        h.post(loop);
    }

    void stop() { h.removeCallbacks(loop); }

    private void update() {
        Context c = getContext();
        String ram = "RAM -";
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram = "RAM " + Math.round(100.0 - 100.0 * mi.availMem / mi.totalMem) + "%";
        } catch (Exception ignored) { }
        String disk = "";
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            disk = String.format(Locale.US, "Sisa %.1f GB", fs.getAvailableBytes() / 1073741824.0).replace('.', ',');
        } catch (Exception ignored) { }
        setText(ram + "  \u00b7  " + disk + "  \u00b7  " + net(c) + "  \u00b7  " + (ping < 0 ? "- ms" : ping + " ms"));
        pingAsync();
    }

    private String net(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return "Offline";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                return "Wi-Fi " + wm.getConnectionInfo().getRssi() + " dBm";
            }
            return "Online";
        } catch (Exception e) {
            return "Jaringan -";
        }
    }

    private void pingAsync() {
        long now = System.currentTimeMillis();
        if (now - lastPing < 5000) return;
        lastPing = now;
        new Thread(new Runnable() {
            @Override public void run() {
                long t0 = System.nanoTime();
                Socket s = new Socket();
                try {
                    s.connect(new InetSocketAddress("1.1.1.1", 443), 1500);
                    ping = (int) ((System.nanoTime() - t0) / 1000000L);
                } catch (Exception e) {
                    ping = -1;
                } finally {
                    try { s.close(); } catch (Exception ignored) { }
                }
            }
        }, "ping").start();
    }
}
