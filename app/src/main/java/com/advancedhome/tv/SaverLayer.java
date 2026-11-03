package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Calendar;
import java.util.Locale;

/**
 * Tampilan screensaver: jam digital, analog, flip, nixie, dan StandBy ala iOS dengan dua widget.
 * Dipakai oleh Home (saat tidak ada aktivitas) dan oleh ClockDream (screensaver sistem).
 * Digambar dengan canvas tanpa bitmap, diperbarui sekali per detik, dan bergeser pelan
 * supaya layar tidak terbakar (burn-in).
 */
final class SaverLayer extends FrameLayout {

    static final String[] TYPES = {"Jam digital", "Jam analog", "Jam flip", "Nixie tube clock", "StandBy ala iOS", "Google Photos (bawaan TV)"};
    static final String[] WIDGETS = {"Analog clock", "Modern clock", "Date", "Calendar", "Cuaca"};
    static final String[] STYLES = {"Analog", "Float", "Digital"};

    private static final String[] DAYS = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
    private static final String[] MONTHS = {"Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
            "Agustus", "September", "Oktober", "November", "Desember"};

    private final float u;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final FlipTile flipH, flipM;
    private final Face face;
    private int type;
    private WeatherRepo.Data wx;
    private long wxAt;

    SaverLayer(Context c, float u) {
        super(c);
        this.u = u;
        setBackgroundColor(0xFF000000);
        face = new Face(c);
        addView(face, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        flipH = new FlipTile(c, u);
        flipM = new FlipTile(c, u);
        for (int i = 0; i < 2; i++) {
            FlipTile t = i == 0 ? flipH : flipM;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(136 * u), Math.round(80 * u), Gravity.CENTER);
            t.setLayoutParams(lp);
            t.setScaleX(3f);
            t.setScaleY(3f);
            t.setTranslationX((i == 0 ? -1 : 1) * (136 * 1.5f + 14) * u);
            t.setTranslationY(-30 * u);
            t.setVisibility(View.GONE);
            addView(t);
        }
    }

    void start() { start(-1); }

    /** override >= 0 memaksa jenis tertentu (pratinjau) tanpa mengubah pilihan pengguna. */
    void start(int override) {
        type = override >= 0 ? override : Prefs.geti("ss_type", 0);
        if (type >= 5) type = 0;   // "Google Photos" adalah pilihan sistem, bukan gambar buatan kita
        boolean flip = type == 2;
        flipH.setVisibility(flip ? View.VISIBLE : View.GONE);
        flipM.setVisibility(flip ? View.VISIBLE : View.GONE);
        flipH.setValue("00", false);
        flipM.setValue("00", false);
        h.removeCallbacks(loop);
        h.post(loop);
    }

    void stop() { h.removeCallbacks(loop); }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            Calendar c = Calendar.getInstance();
            if (type == 2) {
                String[] t = hm(c);
                flipH.setValue(t[0], true);
                flipM.setValue(t[1], true);
            }
            if (System.currentTimeMillis() - wxAt > 60000) {
                SettingsMenu.City ct = SettingsMenu.city();
                wx = WeatherRepo.cached(ct.lat, ct.lon);
                wxAt = System.currentTimeMillis();
            }
            face.invalidate();
            h.postDelayed(this, 1020 - System.currentTimeMillis() % 1000);
        }
    };

    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    private static String[] hm(Calendar c) {
        int hr = c.get(Calendar.HOUR_OF_DAY);
        if (!Prefs.getb("h24", true)) {
            hr = hr % 12;
            if (hr == 0) hr = 12;
        }
        return new String[]{two(hr), two(c.get(Calendar.MINUTE))};
    }

    /** Kanvas tempat semua gaya digambar. */
    private final class Face extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();

        Face(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), hgt = getHeight();
            Calendar now = Calendar.getInstance();
            long minute = System.currentTimeMillis() / 60000L;
            // geser pelan agar tidak ada piksel yang menyala di tempat yang sama terlalu lama
            float dx = (float) Math.sin(minute * 0.35) * 28 * u, dy = (float) Math.cos(minute * 0.23) * 18 * u;
            cv.save();
            cv.translate(dx, dy);
            switch (type) {
                case 0: digital(cv, now, w, hgt); break;
                case 1: analog(cv, now, w / 2f, hgt / 2f - 10 * u, Math.min(w, hgt) * 0.36f, 0xFFFF9F0A, true); date(cv, now, w / 2f, hgt / 2f + Math.min(w, hgt) * 0.36f + 70 * u, 30 * u, 0xB3FFFFFF); break;
                case 2: date(cv, now, w / 2f, hgt / 2f + 200 * u, 32 * u, 0xB3FFFFFF); break;
                case 3: nixie(cv, now, w, hgt); break;
                default: standby(cv, now, w, hgt); break;
            }
            cv.restore();
        }

        private void text(Canvas c, String s, float cx, float cy, float size, int color, Typeface tf) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            p.setTypeface(tf);
            c.drawText(s, cx, cy - (p.ascent() + p.descent()) / 2f, p);
        }

        private void digital(Canvas c, Calendar now, int w, int h2) {
            String[] t = hm(now);
            text(c, t[0] + ":" + t[1], w / 2f, h2 / 2f - 20 * u, 230 * u, 0xFFFFFFFF, Typeface.create("sans-serif-thin", Typeface.NORMAL));
            date(c, now, w / 2f, h2 / 2f + 150 * u, 34 * u, 0xB3FFFFFF);
        }

        private void date(Canvas c, Calendar now, float cx, float cy, float size, int color) {
            String s = DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + now.get(Calendar.DAY_OF_MONTH) + " "
                    + MONTHS[now.get(Calendar.MONTH)];
            text(c, s, cx, cy, size, color, Typeface.DEFAULT);
        }

        private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int color) {
            double a = Math.toRadians(deg);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawLine(cx - (float) Math.sin(a) * len * 0.12f, cy + (float) Math.cos(a) * len * 0.12f,
                    cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
        }

        /** Jam analog: tanda jam, jarum jam dan menit, jarum detik berwarna aksen. */
        private void analog(Canvas c, Calendar now, float cx, float cy, float r, int accent, boolean ring) {
            if (ring) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * u);
                p.setColor(0x55FFFFFF);
                c.drawCircle(cx, cy, r, p);
            }
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 60; i++) {
                boolean big = i % 5 == 0;
                double a = Math.toRadians(i * 6);
                float r1 = r * (big ? 0.86f : 0.92f), r2 = r * 0.97f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((big ? 4 : 1.6f) * u * (r / (200 * u)));
                p.setColor(big ? 0xFFFFFFFF : 0x77FFFFFF);
                c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                        cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, p);
            }
            int hr = now.get(Calendar.HOUR), mi = now.get(Calendar.MINUTE), se = now.get(Calendar.SECOND);
            float k = r / (200 * u);
            hand(c, cx, cy, (hr + mi / 60f) * 30f, r * 0.5f, 9 * u * k, 0xFFFFFFFF);
            hand(c, cx, cy, (mi + se / 60f) * 6f, r * 0.78f, 6 * u * k, 0xFFFFFFFF);
            hand(c, cx, cy, se * 6f, r * 0.86f, 2.6f * u * k, accent);
            p.setStyle(Paint.Style.FILL);
            p.setColor(accent);
            c.drawCircle(cx, cy, 7 * u * k, p);
        }

        /** Tabung nixie: kaca gelap dengan angka oranye berpendar. */
        private void nixie(Canvas c, Calendar now, int w, int h2) {
            c.drawColor(0xFF0B0705);
            String[] t = hm(now);
            String d = t[0] + t[1];
            float tw = 128 * u, th = 250 * u, gap = 20 * u, colon = 70 * u;
            float total = 4 * tw + 2 * gap + colon;
            float x = (w - total) / 2f, top = h2 / 2f - th / 2f - 10 * u;
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    float mx = x + colon / 2f;
                    p.setStyle(Paint.Style.FILL);
                    for (int k = 0; k < 2; k++) {
                        float my = top + th * (k == 0 ? 0.36f : 0.64f);
                        p.setColor(0x33FF6A00);
                        c.drawCircle(mx, my, 16 * u, p);
                        p.setColor(0xFFFF9A3C);
                        c.drawCircle(mx, my, 7 * u, p);
                    }
                    x += colon;
                }
                rf.set(x, top, x + tw, top + th);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF1B100A);
                c.drawRoundRect(rf, 44 * u, 44 * u, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * u);
                p.setColor(0xFF5A3A28);
                c.drawRoundRect(rf, 44 * u, 44 * u, p);
                glowDigit(c, d.substring(i, i + 1), x + tw / 2f, top + th / 2f, 190 * u);
                x += tw + (i == 1 ? 0 : gap);
            }
            date(c, now, w / 2f, top + th + 80 * u, 30 * u, 0x99FF9A3C);
        }

        private void glowDigit(Canvas c, String s, float cx, float cy, float size) {
            Typeface tf = Typeface.create("sans-serif-light", Typeface.NORMAL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            p.setTypeface(tf);
            float y = cy - (p.ascent() + p.descent()) / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(16 * u);
            p.setColor(0x33FF6A00);
            c.drawText(s, cx, y, p);
            p.setStrokeWidth(8 * u);
            p.setColor(0x66FF8A2B);
            c.drawText(s, cx, y, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFB066);
            c.drawText(s, cx, y, p);
        }

        /** StandBy: dua widget berdampingan. Gaya: Analog (gelap, aksen merah), Float (kaca melayang), Digital (aksen oranye). */
        private void standby(Canvas c, Calendar now, int w, int h2) {
            int style = Prefs.geti("sb_style", 0);
            int[] accents = {0xFFFF3B30, 0xFF64D2FF, 0xFFFF9F0A};
            int accent = accents[Math.max(0, Math.min(2, style))];
            if (style == 1) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, w, h2, 0xFF1A1245, 0xFF0F5E5A, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h2, p);
                p.setShader(null);
            }
            float s = Math.min(h2 * 0.64f, w * 0.34f), gap = 36 * u;
            double t = System.currentTimeMillis() / 1600.0;
            float bobL = style == 1 ? (float) Math.sin(t) * 10 * u : 0, bobR = style == 1 ? (float) Math.cos(t) * 10 * u : 0;
            float top = (h2 - s) / 2f;
            card(c, w / 2f - gap / 2f - s, top + bobL, s, style);
            card(c, w / 2f + gap / 2f, top + bobR, s, style);
            widget(c, Prefs.geti("sb_l", 0), now, w / 2f - gap / 2f - s, top + bobL, s, accent);
            widget(c, Prefs.geti("sb_r", 4), now, w / 2f + gap / 2f, top + bobR, s, accent);
        }

        private void card(Canvas c, float x, float y, float s, int style) {
            rf.set(x, y, x + s, y + s);
            p.setStyle(Paint.Style.FILL);
            p.setColor(style == 1 ? 0x30FFFFFF : style == 2 ? 0xFF0A0A0A : 0xFF1C1C1E);
            c.drawRoundRect(rf, 48 * u, 48 * u, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, u));
            p.setColor(style == 1 ? 0x66FFFFFF : 0x33FFFFFF);
            c.drawRoundRect(rf, 48 * u, 48 * u, p);
        }

        private void widget(Canvas c, int id, Calendar now, float x, float y, float s, int accent) {
            float cx = x + s / 2f, cy = y + s / 2f;
            String[] t = hm(now);
            switch (id) {
                case 0:
                    analog(c, now, cx, cy, s * 0.38f, accent, false);
                    break;
                case 1:
                    text(c, t[0], cx, cy - s * 0.20f, s * 0.42f, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
                    text(c, t[1], cx, cy + s * 0.20f, s * 0.42f, accent, Typeface.create("sans-serif-light", Typeface.NORMAL));
                    break;
                case 2:
                    text(c, DAYS[now.get(Calendar.DAY_OF_WEEK) - 1].toUpperCase(Locale.ROOT), cx, y + s * 0.16f, s * 0.085f, accent, Typeface.DEFAULT_BOLD);
                    text(c, String.valueOf(now.get(Calendar.DAY_OF_MONTH)), cx, cy + s * 0.02f, s * 0.52f, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
                    text(c, MONTHS[now.get(Calendar.MONTH)], cx, y + s * 0.86f, s * 0.085f, 0xB3FFFFFF, Typeface.DEFAULT);
                    break;
                case 3:
                    calendar(c, now, x, y, s, accent);
                    break;
                default:
                    weather(c, cx, y, s, accent);
                    break;
            }
        }

        private void calendar(Canvas c, Calendar now, float x, float y, float s, int accent) {
            text(c, MONTHS[now.get(Calendar.MONTH)].toUpperCase(Locale.ROOT) + " " + now.get(Calendar.YEAR), x + s / 2f, y + s * 0.11f, s * 0.07f, accent, Typeface.DEFAULT_BOLD);
            String[] hdr = {"S", "S", "R", "K", "J", "S", "M"};
            float cw = s * 0.82f / 7f, x0 = x + s * 0.09f, y0 = y + s * 0.24f;
            for (int i = 0; i < 7; i++) text(c, hdr[i], x0 + cw * (i + 0.5f), y0, s * 0.06f, 0x99FFFFFF, Typeface.DEFAULT);
            Calendar first = (Calendar) now.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            int off = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Senin = 0
            int days = now.getActualMaximum(Calendar.DAY_OF_MONTH), today = now.get(Calendar.DAY_OF_MONTH);
            float rh = s * 0.105f;
            for (int d = 1; d <= days; d++) {
                int cell = off + d - 1;
                float px = x0 + cw * (cell % 7 + 0.5f), py = y0 + rh * (cell / 7 + 1.15f);
                if (d == today) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(accent);
                    c.drawCircle(px, py, rh * 0.46f, p);
                }
                text(c, String.valueOf(d), px, py, s * 0.07f, 0xFFFFFFFF, d == today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        }

        private void weather(Canvas c, float cx, float y, float s, int accent) {
            text(c, SettingsMenu.city().name, cx, y + s * 0.13f, s * 0.085f, 0xB3FFFFFF, Typeface.DEFAULT);
            if (wx == null) {
                text(c, "Belum ada data", cx, y + s * 0.55f, s * 0.08f, 0x99FFFFFF, Typeface.DEFAULT);
                return;
            }
            int kind = WeatherRepo.kind(wx.code);
            WeatherIcons.draw(c, kind, cx, y + s * 0.40f, s * 0.34f);
            text(c, Math.round(wx.temp) + "\u00b0", cx, y + s * 0.70f, s * 0.22f, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
            text(c, WeatherRepo.NAMES[kind], cx, y + s * 0.88f, s * 0.08f, accent, Typeface.DEFAULT);
        }
    }
}
