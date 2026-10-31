package com.advancedhome.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Layar Home. Semua ukuran dihitung dalam "unit panggung" 1280x720 lalu diskalakan ke
 * tinggi layar, jadi tampilannya sama di TV 720p, 1080p, atau 4K.
 */
public class MainActivity extends Activity implements SettingsMenu.Host {

    private static final int COLS = 7;
    private static final int GAP = 28;
    private static final String K_HOME = "home";
    private static final String K_REMOVED = "removed";
    private static final int[] DAY_BG = {0xFF9DD9FF, 0xFFC9B8FF, 0xFFFFD2E2};
    private static final int[] NIGHT_BG = {0xFF070810, 0xFF1A1245, 0xFF0F5E5A};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<IconTile> tiles = new ArrayList<>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", new Locale("id", "ID"));

    private float u;
    private int screenW, screenH, shelfH;
    private boolean day, clockReady;
    private String appsKey = "";
    private List<AppItem> allApps = new ArrayList<>();
    private String moving;

    private FrameLayout root;
    private GradientDrawable wallpaper, bandBg;
    private ScrollView scroll;
    private LinearLayout col, dockRow, grid, verseBox;
    private TextView title, sub, dateTv, vLabel, vText, vRef, toastTv;
    private FlipTile flipH, flipM;
    private WeatherView weather;
    private Island island;
    private SysMonitor monitor;
    private Panel panel;
    private SettingsMenu menu;

    // ---------- siklus hidup ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        u = screenH / 720f;
        setContentView(buildUi());
        menu = new SettingsMenu(this, panel);
        applyAll();
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        ui.post(tick);
        monitor.start();
        updateWifiLabel();
        loadWeather();
        reload();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        monitor.stop();
        super.onPause();
    }

    /** Tombol Home saat launcher sudah di depan: tutup panel, kembali ke atas, fokus ke aplikasi pertama. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        moving = null;
        if (panel.isOpen()) panel.close();
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        if (moving != null) {
            moving = null;
            toast("Posisi disimpan");
        } else if (panel.isOpen()) {
            panel.back();
        }
        // di Home biasa: Back tidak melakukan apa-apa
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) Sound.play("nav");
            else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) Sound.play("ok");
            else if (k == KeyEvent.KEYCODE_BACK) Sound.play("back");
        }
        if (moving != null && handleMoveKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    // ---------- membangun tampilan ----------

    private int px(float v) { return Math.round(v * u); }

    private int iconSize() {
        return Math.round((screenW - px(176) - px(GAP) * (COLS - 1)) / (float) COLS);
    }

    private View buildUi() {
        int size = iconSize();
        int bandH = size + 2 * px(28);
        shelfH = screenH - (bandH + px(29));

        root = new FrameLayout(this);
        wallpaper = new GradientDrawable(GradientDrawable.Orientation.TL_BR, NIGHT_BG);
        root.setBackground(wallpaper);

        // ScrollView tanpa auto-scroll bawaan: diatur sendiri di onFocused().
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

        // --- rak atas: jam flip, cuaca, judul, ayat ---
        FrameLayout shelf = new FrameLayout(this);
        shelf.setClipChildren(false);
        col.addView(shelf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shelfH));

        dateTv = text(17, false);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(px(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = px(62);
        dlp.topMargin = px(24);
        shelf.addView(dateTv, dlp);

        flipH = new FlipTile(this, u);
        flipM = new FlipTile(this, u);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(px(136), px(80));
        hlp.leftMargin = px(58);
        hlp.topMargin = px(50);
        shelf.addView(flipH, hlp);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(px(136), px(80));
        mlp.leftMargin = px(58 + 136 + 8);
        mlp.topMargin = px(50);
        shelf.addView(flipM, mlp);

        weather = new WeatherView(this, u);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(px(280), px(152));
        wlp.leftMargin = px(58);
        wlp.topMargin = px(140);
        shelf.addView(weather, wlp);

        title = text(46, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.02f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(px(660), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = px(64);
        tlp.topMargin = shelfH - px(112);
        shelf.addView(title, tlp);

        sub = text(17, false);
        sub.setText("Tekan OK untuk membuka, tahan OK untuk menu");
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(px(660), ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = px(64);
        slp.topMargin = shelfH - px(56);
        shelf.addView(sub, slp);

        verseBox = new LinearLayout(this);
        verseBox.setOrientation(LinearLayout.VERTICAL);
        vLabel = text(12, true);
        vLabel.setText("AYAT HARI INI");
        vLabel.setLetterSpacing(0.12f);
        vText = text(18, false);
        vRef = text(14, true);
        verseBox.addView(vLabel);
        verseBox.addView(vText);
        verseBox.addView(vRef);
        ((LinearLayout.LayoutParams) vText.getLayoutParams()).setMargins(0, px(5), 0, px(7));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(px(464), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        vlp.leftMargin = px(756);
        vlp.bottomMargin = px(19);
        shelf.addView(verseBox, vlp);

        // --- dock: pita kaca, jarak sama di semua sisi ---
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
        grid.setPadding(0, px(44), 0, px(100));
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- lapisan di atas: monitor, island, toast, panel ---
        monitor = new SysMonitor(this, u);
        FrameLayout.LayoutParams mon = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        mon.rightMargin = px(62);
        mon.topMargin = px(96);
        root.addView(monitor, mon);

        island = new Island(this, u);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        ilp.rightMargin = px(45);
        ilp.topMargin = px(28);
        root.addView(island, ilp);
        wireIsland();
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                float a = Math.max(0f, 1f - sy / (shelfH * 0.35f));
                island.setAlpha(a);
                monitor.setAlpha(a);
            }
        });

        toastTv = text(19, false);
        toastTv.setTextColor(0xFFFFFFFF);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(0xCC1E2026);
        tg.setCornerRadius(px(26));
        toastTv.setBackground(tg);
        toastTv.setPadding(px(26), px(12), px(26), px(12));
        toastTv.setAlpha(0f);
        FrameLayout.LayoutParams tolp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tolp.bottomMargin = px(34);
        root.addView(toastTv, tolp);

        panel = new Panel(this, root, u);
        panel.setListener(new Panel.Listener() {
            @Override public void onOpenChanged(boolean open, View source) {
                int f = open ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
                scroll.setDescendantFocusability(f);
                island.setDescendantFocusability(f);
                if (!open) {
                    if (source != null && source.isAttachedToWindow()) source.requestFocus();
                    else focusFirst();
                }
            }
        });
        return root;
    }

    private TextView text(float sizeUnits, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeUnits));
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void wireIsland() {
        View.OnFocusChangeListener up = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) scroll.smoothScrollTo(0, 0);
            }
        };
        island.input.setOnFocusChangeListener(up);
        island.wifi.setOnFocusChangeListener(up);
        island.settings.setOnFocusChangeListener(up);
        island.input.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(inputsPage(), island.input); }
        });
        island.wifi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettingsScreen(Settings.ACTION_WIFI_SETTINGS); }
        });
        island.settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(menu.root(), island.settings); }
        });
    }

    // ---------- Input dan Wi-Fi ----------

    private Panel.Page inputsPage() {
        Panel.Page p = new Panel.Page("Input");
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (final TvInputInfo info : tim.getTvInputList()) {
                    String name = String.valueOf(info.loadLabel(this));
                    CharSequence custom = info.loadCustomLabel(this);
                    if (custom != null && custom.length() > 0) name = String.valueOf(custom);
                    p.add(new Panel.Row(name).value(textValue(typeName(info.getType()))).click(new Runnable() {
                        @Override public void run() { panel.close(); openInput(info); }
                    }));
                }
            }
        } catch (Exception ignored) {
            // perangkat tanpa layanan input TV atau izin ditolak
        }
        if (p.rows.isEmpty()) {
            p.add(new Panel.Row("Tidak ada input ditemukan").value(textValue("")));
        }
        p.add(new Panel.Row("Daftar input sistem").value(textValue("\u203a")).click(new Runnable() {
            @Override public void run() {
                panel.close();
                openSettingsScreen("com.android.tv.action.VIEW_INPUTS");
            }
        }));
        return p;
    }

    private static Panel.Value textValue(final String s) {
        return new Panel.Value() { @Override public String get() { return s; } };
    }

    private static String typeName(int t) {
        switch (t) {
            case TvInputInfo.TYPE_TUNER: return "TV";
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "Komponen";
            case TvInputInfo.TYPE_VGA: return "VGA";
            case TvInputInfo.TYPE_SVIDEO: return "S-Video";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "DisplayPort";
            default: return "Lainnya";
        }
    }

    private void openInput(TvInputInfo info) {
        try {
            Uri uri = info.isPassthroughInput()
                    ? TvContract.buildChannelUriForPassthroughInput(info.getId())
                    : TvContract.Channels.CONTENT_URI;
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Tidak bisa membuka input ini");
        }
    }

    private void openSettingsScreen(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                toast("Tidak bisa membuka pengaturan");
            }
        }
    }

    private void updateWifiLabel() {
        String label = "Wi-Fi";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) {
                label = "Offline";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                label = "Ethernet";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String ssid = wm.getConnectionInfo().getSSID();
                if (ssid != null && !ssid.contains("unknown")) label = ssid.replace("\"", "");
            }
        } catch (Exception ignored) { }
        island.wifi.setLabel(label);
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
                        allApps = list;
                        buildApps();
                        if (getCurrentFocus() == null) focusFirst();
                    }
                });
            }
        }, "load-apps").start();
    }

    private static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }

    /** Aplikasi di Home, berurutan: urutan tersimpan dulu, lalu aplikasi baru di belakangnya. */
    private List<AppItem> homeApps() {
        Set<String> removed = new HashSet<>(csv(Prefs.gets(K_REMOVED, "")));
        Map<String, AppItem> by = new LinkedHashMap<>();
        for (AppItem a : allApps) by.put(a.pkg, a);
        List<AppItem> out = new ArrayList<>();
        for (String p : csv(Prefs.gets(K_HOME, ""))) {
            AppItem a = by.get(p);
            if (a != null && !removed.contains(p) && !out.contains(a)) out.add(a);
        }
        for (AppItem a : allApps) if (!removed.contains(a.pkg) && !out.contains(a)) out.add(a);
        return out;
    }

    private void saveHome(List<AppItem> list) {
        List<String> pk = new ArrayList<>();
        for (AppItem a : list) pk.add(a.pkg);
        Prefs.puts(K_HOME, join(pk));
    }

    private void buildApps() {
        tiles.clear();
        dockRow.removeAllViews();
        grid.removeAllViews();
        List<AppItem> apps = homeApps();
        int size = iconSize();
        int n = apps.size();

        for (int i = 0; i < Math.min(COLS, n); i++) {
            IconTile t = new IconTile(this, apps.get(i), u);
            wire(t, -1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
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
                IconTile t = new IconTile(this, apps.get(j), u);
                wire(t, rowIdx);
                TextView lab = text(16, false);
                lab.setText(t.app.label);
                lab.setSingleLine(true);
                lab.setEllipsize(TextUtils.TruncateAt.END);
                lab.setGravity(Gravity.CENTER);
                t.label = lab;
                cell.addView(t, new LinearLayout.LayoutParams(size, size));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(size, px(24));
                llp.topMargin = px(12);
                cell.addView(lab, llp);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (j > i) clp.leftMargin = px(GAP);
                row.addView(cell, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = px(22);
            grid.addView(row, rlp);
            rowIdx++;
        }
        applyAll();
    }

    private void wire(final IconTile t, int row) {
        t.row = row;
        tiles.add(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (moving != null) {
                    moving = null;
                    toast("Posisi disimpan");
                } else {
                    launch(t.app);
                }
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (moving == null) panel.open(tileMenu(t), t);
                return true;
            }
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
            toast("Tidak bisa membuka " + a.label);
        }
    }

    private void onFocused(IconTile t) {
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
        int want = Math.max(shelfH - px(72), y + t.getHeight() + px(70) - screenH);
        scroll.smoothScrollTo(0, want);
    }

    private void focusFirst() {
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private void focusPkg(String pkg) {
        for (IconTile t : tiles) {
            if (t.app.pkg.equals(pkg)) {
                t.requestFocus();
                return;
            }
        }
        focusFirst();
    }

    // ---------- menu tile: pindah, hapus, info, pustaka ----------

    private Panel.Page tileMenu(final IconTile t) {
        final String pkg = t.app.pkg;
        return new Panel.Page(t.app.label)
                .add(new Panel.Row("Pindah").value(textValue("\u203a")).click(new Runnable() {
                    @Override public void run() {
                        panel.close();
                        moving = pkg;
                        toast("Geser dengan panah, tekan OK untuk meletakkan");
                        ui.post(new Runnable() { @Override public void run() { focusPkg(pkg); } });
                    }
                }))
                .add(new Panel.Row("Hapus dari Home").value(textValue("")).click(new Runnable() {
                    @Override public void run() {
                        List<String> rm = csv(Prefs.gets(K_REMOVED, ""));
                        if (!rm.contains(pkg)) rm.add(pkg);
                        Prefs.puts(K_REMOVED, join(rm));
                        panel.close();
                        buildApps();
                        focusFirst();
                        toast(t.app.label + " dipindah ke Pustaka Aplikasi");
                    }
                }))
                .add(new Panel.Row("Info aplikasi").value(textValue("")).click(new Runnable() {
                    @Override public void run() {
                        panel.close();
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception e) {
                            toast("Tidak bisa membuka info aplikasi");
                        }
                    }
                }));
    }

    @Override
    public Panel.Page libraryPage() {
        Panel.Page p = new Panel.Page("Pustaka Aplikasi");
        final Set<String> removed = new HashSet<>(csv(Prefs.gets(K_REMOVED, "")));
        for (final AppItem a : allApps) {
            if (!removed.contains(a.pkg)) continue;
            p.add(new Panel.Row(a.label).value(textValue("Tambah ke Home")).click(new Runnable() {
                @Override public void run() {
                    List<String> rm = csv(Prefs.gets(K_REMOVED, ""));
                    rm.remove(a.pkg);
                    Prefs.puts(K_REMOVED, join(rm));
                    List<AppItem> cur = homeApps();
                    cur.remove(a);
                    cur.add(a);
                    saveHome(cur);
                    buildApps();
                    toast(a.label + " ditambahkan ke Home");
                    panel.back();
                    panel.push(libraryPage());
                }
            }));
        }
        if (p.rows.isEmpty()) p.add(new Panel.Row("Semua aplikasi sudah ada di Home").value(textValue("")));
        return p;
    }

    /** Mode pindah: panah menggeser aplikasi, OK atau Back meletakkannya. */
    private boolean handleMoveKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean dpad = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (!dpad && !ok) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (ok) {
            moving = null;
            toast("Posisi disimpan");
            return true;
        }
        List<AppItem> cur = homeApps();
        int i = -1;
        for (int n = 0; n < cur.size(); n++) if (cur.get(n).pkg.equals(moving)) i = n;
        if (i < 0) {
            moving = null;
            return true;
        }
        int to = i;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) to = i - 1;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) to = i + 1;
        else if (k == KeyEvent.KEYCODE_DPAD_UP) to = i - COLS;
        else to = i + COLS;
        if (to >= 0 && to < cur.size()) {
            AppItem a = cur.remove(i);
            cur.add(to, a);
            saveHome(cur);
            buildApps();
            focusPkg(moving);
        }
        return true;
    }

    // ---------- cuaca, ayat, tema, jam ----------

    private void loadWeather() {
        final SettingsMenu.City c = SettingsMenu.city();
        WeatherRepo.Data d = WeatherRepo.cached(c.lat, c.lon);
        weather.setData(d);
        WeatherRepo.refresh(this, c.lat, c.lon, new WeatherRepo.Callback() {
            @Override public void onData(WeatherRepo.Data data) { weather.setData(data); }
        });
    }

    private boolean isDay() {
        if (Prefs.geti("dark", 1) == 0) return true;
        SettingsMenu.City c = SettingsMenu.city();
        return Sky.isDay(c.lat, c.lon, Calendar.getInstance());
    }

    private void applyAll() {
        day = isDay();
        wallpaper.setColors(day ? DAY_BG : NIGHT_BG);
        int tx = day ? 0xFF10131A : 0xFFFFFFFF;
        int tx2 = day ? 0xA010131A : 0xB3FFFFFF;
        title.setTextColor(tx);
        sub.setTextColor(tx2);
        dateTv.setTextColor(tx2);
        vLabel.setTextColor(tx2);
        vText.setTextColor(tx);
        vRef.setTextColor(tx2);
        monitor.setTextColor(tx2);
        weather.setColors(tx, tx2);
        bandBg.setColor(day ? 0x66FFFFFF : 0x22FFFFFF);
        bandBg.setStroke(Math.max(1, px(1)), day ? 0xB3FFFFFF : 0x44FFFFFF);
        for (IconTile t : tiles) t.applyLook(day, 70);

        boolean showVerse = Prefs.getb("verse", true);
        verseBox.setVisibility(showVerse ? View.VISIBLE : View.GONE);
        if (showVerse) {
            String[] v = Verses.today(this);
            vText.setText(v[1]);
            vRef.setText(v[0] + " \u00b7 " + v[2]);
        }
        dateTv.setVisibility(Prefs.getb("showDate", true) ? View.VISIBLE : View.GONE);
        tickClock();
    }

    private void tickClock() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (!Prefs.getb("h24", true)) {
            h = h % 12;
            if (h == 0) h = 12;
        }
        flipH.setValue(two(h), clockReady);
        flipM.setValue(two(c.get(Calendar.MINUTE)), clockReady);
        clockReady = true;
        String d = dateFmt.format(c.getTime());
        if (!d.contentEquals(dateTv.getText())) dateTv.setText(d);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            tickClock();
            if (isDay() != day) applyAll();
            ui.postDelayed(this, 1020 - System.currentTimeMillis() % 1000);
        }
    };

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    // ---------- SettingsMenu.Host ----------

    @Override
    public void changed() {
        applyAll();
        loadWeather();
    }

    @Override
    public void toast(String s) {
        toastTv.setText(s);
        toastTv.animate().cancel();
        toastTv.setAlpha(1f);
        toastTv.animate().alpha(0f).setStartDelay(2200).setDuration(300).start();
    }

    @Override
    public File profileFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "profile.json");
    }
}
