package com.advancedhome.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Widget Now Playing: judul, artis, dan tombol sebelumnya, putar atau jeda, berikutnya untuk media yang sedang
 * berjalan di aplikasi lain (YouTube, Spotify, dan lain-lain). Butuh akses notifikasi untuk Advanced Home TV.
 */
final class NowPlaying extends LinearLayout {

    private final TextView title, sub;
    private final Btn prev, play, next;
    private final GradientDrawable bg = new GradientDrawable();
    private MediaController ctl;

    NowPlaying(Context c, float u) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        bg.setCornerRadius(30 * u);
        setBackground(bg);
        int pad = Math.round(16 * u);
        setPadding(pad, Math.round(10 * u), pad, Math.round(10 * u));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = new TextView(c);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19 * u);
        title.setSingleLine(true);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sub = new TextView(c);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * u);
        sub.setSingleLine(true);
        col.addView(title);
        col.addView(sub);
        addView(col, new LinearLayout.LayoutParams(Math.round(190 * u), ViewGroup.LayoutParams.WRAP_CONTENT));

        prev = new Btn(c, u, 0);
        play = new Btn(c, u, 1);
        next = new Btn(c, u, 2);
        int s = Math.round(46 * u);
        addView(prev, new LinearLayout.LayoutParams(s, s));
        addView(play, new LinearLayout.LayoutParams(s, s));
        addView(next, new LinearLayout.LayoutParams(s, s));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (ctl != null) ctl.getTransportControls().skipToPrevious(); }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ctl == null) return;
                PlaybackState st = ctl.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) ctl.getTransportControls().pause();
                else ctl.getTransportControls().play();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (ctl != null) ctl.getTransportControls().skipToNext(); }
        });
        setVisibility(GONE);
    }

    void setLook(boolean day, int tx, int tx2) {
        bg.setColor(day ? 0x66FFFFFF : 0x33FFFFFF);
        title.setTextColor(tx);
        sub.setTextColor(tx2);
    }

    /** Memeriksa sesi media aktif. Tanpa akses notifikasi widget disembunyikan. */
    void poll(Context c) {
        MediaController pick = null;
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(new ComponentName(c, MediaListener.class));
            for (MediaController mc : l) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { pick = mc; break; }
            }
            if (pick == null && !l.isEmpty()) pick = l.get(0);
        } catch (Exception ignored) {
            // belum diberi akses notifikasi, atau layanan tidak tersedia
        }
        ctl = pick;
        if (pick == null) {
            setVisibility(GONE);
            return;
        }
        MediaMetadata md = pick.getMetadata();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String app = pick.getPackageName();
        try {
            PackageManager pm = c.getPackageManager();
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pick.getPackageName(), 0)));
        } catch (Exception ignored) { }
        title.setText(t == null || t.isEmpty() ? app : t);
        sub.setText(a == null || a.isEmpty() ? app : a + "  \u00b7  " + app);
        PlaybackState st = pick.getPlaybackState();
        play.setPlaying(st != null && st.getState() == PlaybackState.STATE_PLAYING);
        setVisibility(VISIBLE);
    }

    /** Tombol bulat: 0 sebelumnya, 1 putar atau jeda, 2 berikutnya. */
    private static final class Btn extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rf = new RectF();
        private final int kind;
        private boolean playing;

        Btn(Context c, float u, int kind) {
            super(c);
            this.kind = kind;
            setFocusable(true);
            setClickable(true);
        }

        void setPlaying(boolean v) {
            if (v != playing) {
                playing = v;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, s = w * 0.2f;
            if (isFocused()) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, w * 0.46f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(isFocused() ? 0xFF111111 : 0xFFFFFFFF);
            if (kind == 1 && playing) {
                rf.set(cx - s * 0.9f, cy - s, cx - s * 0.2f, cy + s);
                c.drawRoundRect(rf, 2, 2, p);
                rf.set(cx + s * 0.2f, cy - s, cx + s * 0.9f, cy + s);
                c.drawRoundRect(rf, 2, 2, p);
                return;
            }
            path.reset();
            if (kind == 1) {
                path.moveTo(cx - s * 0.7f, cy - s);
                path.lineTo(cx + s, cy);
                path.lineTo(cx - s * 0.7f, cy + s);
            } else if (kind == 2) {
                path.moveTo(cx - s, cy - s * 0.8f);
                path.lineTo(cx, cy);
                path.lineTo(cx - s, cy + s * 0.8f);
                path.moveTo(cx, cy - s * 0.8f);
                path.lineTo(cx + s, cy);
                path.lineTo(cx, cy + s * 0.8f);
            } else {
                path.moveTo(cx + s, cy - s * 0.8f);
                path.lineTo(cx, cy);
                path.lineTo(cx + s, cy + s * 0.8f);
                path.moveTo(cx, cy - s * 0.8f);
                path.lineTo(cx - s, cy);
                path.lineTo(cx, cy + s * 0.8f);
            }
            path.close();
            c.drawPath(path, p);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect r) {
            super.onFocusChanged(gained, direction, r);
            invalidate();
        }
    }
}
