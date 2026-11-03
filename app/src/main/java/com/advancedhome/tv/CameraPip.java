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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Jendela kecil kamera CCTV (picture in picture) di Home. Alamat RTSP dibaca dari camera.txt di folder file aplikasi.
 * Pemutaran RTSP memakai pemutar bawaan Android, yang tidak semua TV dan kamera mendukungnya (eksperimental).
 */
final class CameraPip extends FrameLayout {

    private final VideoView video;
    private final TextView msg;

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

    /** Baris pertama camera.txt (alamat rtsp://...), atau null kalau berkas belum ada. */
    static String readUrl(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, "camera.txt");
            if (f == null || !f.isFile() || f.length() > 2000) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("rtsp://") || line.startsWith("http")) {
                    r.close();
                    return line;
                }
            }
            r.close();
        } catch (Exception ignored) { }
        return null;
    }

    void start(String url) {
        try {
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
                    msg.setText("Kamera tidak bisa dibuka.\nCek alamat di camera.txt");
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
    }
}
