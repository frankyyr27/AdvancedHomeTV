package com.advancedhome.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Layar Home. Semua ukuran dihitung dalam "unit panggung" 1280x720 lalu diskalakan ke
 * tinggi layar, jadi tampilannya sama di TV 720p, 1080p, atau 4K.
 */
public class MainActivity extends Activity {

    private static final int COLS = 5;
    private static final int GAP = 22;
    private static final int[] DAY_BG = {0xFF9DD9FF, 0xFFC9B8FF, 0xFFFFD2E2};
    private static final int[] NIGHT_BG = {0xFF070810, 0xFF1A1245, 0xFF0F5E5A};
    // Ayat contoh (Terjemahan Baru). Tahap berikutnya: diganti sumber YouVersion.
    private static final String[][] VERSES = {
            {"Mazmur 23:1", "TUHAN adalah gembalaku, takkan kekurangan aku."},
            {"Filipi 4:13", "Segala perkara dapat kutanggung di dalam Dia yang memberi kekuatan kepadaku."},
            {"Amsal 3:5", "Percayalah kepada TUHAN dengan segenap hatimu, dan janganlah bersandar kepada pengertianmu sendiri."},
            {"Matius 11:28", "Marilah kepada-Ku, semua yang letih lesu dan berbeban berat, Aku akan memberi kelegaan kepadamu."},
            {"Mazmur 121:2", "Pertolonganku ialah dari TUHAN, yang menjadikan langit dan bumi."},
            {"Mazmur 119:105", "Firman-Mu itu pelita bagi kakiku dan terang bagi jalanku."}};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<GlassTile> tiles = new ArrayList<>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", new Locale("id", "ID"));

    private float u;
    private int screenW, screenH, shelfH;
    private boolean day;
    private String appsKey = "";

    private GradientDrawable wallpaper, bandBg;
    private ScrollView scroll;
    private LinearLayout col, dockRow, grid;
    private TextView title, sub, hh, mm, date, vLabel, vText, vRef;

    // ---------- siklus hidup ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        u = screenH / 720f;
        setContentView(buildUi());
        applyTheme();
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        ui.post(tick);
        reload();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    /** Tombol Home saat launcher sudah di depan: kembali ke atas dan fokus ke aplikasi pertama. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        scroll.smoothScrollTo(0, 0);
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    /** Launcher tidak boleh tertutup oleh Back. */
    @Override
    public void onBackPressed() { }

    // ---------- membangun tampilan ----------

    private int px(float v) { return Math.round(v * u); }

    private View buildUi() {
        int tileH = Math.round(tileW() * 0.48f);
        int bandH = tileH + 2 * px(28);
        shelfH = screenH - (bandH + px(29));

        FrameLayout root = new FrameLayout(this);
        wallpaper = new GradientDrawable(GradientDrawable.Orientation.TL_BR, NIGHT_BG);
        root.setBackground(wallpaper);

        // ScrollView tanpa auto-scroll bawaan: kita atur sendiri di onFocused().
        scroll = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- rak atas: jam, judul, ayat ---
        FrameLayout shelf = new FrameLayout(this);
        shelf.setClipChildren(false);
        col.addView(shelf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shelfH));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        hh = flip(clock, true);
        mm = flip(clock, false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(px(250), px(76));
        clp.leftMargin = px(60);
        clp.topMargin = px(50);
        shelf.addView(clock, clp);

        date = text(20, false);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(px(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = px(64);
        dlp.topMargin = px(138);
        shelf.addView(date, dlp);

        title = text(46, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.02f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(px(660), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = px(64);
        tlp.topMargin = px(434);
        shelf.addView(title, tlp);

        sub = text(17, false);
        sub.setText("Tekan OK untuk membuka");
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(px(660), ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = px(64);
        slp.topMargin = px(492);
        shelf.addView(sub, slp);

        LinearLayout verse = new LinearLayout(this);
        verse.setOrientation(LinearLayout.VERTICAL);
        vLabel = text(12, true);
        vLabel.setText("AYAT HARI INI");
        vLabel.setLetterSpacing(0.12f);
        vText = text(18, false);
        vRef = text(14, true);
        String[] v = VERSES[(int) ((System.currentTimeMillis() / 86400000L) % VERSES.length)];
        vText.setText(v[1]);
        vRef.setText(v[0] + " \u00b7 Terjemahan Baru");
        verse.addView(vLabel);
        verse.addView(vText);
        verse.addView(vRef);
        ((LinearLayout.LayoutParams) vText.getLayoutParams()).setMargins(0, px(5), 0, px(7));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(px(464), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        vlp.leftMargin = px(756);
        vlp.bottomMargin = px(19);
        shelf.addView(verse, vlp);

        // --- dock: pita kaca dengan jarak sama di semua sisi ---
        FrameLayout dock = new FrameLayout(this);
        dock.setClipChildren(false);
        col.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bandH + px(29)));

        View band = new View(this);
        bandBg = new GradientDrawable();
        bandBg.setCornerRadius(px(44));
        band.setBackground(bandBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bandH);
        blp.leftMargin = px(60);
        blp.rightMargin = px(60);
        dock.addView(band, blp);

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bandH);
        rlp.leftMargin = px(60);
        rlp.rightMargin = px(60);
        dock.addView(dockRow, rlp);

        // --- baris aplikasi di bawah dock ---
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(0, px(40), 0, px(90));
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private int tileW() {
        return Math.round((screenW - px(176) - px(GAP) * (COLS - 1)) / (float) COLS);
    }

    private TextView text(float sizeUnits, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeUnits));
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Satu ubin angka jam gaya flip. */
    private TextView flip(LinearLayout row, boolean first) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFE6E6EB});
        g.setCornerRadius(px(14));
        f.setBackground(g);
        f.setElevation(px(4));
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(0xFF15161A);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(58));
        t.setIncludeFontPadding(false);
        f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View hinge = new View(this);
        hinge.setBackgroundColor(0x4D000000);
        f.addView(hinge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1.5f)), Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(121), px(76));
        if (!first) lp.leftMargin = px(8);
        row.addView(f, lp);
        return t;
    }

    // ---------- aplikasi ----------

    private void reload() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppItem> list = AppItem.load(MainActivity.this);
                final StringBuilder k = new StringBuilder();
                for (AppItem a : list) k.append(a.pkg).append(';');
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (k.toString().equals(appsKey)) return;
                        appsKey = k.toString();
                        buildApps(list);
                        if (getCurrentFocus() == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
                    }
                });
            }
        }, "load-apps").start();
    }

    private void buildApps(List<AppItem> apps) {
        tiles.clear();
        dockRow.removeAllViews();
        grid.removeAllViews();
        int tw = tileW();
        int th = Math.round(tw * 0.48f);
        int n = apps.size();

        for (int i = 0; i < Math.min(COLS, n); i++) {
            GlassTile t = new GlassTile(this, apps.get(i), th, u);
            wire(t, -1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tw, th);
            if (i > 0) lp.leftMargin = px(GAP);
            dockRow.addView(t, lp);
        }

        int rowIdx = 0;
        for (int i = COLS; i < n; i += COLS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            row.setClipChildren(false);
            row.setPadding(px(88), 0, px(88), 0);
            for (int j = i; j < Math.min(i + COLS, n); j++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setClipChildren(false);
                GlassTile t = new GlassTile(this, apps.get(j), th, u);
                wire(t, rowIdx);
                TextView lab = text(20, false);
                lab.setText(t.app.label);
                lab.setSingleLine(true);
                lab.setEllipsize(TextUtils.TruncateAt.END);
                lab.setGravity(Gravity.CENTER);
                lab.setAlpha(0f);
                t.label = lab;
                cell.addView(t, new LinearLayout.LayoutParams(tw, th));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(tw, px(30));
                llp.topMargin = px(16);
                cell.addView(lab, llp);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (j > i) clp.leftMargin = px(GAP);
                row.addView(cell, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = px(24);
            grid.addView(row, rlp);
            rowIdx++;
        }
        applyTheme();
    }

    private void wire(final GlassTile t, int row) {
        t.row = row;
        tiles.add(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(t.app); }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) onFocused(t);
            }
        });
    }

    private void launch(AppItem a) {
        try {
            startActivity(a.launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            // aplikasi baru dihapus atau tidak bisa dibuka: abaikan
        }
    }

    private void onFocused(GlassTile t) {
        title.setText(t.app.label);
        if (t.row < 0) {
            scroll.smoothScrollTo(0, 0);
            return;
        }
        int y = 0;
        View v = t;
        while (v != null && v != col) {
            y += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        int want = Math.max(shelfH - px(72), y + t.getHeight() + px(90) - screenH);
        scroll.smoothScrollTo(0, want);
    }

    // ---------- tema siang dan malam, jam ----------

    private boolean isDay() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 18;
    }

    private void applyTheme() {
        day = isDay();
        wallpaper.setColors(day ? DAY_BG : NIGHT_BG);
        int tx = day ? 0xFF10131A : 0xFFFFFFFF;
        int tx2 = day ? 0xA010131A : 0xB3FFFFFF;
        title.setTextColor(tx);
        sub.setTextColor(tx2);
        date.setTextColor(tx2);
        vLabel.setTextColor(tx2);
        vText.setTextColor(tx);
        vRef.setTextColor(tx2);
        bandBg.setColor(day ? 0x66FFFFFF : 0x22FFFFFF);
        bandBg.setStroke(Math.max(1, px(1)), day ? 0xB3FFFFFF : 0x44FFFFFF);
        for (GlassTile t : tiles) t.applyDay(day);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Calendar c = Calendar.getInstance();
            setIfChanged(hh, two(c.get(Calendar.HOUR_OF_DAY)));
            setIfChanged(mm, two(c.get(Calendar.MINUTE)));
            setIfChanged(date, dateFmt.format(c.getTime()));
            if (isDay() != day) applyTheme();
            ui.postDelayed(this, 1020 - System.currentTimeMillis() % 1000);
        }
    };

    private static void setIfChanged(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
