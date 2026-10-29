package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Tile aplikasi bergaya kaca: membesar dan berkilau saat fokus. Hanya animasi transform dan alpha. */
final class GlassTile extends FrameLayout {

    final AppItem app;
    /** -1 untuk dock, 0 dan seterusnya untuk baris di bawah dock. */
    int row = -1;
    /** Nama di bawah tile (hanya untuk baris di bawah dock), boleh null. */
    TextView label;

    private final GradientDrawable base;
    private final View sheen;
    private final float u;
    private final int strokeW;

    GlassTile(Context c, AppItem app, int h, float u) {
        super(c);
        this.app = app;
        this.u = u;
        this.strokeW = Math.max(1, Math.round(u));
        setFocusable(true);
        setClickable(true);

        final float radius = 22 * u;
        base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x55FFFFFF, 0x1AFFFFFF});
        base.setCornerRadius(radius);
        base.setStroke(strokeW, 0x55FFFFFF);
        setBackground(base);

        ImageView art = new ImageView(c);
        art.setImageDrawable(app.art);
        if (app.banner) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Math.round(h * 0.16f);
            art.setPadding(p, p, p, p);
        }
        addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sheen = new View(c);
        GradientDrawable s = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x66FFFFFF, 0x00FFFFFF});
        s.setCornerRadius(radius);
        sheen.setBackground(s);
        sheen.setAlpha(0f);
        addView(sheen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    /** Warna kaca dan nama menyesuaikan mode siang atau malam. */
    void applyDay(boolean day) {
        base.setColors(new int[]{day ? 0x99FFFFFF : 0x55FFFFFF, day ? 0x44FFFFFF : 0x1AFFFFFF});
        base.setStroke(strokeW, day ? 0xCCFFFFFF : 0x55FFFFFF);
        if (label != null) label.setTextColor(day ? 0xFF10131A : 0xFFFFFFFF);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect);
        float s = gained ? 1.1f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(gained ? 14 * u : 0f)
                .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        sheen.animate().alpha(gained ? 1f : 0f).setDuration(200).start();
        if (label != null) label.animate().alpha(gained ? 1f : 0f).setDuration(150).start();
    }
}
