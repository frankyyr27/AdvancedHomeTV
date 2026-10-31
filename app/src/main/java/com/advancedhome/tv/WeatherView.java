package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Cuaca hari ini dan 7 hari ke depan, digambar langsung (tanpa tampilan bersarang). */
final class WeatherView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float u;
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", new Locale("id", "ID"));
    private WeatherRepo.Data data;
    private int tx = 0xFFFFFFFF, tx2 = 0xB3FFFFFF;

    WeatherView(Context c, float u) {
        super(c);
        this.u = u;
    }

    void setData(WeatherRepo.Data d) {
        data = d;
        invalidate();
    }

    void setColors(int primary, int secondary) {
        tx = primary;
        tx2 = secondary;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (data == null) return;
        float w = getWidth();
        int kind = WeatherRepo.kind(data.code);
        WeatherIcons.draw(c, kind, 26 * u, 26 * u, 44 * u);

        ink.setTypeface(Typeface.DEFAULT_BOLD);
        ink.setTextAlign(Paint.Align.LEFT);
        ink.setColor(tx);
        ink.setTextSize(36 * u);
        c.drawText(Math.round(data.temp) + "\u00b0", 58 * u, 38 * u, ink);

        ink.setTypeface(Typeface.DEFAULT);
        ink.setTextSize(17 * u);
        ink.setColor(tx);
        c.drawText(WeatherRepo.NAMES[kind], 128 * u, 22 * u, ink);
        ink.setColor(tx2);
        ink.setTextSize(15 * u);
        if (!data.days.isEmpty()) {
            WeatherRepo.Day t = data.days.get(0);
            c.drawText(Math.round(t.max) + "\u00b0 / " + Math.round(t.min) + "\u00b0", 128 * u, 42 * u, ink);
        }

        int n = Math.min(7, data.days.size());
        if (n == 0) return;
        float col = w / n;
        Calendar cal = Calendar.getInstance();
        ink.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            WeatherRepo.Day d = data.days.get(i);
            float cx = col * i + col / 2f;
            ink.setColor(tx2);
            ink.setTextSize(13 * u);
            ink.setTypeface(Typeface.DEFAULT);
            c.drawText(i == 0 ? "Ini" : dayFmt.format(cal.getTime()), cx, 78 * u, ink);
            WeatherIcons.draw(c, WeatherRepo.kind(d.code), cx, 100 * u, 26 * u);
            ink.setColor(tx);
            ink.setTypeface(Typeface.DEFAULT_BOLD);
            ink.setTextSize(15 * u);
            c.drawText(Math.round(d.max) + "\u00b0", cx, 128 * u, ink);
            ink.setColor(tx2);
            ink.setTypeface(Typeface.DEFAULT);
            ink.setTextSize(13 * u);
            c.drawText(Math.round(d.min) + "\u00b0", cx, 145 * u, ink);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }
}
