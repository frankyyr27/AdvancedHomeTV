package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Pembaca satu pasal penuh. Atas dan bawah menggulir, kiri dan kanan pindah pasal, OK atau Back menutup. */
final class Reader {

    private final float u;
    private final View dim;
    private final LinearLayout card;
    private final TextView titleTv, body;
    private final ScrollView sv;
    private BibleApi.Ref ref;
    private int chapter, lang, token;
    private boolean open;

    Reader(Context c, FrameLayout host, float u) {
        this.u = u;
        dim = new View(c);
        dim.setBackgroundColor(0xB3000000);
        dim.setVisibility(View.GONE);
        host.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01C1E24);
        bg.setCornerRadius(36 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x38FFFFFF);
        card.setBackground(bg);
        int pad = Math.round(30 * u);
        card.setPadding(pad, Math.round(24 * u), pad, Math.round(20 * u));
        card.setVisibility(View.GONE);

        titleTv = new TextView(c);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * u);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextColor(0xFFFFFFFF);
        card.addView(titleTv);

        body = new TextView(c);
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u);
        body.setTextColor(0xFFEDEDF0);
        body.setLineSpacing(8 * u, 1f);
        sv = new ScrollView(c);
        sv.setFocusable(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(440 * u));
        slp.topMargin = Math.round(12 * u);
        card.addView(sv, slp);

        TextView hint = new TextView(c);
        hint.setText("\u25b2 \u25bc gulir     \u25c0 \u25b6 pasal     OK atau Back tutup");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * u);
        hint.setTextColor(0x99FFFFFF);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Math.round(12 * u);
        card.addView(hint, hlp);

        host.addView(card, new FrameLayout.LayoutParams(Math.round(880 * u), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    boolean isOpen() { return open; }

    void open(BibleApi.Ref r, int language) {
        ref = r;
        chapter = r.chapter;
        lang = language;
        open = true;
        dim.setVisibility(View.VISIBLE);
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start();
        load();
    }

    void close() {
        if (!open) return;
        open = false;
        token++;
        dim.setVisibility(View.GONE);
        card.setVisibility(View.GONE);
    }

    private void load() {
        final int mine = ++token;
        titleTv.setText(ref.idn + " " + chapter + "  \u00b7  " + BibleApi.langLabel(lang));
        body.setText("Memuat\u2026");
        sv.scrollTo(0, 0);
        BibleApi.chapter(ref, chapter, lang, new BibleApi.Callback() {
            @Override public void onResult(List<String[]> verses) {
                if (mine != token) return;
                if (verses == null) {
                    body.setText("Pasal ini tidak bisa dimuat. Periksa koneksi internet, atau coba bahasa lain di pengaturan Ayat Harian.");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (String[] v : verses) {
                    if (sb.length() > 0) sb.append("\n\n");
                    if (!v[0].isEmpty()) sb.append(v[0]).append("   ");
                    sb.append(v[1]);
                }
                body.setText(sb.toString());
                sv.scrollTo(0, 0);
            }
        });
    }

    /** Mengonsumsi semua tombol selama pembaca terbuka. */
    boolean handleKey(KeyEvent e) {
        if (!open) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN: sv.smoothScrollBy(0, Math.round(260 * u)); break;
            case KeyEvent.KEYCODE_DPAD_UP: sv.smoothScrollBy(0, -Math.round(260 * u)); break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (chapter > 1) { chapter--; load(); }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                chapter++;
                load();
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                close();
                break;
            default: break;
        }
        return true;
    }
}
