package com.advancedhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Ikon aplikasi bergaya iOS: kotak membulat (sudut 22%), ikon adaptif diisi penuh.
 * Membesar saat fokus dengan transform saja, jadi tepinya tidak terpotong selama
 * induknya tidak memotong anak (clipChildren = false).
 */
final class IconTile extends View {

    final AppItem app;
    /** -1 untuk dock, 0 dan seterusnya untuk baris di bawah dock. */
    int row = -1;
    /** Nama di bawah ikon (baris di bawah dock), boleh null. */
    TextView label;

    private final float u;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rr = new RectF();
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
        applyLook(false, 70);
    }

    /** Warna kaca menyesuaikan siang atau malam dan tingkat transparansi (0 sampai 100). */
    void applyLook(boolean day, int transparency) {
        int a = Math.round(30 + (100 - transparency) * 1.2f);
        fill.setColor((a << 24) | 0x00FFFFFF);
        stroke.setColor(day ? 0xCCFFFFFF : 0x55FFFFFF);
        if (label != null) label.setTextColor(day ? 0xFF10131A : 0xFFFFFFFF);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheenPaint.setShader(new LinearGradient(0, 0, w, h, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float r = Math.min(w, h) * 0.2237f;
        rr.set(0, 0, w, h);
        c.drawRoundRect(rr, r, r, fill);

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
            int p = Math.round(w * 0.14f);
            Rect old = d.copyBounds();
            d.setBounds(p, p, w - p, h - p);
            d.draw(c);
            d.setBounds(old);
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
