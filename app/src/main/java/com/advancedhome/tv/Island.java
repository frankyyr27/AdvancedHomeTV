package com.advancedhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kapsul hitam ala Dynamic Island di kanan atas: Input, Wi-Fi, Pengaturan. Label melebar saat fokus. */
final class Island extends LinearLayout {
    static final int INPUT = 0, WIFI = 1, SETTINGS = 2;

    final Chip input, wifi, settings;

    Island(Context c, float u) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(60 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x33FFFFFF);
        setBackground(bg);
        int p = Math.round(6 * u);
        setPadding(p, p, p, p);
        input = add(new Chip(c, u, INPUT, "Input"));
        wifi = add(new Chip(c, u, WIFI, "Wi-Fi"));
        settings = add(new Chip(c, u, SETTINGS, "Pengaturan"));
    }

    private Chip add(Chip ch) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (getChildCount() > 0) lp.leftMargin = Math.round(4 * ch.u);
        addView(ch, lp);
        return ch;
    }

    static final class Chip extends LinearLayout {
        final float u;
        private final Glyph glyph;
        private final TextView label;
        private final GradientDrawable fill = new GradientDrawable();
        private final int fullW;
        private ValueAnimator anim;
        private float ex;

        Chip(Context c, float u, int kind, String text) {
            super(c);
            this.u = u;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setClickable(true);
            fill.setCornerRadius(40 * u);
            fill.setColor(0x00FFFFFF);
            setBackground(fill);
            int padH = Math.round(9 * u);
            setPadding(padH, 0, padH, 0);
            glyph = new Glyph(c, kind, u);
            addView(glyph, new LinearLayout.LayoutParams(Math.round(26 * u), Math.round(44 * u)));
            label = new TextView(c);
            label.setText(text);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18 * u);
            label.setTextColor(0xFF111111);
            label.setSingleLine(true);
            label.setAlpha(0f);
            fullW = Math.round(label.getPaint().measureText(text)) + Math.round(8 * u);
            addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
            setMinimumHeight(Math.round(44 * u));
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect prev) {
            super.onFocusChanged(gained, direction, prev);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(ex, gained ? 1f : 0f);
            anim.setDuration(260);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    ex = (Float) a.getAnimatedValue();
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) label.getLayoutParams();
                    lp.width = Math.round(fullW * ex);
                    lp.leftMargin = Math.round(6 * u * ex);
                    label.setLayoutParams(lp);
                    label.setAlpha(ex);
                    fill.setColor(((int) (255 * ex) << 24) | 0x00FFFFFF);
                    glyph.setInk(ex > 0.5f ? 0xFF111111 : 0xFFFFFFFF);
                }
            });
            anim.start();
        }

        void setLabel(String t) { label.setText(t); }
    }

    /** Ikon kecil: monitor (input), busur Wi-Fi, atau roda gigi sederhana. */
    private static final class Glyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final int kind;
        private final float u;

        Glyph(Context c, int kind, float u) {
            super(c);
            this.kind = kind;
            this.u = u;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(1.5f, 1.9f * u));
            p.setColor(0xFFFFFFFF);
        }

        void setInk(int c) {
            if (p.getColor() != c) {
                p.setColor(c);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, s = 11 * u;
            if (kind == INPUT) {
                rf.set(cx - s, cy - s * 0.75f, cx + s, cy + s * 0.45f);
                c.drawRoundRect(rf, 2 * u, 2 * u, p);
                c.drawLine(cx - s * 0.5f, cy + s * 0.9f, cx + s * 0.5f, cy + s * 0.9f, p);
                c.drawLine(cx, cy + s * 0.45f, cx, cy + s * 0.9f, p);
            } else if (kind == WIFI) {
                for (int i = 1; i <= 3; i++) {
                    float r = s * i / 3f * 1.1f;
                    rf.set(cx - r, cy + s * 0.6f - r, cx + r, cy + s * 0.6f + r);
                    c.drawArc(rf, 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + s * 0.6f, 1.6f * u, p);
                p.setStyle(Paint.Style.STROKE);
            } else {
                c.drawCircle(cx, cy, s * 0.35f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    c.drawLine(cx + dx * s * 0.62f, cy + dy * s * 0.62f, cx + dx * s * 0.95f, cy + dy * s * 0.95f, p);
                }
                c.drawCircle(cx, cy, s * 0.62f, p);
            }
        }
    }
}
