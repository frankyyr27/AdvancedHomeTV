package com.advancedhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Ikon aplikasi bergaya iOS: semua ikon menjadi kotak dengan sudut tumpul (24%) yang terisi penuh.
 * - Ikon adaptif: lapisan latar dan depan diisi penuh.
 * - Ikon biasa dengan latar transparan: ditaruh di atas warna dasar yang diambil dari isi ikon itu sendiri
 *   (gradasi halus dan kilau di atas, seperti ikon iOS).
 * - Ikon yang sudah padat atau berbentuk banner: dipotong di tengah agar memenuhi kotak.
 * Membesar saat fokus dengan transform saja, jadi tepinya tidak terpotong selama induknya tidak memotong anak.
 */
final class IconTile extends View {

    final AppItem app;
    /** -1 untuk dock, 0 dan seterusnya untuk baris di bawah dock. */
    int row = -1;
    /** Nama di bawah ikon (baris di bawah dock), boleh null. */
    TextView label;

    private final float u;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rr = new RectF();
    private int base = 0xFF5B6CF0;
    private boolean full, wide, day;
    private float sheen;
    private ValueAnimator sheenAnim;

    IconTile(Context c, AppItem app, float u) {
        super(c);
        this.app = app;
        this.u = u;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, u));
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        analyze();
        applyLook(false, 70);
    }

    /** Membaca isi ikon sekali: warna dominan, apakah sudah memenuhi kotak, apakah berbentuk banner. */
    private void analyze() {
        Drawable d = app.art;
        if (d == null || d instanceof AdaptiveIconDrawable) return;
        try {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            wide = iw > 0 && ih > 0 && iw >= ih * 1.3f;
            int n = 24;
            Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Rect old = d.copyBounds();
            d.setBounds(0, 0, n, n);
            d.draw(new Canvas(b));
            d.setBounds(old);
            float[] hsv = new float[3];
            double r = 0, g = 0, bl = 0, ws = 0;
            int opaque = 0;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int p = b.getPixel(x, y);
                    int a = Color.alpha(p);
                    if (a >= 200) opaque++;
                    if (a < 128) continue;
                    Color.colorToHSV(p, hsv);
                    double w = 0.15 + hsv[1] * hsv[2] * 3.0;   // warna yang hidup lebih berpengaruh
                    r += Color.red(p) * w;
                    g += Color.green(p) * w;
                    bl += Color.blue(p) * w;
                    ws += w;
                }
            }
            b.recycle();
            full = !wide && opaque >= n * n * 0.93f;
            if (ws > 0) base = Color.rgb((int) (r / ws), (int) (g / ws), (int) (bl / ws));
        } catch (Throwable ignored) {
            // ikon yang tidak bisa dianalisis tetap digambar dengan warna dasar bawaan
        }
    }

    private static int shade(int c, float f) {
        float t = f >= 0 ? 255f : 0f, k = Math.abs(f);
        return Color.rgb(Math.round(Color.red(c) + (t - Color.red(c)) * k),
                Math.round(Color.green(c) + (t - Color.green(c)) * k),
                Math.round(Color.blue(c) + (t - Color.blue(c)) * k));
    }

    /** Mode siang atau malam: warna dasar sedikit lebih terang atau gelap, dan warna nama. */
    void applyLook(boolean isDay, int transparency) {
        day = isDay;
        stroke.setColor(day ? 0x66FFFFFF : 0x33FFFFFF);
        if (label != null) label.setTextColor(day ? 0xFF10131A : 0xFFFFFFFF);
        rebuildShaders();
        invalidate();
    }

    private void rebuildShaders() {
        int h = getHeight();
        if (h <= 0) return;
        int b = day ? base : shade(base, -0.12f);
        basePaint.setShader(new LinearGradient(0, 0, 0, h, shade(b, 0.16f), shade(b, -0.22f), Shader.TileMode.CLAMP));
        glossPaint.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheenPaint.setShader(new LinearGradient(0, 0, w, h, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rebuildShaders();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float r = Math.min(w, h) * 0.24f;
        rr.set(0, 0, w, h);

        c.save();
        clip.reset();
        clip.addRoundRect(rr, r, r, Path.Direction.CW);
        c.clipPath(clip);
        Drawable d = app.art;
        if (d instanceof AdaptiveIconDrawable) {
            // Lapisan latar dan depan digambar manual agar memenuhi seluruh kotak (zona aman 72 dari 108).
            AdaptiveIconDrawable ai = (AdaptiveIconDrawable) d;
            int e = Math.round(w * 18f / 72f);
            Drawable bg = ai.getBackground(), fg = ai.getForeground();
            if (bg != null) { bg.setBounds(-e, -e, w + e, h + e); bg.draw(c); }
            if (fg != null) { fg.setBounds(-e, -e, w + e, h + e); fg.draw(c); }
        } else if (d != null) {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            Rect old = d.copyBounds();
            if ((wide || full) && iw > 0 && ih > 0) {
                // penuhi kotak: dipotong di tengah
                float s = Math.max((float) w / iw, (float) h / ih);
                int dw = Math.round(iw * s), dh = Math.round(ih * s);
                int l = (w - dw) / 2, t = (h - dh) / 2;
                d.setBounds(l, t, l + dw, t + dh);
            } else {
                c.drawRect(0, 0, w, h, basePaint);
                float box = w * 0.64f;
                float s = iw > 0 && ih > 0 ? Math.min(box / iw, box / ih) : 1f;
                int dw = iw > 0 ? Math.round(iw * s) : Math.round(box);
                int dh = ih > 0 ? Math.round(ih * s) : Math.round(box);
                int l = (w - dw) / 2, t = (h - dh) / 2;
                d.setBounds(l, t, l + dw, t + dh);
            }
            d.draw(c);
            d.setBounds(old);
            c.drawRect(0, 0, w, h * 0.6f, glossPaint);   // kilau tipis di bagian atas
        }
        if (sheen > 0f) {
            sheenPaint.setAlpha(Math.round(255 * sheen));
            c.drawRoundRect(rr, r, r, sheenPaint);
        }
        c.restore();
        c.drawRoundRect(rr, r, r, stroke);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect);
        float s = gained ? 1.12f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(gained ? 14 * u : 0f)
                .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        if (sheenAnim != null) sheenAnim.cancel();
        sheenAnim = ValueAnimator.ofFloat(sheen, gained ? 1f : 0f);
        sheenAnim.setDuration(180);
        sheenAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                sheen = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        sheenAnim.start();
    }
}
