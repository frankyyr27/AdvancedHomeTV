package com.advancedhome.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Ikon cuaca digambar dengan kode (tanpa gambar), jadi hemat memori. s = lebar ikon. */
final class WeatherIcons {
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();

    static void draw(Canvas c, int kind, float cx, float cy, float s) {
        switch (kind) {
            case 0: sun(c, cx, cy, s * 0.5f); break;
            case 1: sun(c, cx + s * 0.18f, cy - s * 0.16f, s * 0.36f); cloud(c, cx - s * 0.04f, cy + s * 0.1f, s * 0.8f, 0xFFE8EEF7); break;
            case 2: cloud(c, cx, cy, s, 0xFFCBD5E3); break;
            case 3:
                cloud(c, cx, cy - s * 0.12f, s * 0.8f, 0xFFCBD5E3);
                P.setColor(0xFFB6C2D4); P.setStrokeWidth(s * 0.07f); P.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(cx - s * 0.3f, cy + s * 0.24f, cx + s * 0.3f, cy + s * 0.24f, P);
                c.drawLine(cx - s * 0.2f, cy + s * 0.38f, cx + s * 0.4f, cy + s * 0.38f, P);
                break;
            case 4:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, 0xFFAEB9CA);
                P.setColor(0xFF5AA9FF); P.setStrokeWidth(s * 0.08f); P.setStrokeCap(Paint.Cap.ROUND);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * s * 0.22f, cy + s * 0.22f, cx + i * s * 0.22f - s * 0.06f, cy + s * 0.42f, P);
                break;
            case 5:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, 0xFFCBD5E3);
                P.setColor(0xFFFFFFFF); P.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * s * 0.22f, cy + s * 0.32f, s * 0.05f, P);
                break;
            default:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, 0xFFAEB9CA);
                P.setColor(0xFFFFC83D); P.setStyle(Paint.Style.FILL);
                PATH.reset();
                PATH.moveTo(cx + s * 0.04f, cy + s * 0.12f);
                PATH.lineTo(cx - s * 0.14f, cy + s * 0.34f);
                PATH.lineTo(cx - s * 0.02f, cy + s * 0.34f);
                PATH.lineTo(cx - s * 0.08f, cy + s * 0.5f);
                PATH.lineTo(cx + s * 0.16f, cy + s * 0.26f);
                PATH.lineTo(cx + s * 0.04f, cy + s * 0.26f);
                PATH.close();
                c.drawPath(PATH, P);
        }
    }

    private static void sun(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFFC83D);
        c.drawCircle(cx, cy, s * 0.3f, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(s * 0.09f);
        P.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * s * 0.45f, cy + dy * s * 0.45f, cx + dx * s * 0.62f, cy + dy * s * 0.62f, P);
        }
        P.setStyle(Paint.Style.FILL);
    }

    private static void cloud(Canvas c, float cx, float cy, float s, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawCircle(cx - s * 0.2f, cy + s * 0.05f, s * 0.2f, P);
        c.drawCircle(cx + s * 0.05f, cy - s * 0.1f, s * 0.27f, P);
        c.drawCircle(cx + s * 0.28f, cy + s * 0.08f, s * 0.18f, P);
        c.drawRect(cx - s * 0.2f, cy + s * 0.05f, cx + s * 0.28f, cy + s * 0.26f, P);
    }
}
