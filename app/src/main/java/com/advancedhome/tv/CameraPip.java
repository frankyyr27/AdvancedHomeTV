package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Jendela kecil kamera CCTV (picture in picture) di Home. Alamat diisi di Pengaturan > Widget > Alamat kamera
 * (atau camera.txt). rtsp:// diputar dengan pemutar bawaan Android; tautan http(s) (mis. tautan berbagi) dibuka di WebView.
 * Keduanya eksperimental: tidak semua TV, kamera, atau halaman berbagi bisa diputar, dan WebView memakai memori cukup besar.
 */
final class CameraPip extends FrameLayout {

    private final VideoView video;
    private final TextView msg;
    private WebView web;

    CameraPip(Context c, final float u) {
        super(c);
        setFocusable(true);
        setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(24 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x55FFFFFF);
        setBackground(bg);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 24 * u);
            }
        });
        video = new VideoView(c);
        addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        msg = new TextView(c);
        msg.setTextColor(0xFFFFFFFF);
        msg.setGravity(Gravity.CENTER);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * u);
        msg.setText("Memuat kamera\u2026");
        addView(msg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
    }

    /** Alamat dari pengaturan, atau baris pertama camera.txt. Null kalau belum diisi. */
    static String readUrl(Context c) {
        String saved = Prefs.gets("cam_url", "");
        if (saved != null && isAddress(saved.trim())) return saved.trim();
        try {
            File dir = c.getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, "camera.txt");
            if (f == null || !f.isFile() || f.length() > 4000) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (isAddress(line)) {
                    r.close();
                    return line;
                }
            }
            r.close();
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean isAddress(String s) {
        return s.startsWith("rtsp://") || s.startsWith("http://") || s.startsWith("https://");
    }

    /** "RTSP", "Tautan web", atau "Belum diisi", untuk ditampilkan di pengaturan. */
    static String kind(String url) {
        return url == null ? "Belum diisi" : url.startsWith("rtsp://") ? "RTSP" : "Tautan web";
    }

    void start(String url) {
        stop();
        if (url.startsWith("http")) startWeb(url);
        else startVideo(url);
    }

    private void startWeb(String url) {
        try {
            video.setVisibility(INVISIBLE);
            msg.setVisibility(VISIBLE);
            msg.setText("Memuat tautan kamera\u2026");
            web = new WebView(getContext());
            web.setFocusable(false);
            web.setClickable(false);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String u) { msg.setVisibility(GONE); }
            });
            addView(web, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.loadUrl(url);
        } catch (Throwable t) {
            msg.setVisibility(VISIBLE);
            msg.setText("WebView tidak tersedia di TV ini");
        }
    }

    private void startVideo(String url) {
        try {
            video.setVisibility(VISIBLE);
            msg.setVisibility(VISIBLE);
            msg.setText("Memuat kamera\u2026");
            video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    mp.setVolume(0f, 0f);
                    msg.setVisibility(GONE);
                    video.start();
                }
            });
            video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    msg.setVisibility(VISIBLE);
                    msg.setText("Kamera tidak bisa dibuka.\nPeriksa alamat kamera");
                    return true;
                }
            });
            video.setVideoURI(Uri.parse(url));
        } catch (Exception e) {
            msg.setText("Alamat kamera tidak valid");
        }
    }

    void stop() {
        try { video.stopPlayback(); } catch (Exception ignored) { }
        if (web != null) {
            try {
                web.loadUrl("about:blank");
                removeView(web);
                web.destroy();
            } catch (Exception ignored) { }
            web = null;
        }
    }
}
