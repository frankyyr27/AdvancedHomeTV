package com.advancedhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Satu ubin angka jam flip. Digambar dengan canvas (tanpa bitmap), jadi memorinya kecil:
 * separuh atas melipat turun, lalu separuh bawah angka baru terbuka.
 */
final class FlipTile extends View {
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private final Paint hinge = new Paint();
    private final RectF r = new RectF();
    private final float radius;
    private String cur = "00", next = "00";
    private float t = 1f;
    private ValueAnimator anim;

    FlipTile(Context c, float u) {
        super(c);
        radius = 14 * u;
        ink.setColor(0xFF15161A);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(58 * u);
        ink.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        shade.setColor(0xFF000000);
        hinge.setColor(0x4D000000);
        hinge.setStrokeWidth(Math.max(1f, 1.5f * u));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        face.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFE4E4E9, Shader.TileMode.CLAMP));
    }

    void setValue(String v, boolean animate) {
        if (v.equals(next)) return;
        if (anim != null) anim.cancel();
        if (!animate) {
            cur = v;
            next = v;
            t = 1f;
            invalidate();
            return;
        }
        cur = next;
        next = v;
        t = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(460);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                if (t >= 1f) cur = next;
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), half = h / 2f;
        boolean flipping = t < 1f;
        half(c, flipping ? next : cur, true, 1f, w, h, half, false);
        half(c, cur, false, 1f, w, h, half, false);
        if (flipping) {
            if (t < 0.5f) half(c, cur, true, 1f - 2f * t, w, h, half, true);
            else half(c, next, false, 2f * t - 1f, w, h, half, true);
        }
        c.drawLine(0, half, w, half, hinge);
    }

    private void half(Canvas c, String s, boolean top, float sy, float w, float h, float half, boolean flap) {
        if (sy <= 0.01f) return;
        c.save();
        c.scale(1f, sy, 0f, half);
        c.clipRect(0f, top ? 0f : half, w, top ? half : h);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, radius, radius, face);
        c.drawText(s, w / 2f, h / 2f - (ink.ascent() + ink.descent()) / 2f, ink);
        if (flap) {
            shade.setAlpha((int) (90 * (1f - sy)));
            c.drawRect(0f, top ? 0f : half, w, top ? half : h, shade);
        }
        c.restore();
    }
}
