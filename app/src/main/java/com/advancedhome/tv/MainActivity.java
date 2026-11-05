package com.advancedhome.tv;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
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
import android.os.Build;
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
import android.widget.ImageView;
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
import java.util.TimeZone;

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
    private int screenW, screenH, shelfH, tickN;
    private boolean day, lite, clockReady, saverOn, wallLoaded;
    private String appsKey = "", camUrl, sleepLabel = "";
    private long verseTry;
    private List<AppItem> allApps = new ArrayList<>();
    private String moving;
    private BibleApi.Ref currentRef;

    private FrameLayout root;
    private GradientDrawable wallpaper, bandBg, verseBg;
    private ImageView wallImg;
    private View wallScrim, blackout;
    private ScrollView scroll;
    private LinearLayout col, dockRow, grid, verseBox;
    private TextView title, sub, dateTv, vLabel, vText, vRef, toastTv;
    private FlipTile flipH, flipM;
    private WeatherView weather;
    private NowPlaying nowPlaying;
    private CameraPip cam;
    private Island island;
    private SysMonitor monitor;
    private Panel panel;
    private Reader reader;
    private TextPrompt prompt;
    private View prevFocus;
    private SaverLayer saver;
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
        // Suara klik dan geser bawaan sistem dimatikan; yang terdengar hanya System Sound pilihan Anda.
        getWindow().getDecorView().setSoundEffectsEnabled(false);
        quiet(root);
        menu = new SettingsMenu(this, panel);
        applyAll();
        applyWallpaper(false);
        reload();
        if (getIntent() != null && getIntent().getBooleanExtra("blackout", false)) blackout.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        ui.post(tick);
        monitor.start();
        updateWifiLabel();
        loadWeather();
        syncCamera();
        applyWallpaper(false);
        resetIdle();
        reload();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        ui.removeCallbacks(saverStart);
        monitor.stop();
        cam.stop();
        camUrl = null;
        hideSaverQuiet();
        super.onPause();
    }

    /** Tombol Home saat launcher sudah di depan: tutup panel, kembali ke atas, fokus ke aplikasi pertama. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getBooleanExtra("blackout", false)) {
            blackout.setVisibility(View.VISIBLE);
            return;
        }
        moving = null;
        reader.close();
        if (panel.isOpen()) panel.close();
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onUserInteraction() {
        resetIdle();
    }

    @Override
    public void onBackPressed() {
        if (prompt.isOpen()) {
            prompt.close();
        } else if (moving != null) {
            moving = null;
            toast("Posisi disimpan");
        } else if (panel.isOpen()) {
            panel.back();
        }
        // di Home biasa: Back tidak melakukan apa-apa
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (blackout.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) blackout.setVisibility(View.GONE);
            return true;
        }
        if (saverOn) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) hideSaver();
            return true;
        }
        if (reader.isOpen()) {
            reader.handleKey(e);
            return true;
        }
        int k = e.getKeyCode();
        if (prompt.isOpen()) {
            // Keyboard TV menerima tombol lebih dulu; Back yang sampai ke sini menutup kolom teks.
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) prompt.close();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        // Baris yang memutar suaranya sendiri (pilihan System Sound, volume) tidak memicu suara tombol global.
        boolean mute = panel.isOpen() && panel.focusedSilent();
        if (!mute && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
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

        wallImg = new ImageView(this);
        wallImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallImg.setVisibility(View.GONE);
        root.addView(wallImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallScrim = new View(this);
        wallScrim.setVisibility(View.GONE);
        root.addView(wallScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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

        // --- rak atas: jam flip, cuaca, Now Playing, kamera, judul, ayat ---
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

        nowPlaying = new NowPlaying(this, u);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = px(390);
        nlp.topMargin = px(60);
        shelf.addView(nowPlaying, nlp);

        cam = new CameraPip(this, u);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(px(320), px(180));
        clp.leftMargin = px(900);
        clp.topMargin = px(140);
        shelf.addView(cam, clp);
        cam.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (camUrl == null) return;
                try {
                    Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(camUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (camUrl.startsWith("rtsp://")) view.setPackage("org.videolan.vlc");
                    startActivity(view);
                } catch (Exception e) {
                    toast(camUrl.startsWith("rtsp://") ? "Pasang VLC untuk melihat kamera layar penuh" : "Tidak ada browser untuk membuka tautan");
                }
            }
        });

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
        verseBox.setFocusable(true);
        verseBox.setClickable(true);
        verseBox.setPadding(px(14), px(10), px(14), px(10));
        verseBg = new GradientDrawable();
        verseBg.setCornerRadius(px(20));
        verseBox.setBackground(verseBg);
        vLabel = text(12, true);
        vLabel.setText("AYAT HARI INI  \u00b7  OK untuk bahasa dan baca pasal");
        vLabel.setLetterSpacing(0.08f);
        vText = text(18, false);
        vRef = text(14, true);
        verseBox.addView(vLabel);
        verseBox.addView(vText);
        verseBox.addView(vRef);
        ((LinearLayout.LayoutParams) vText.getLayoutParams()).setMargins(0, px(5), 0, px(7));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(px(480), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        vlp.leftMargin = px(748);
        vlp.bottomMargin = px(12);
        shelf.addView(verseBox, vlp);
        verseBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(menu.versePage(), verseBox); }
        });
        verseBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                verseBg.setStroke(f ? px(3) : 0, 0xFFFFFFFF);
                if (f) scroll.smoothScrollTo(0, 0);
            }
        });

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

        // --- lapisan di atas: monitor, island, toast, panel, pembaca, screensaver ---
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
        FrameLayout.LayoutParams tolp = new FrameLayout.LayoutParams(px(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tolp.bottomMargin = px(34);
        toastTv.setGravity(Gravity.CENTER);
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

        reader = new Reader(this, root, u);

        prompt = new TextPrompt(this, root, u);
        prompt.setOnClose(new Runnable() {
            @Override public void run() { promptClosed(); }
        });

        saver = new SaverLayer(this, u);
        saver.setVisibility(View.GONE);
        root.addView(saver, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        blackout = new View(this);
        blackout.setBackgroundColor(0xFF000000);
        blackout.setVisibility(View.GONE);
        root.addView(blackout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private TextView text(float sizeUnits, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeUnits));
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    // ---------- suara bawaan sistem dimatikan ----------

    private final ViewGroup.OnHierarchyChangeListener quietListener = new ViewGroup.OnHierarchyChangeListener() {
        @Override public void onChildViewAdded(View parent, View child) { quiet(child); }
        @Override public void onChildViewRemoved(View parent, View child) { }
    };

    /** Mematikan efek suara klik dan fokus bawaan di semua tampilan, termasuk yang ditambahkan nanti. */
    private void quiet(View v) {
        v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            g.setOnHierarchyChangeListener(quietListener);
            for (int i = 0; i < g.getChildCount(); i++) quiet(g.getChildAt(i));
        }
    }

    // ---------- Island: Input, Wi-Fi, Timer, Pengaturan ----------

    private void wireIsland() {
        View.OnFocusChangeListener up = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) scroll.smoothScrollTo(0, 0);
            }
        };
        island.input.setOnFocusChangeListener(up);
        island.wifi.setOnFocusChangeListener(up);
        island.sleep.setOnFocusChangeListener(up);
        island.settings.setOnFocusChangeListener(up);
        island.input.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(inputsPage(), island.input); }
        });
        island.wifi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSystem(Settings.ACTION_WIFI_SETTINGS); }
        });
        island.sleep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(menu.sleepPage(), island.sleep); }
        });
        island.settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { panel.open(menu.root(), island.settings); }
        });
    }

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
        if (p.rows.isEmpty()) p.add(new Panel.Row("Tidak ada input ditemukan").value(textValue("")));
        p.add(new Panel.Row("Daftar input sistem").value(textValue("\u203a")).click(new Runnable() {
            @Override public void run() {
                panel.close();
                openSystem("com.android.tv.action.VIEW_INPUTS");
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

    // ---------- menu ikon: pindah, hapus, info, pustaka ----------

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
                    panel.replaceTop(libraryPage());
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
        int to;
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

    // ---------- wallpaper foto ----------

    private int dayNumber() {
        long now = System.currentTimeMillis();
        return (int) ((now + TimeZone.getDefault().getOffset(now)) / 86400000L);
    }

    private void applyWallpaper(boolean fetchNow) {
        int src = Prefs.geti("wp_src", 0);
        if (src <= 0) {
            wallLoaded = false;
            wallImg.setVisibility(View.GONE);
            wallScrim.setVisibility(View.GONE);
            applyAll();
            return;
        }
        wallImg.setVisibility(View.VISIBLE);
        wallScrim.setVisibility(View.VISIBLE);
        if (!wallLoaded) {
            Bitmap b = WallpaperRepo.cached(this, screenW, screenH);
            if (b != null) {
                wallImg.setImageBitmap(b);
                wallLoaded = true;
            }
        }
        if (fetchNow || !wallLoaded || Prefs.geti("wp_day", -1) != dayNumber()) {
            WallpaperRepo.fetch(this, src, screenW, screenH, new WallpaperRepo.Callback() {
                @Override public void onBitmap(Bitmap b, String info) {
                    if (b != null) {
                        wallImg.setImageBitmap(b);
                        wallLoaded = true;
                        Prefs.puti("wp_day", dayNumber());
                        applyAll();
                    } else if (info != null && !info.isEmpty()) {
                        toast(info);
                    }
                }
            });
        }
        applyAll();
    }

    // ---------- cuaca, ayat, tema, jam ----------

    private void loadWeather() {
        final SettingsMenu.City c = SettingsMenu.city();
        weather.setData(WeatherRepo.cached(c.lat, c.lon));
        WeatherRepo.refresh(this, c.lat, c.lon, new WeatherRepo.Callback() {
            @Override public void onData(WeatherRepo.Data data) { weather.setData(data); }
        });
    }

    private void loadVerse() {
        boolean show = Prefs.getb("verse", true);
        verseBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        final String[] off = Verses.today(this);
        final int lang = Prefs.geti("vlang", 0);
        currentRef = BibleApi.parse(off[0]);
        vText.setText(off[1]);
        vRef.setText(off[0] + " \u00b7 " + off[2] + " (offline)");
        if (currentRef == null || "verses.txt".equals(off[2])) return;
        final String key = dayNumber() + "|" + lang + "|" + off[0];
        if (key.equals(Prefs.gets("vd_key", ""))) {
            vText.setText(Prefs.gets("vd_text", ""));
            vRef.setText(off[0] + " \u00b7 " + Prefs.gets("vd_src", ""));
            return;
        }
        if (System.currentTimeMillis() - verseTry < 60000) return;   // jangan mengulang permintaan terlalu sering
        verseTry = System.currentTimeMillis();
        final BibleApi.Ref r = currentRef;
        BibleApi.chapter(r, r.chapter, lang, new BibleApi.Callback() {
            @Override public void onResult(List<String[]> verses) {
                if (verses == null) return;
                String t = BibleApi.verseText(verses, r.verse);
                if (t == null) return;
                String src = BibleApi.langLabel(lang);
                Prefs.puts("vd_key", key);
                Prefs.puts("vd_text", t);
                Prefs.puts("vd_src", src);
                vText.setText(t);
                vRef.setText(off[0] + " \u00b7 " + src);
            }
        });
    }

    private boolean isDay() {
        if (Prefs.geti("dark", 1) == 0) return true;
        SettingsMenu.City c = SettingsMenu.city();
        return Sky.isDay(c.lat, c.lon, Calendar.getInstance());
    }

    private void applyAll() {
        day = isDay();
        boolean photo = wallLoaded && Prefs.geti("wp_src", 0) > 0;
        lite = day && !photo;   // di atas foto, teks selalu putih
        wallpaper.setColors(day ? DAY_BG : NIGHT_BG);
        int dim = Prefs.geti("wp_dim", 25) + (day ? 0 : 20);
        wallScrim.setBackgroundColor(Math.min(255, Math.round(255 * dim / 100f)) << 24);
        int tx = lite ? 0xFF10131A : 0xFFFFFFFF;
        int tx2 = lite ? 0xA010131A : 0xB3FFFFFF;
        title.setTextColor(tx);
        sub.setTextColor(tx2);
        dateTv.setTextColor(tx2);
        vLabel.setTextColor(tx2);
        vText.setTextColor(tx);
        vRef.setTextColor(tx2);
        monitor.setTextColor(tx2);
        weather.setColors(tx, tx2);
        nowPlaying.setLook(lite, tx, tx2);
        bandBg.setColor(lite ? 0x66FFFFFF : 0x22FFFFFF);
        bandBg.setStroke(Math.max(1, px(1)), lite ? 0xB3FFFFFF : 0x44FFFFFF);
        for (IconTile t : tiles) t.applyLook(lite, 70);

        loadVerse();
        dateTv.setVisibility(Prefs.getb("showDate", true) ? View.VISIBLE : View.GONE);
        monitor.setVisibility(Prefs.getb("mon_on", true) ? View.VISIBLE : View.GONE);
        if (!Prefs.getb("np_on", true)) nowPlaying.setVisibility(View.GONE);
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
            tickN++;
            int left = SleepTimer.minutesLeft();
            String lab = left > 0 ? left + " mnt" : "Timer";
            if (!lab.equals(sleepLabel)) {
                sleepLabel = lab;
                island.sleep.setLabel(lab);
            }
            if (tickN % 3 == 0 && Prefs.getb("np_on", true)) nowPlaying.poll(MainActivity.this);
            ui.postDelayed(this, 1020 - System.currentTimeMillis() % 1000);
        }
    };

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private void syncCamera() {
        String url = Prefs.getb("cam_on", false) ? CameraPip.readUrl(this) : null;
        if (url == null) {
            cam.stop();
            cam.setVisibility(View.GONE);
            camUrl = null;
            return;
        }
        cam.setVisibility(View.VISIBLE);
        if (!url.equals(camUrl)) {
            camUrl = url;
            cam.start(url);
        }
    }

    // ---------- screensaver ----------

    private final Runnable saverStart = new Runnable() {
        @Override public void run() { showSaver(-1); }
    };

    private void resetIdle() {
        ui.removeCallbacks(saverStart);
        int m = Prefs.geti("ss_min", 5);
        if (m > 0 && Prefs.geti("ss_type", 0) < 5 && !saverOn) ui.postDelayed(saverStart, m * 60000L);
    }

    private void showSaver(int type) {
        ui.removeCallbacks(saverStart);
        saver.start(type);
        saver.setVisibility(View.VISIBLE);
        saverOn = true;
    }

    private void hideSaverQuiet() {
        saver.stop();
        saver.setVisibility(View.GONE);
        saverOn = false;
    }

    private void hideSaver() {
        hideSaverQuiet();
        resetIdle();
    }

    // ---------- SettingsMenu.Host ----------

    @Override
    public void changed() {
        applyAll();
        loadWeather();
        syncCamera();
        resetIdle();
    }

    @Override
    public void toast(String s) {
        toastTv.setText(s);
        toastTv.animate().cancel();
        toastTv.setAlpha(1f);
        toastTv.animate().alpha(0f).setStartDelay(2800).setDuration(300).start();
    }

    @Override
    public File profileFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "profile.json");
    }

    @Override
    public void wallpaperChanged() {
        applyWallpaper(true);
    }

    @Override
    public void previewSaver(final int type) {
        panel.close();
        ui.post(new Runnable() { @Override public void run() { showSaver(type); } });
    }

    @Override
    public void openSystem(String action) {
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

    @Override
    public boolean isDefaultHome() {
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(h, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void makeDefaultHome() {
        if (isDefaultHome()) {
            toast("Advanced Home TV sudah menjadi launcher utama");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 77);
                    return;
                }
            }
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception ignored) { }
        toast("Tekan tombol Home di remote, lalu pilih Advanced Home TV dan Selalu. Atau lewat ADB: cmd package set-home-activity");
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    @Override public void startSleep(int minutes) { SleepTimer.start(this, minutes); }
    @Override public void cancelSleep() { SleepTimer.cancel(this); }
    @Override public int sleepLeft() { return SleepTimer.minutesLeft(); }
    @Override public boolean sleepServiceOn() { return SleepService.enabled(); }

    @Override
    public String cameraStatus() {
        return CameraPip.kind(CameraPip.readUrl(this));
    }

    @Override
    public void promptText(String title, String hint, final String key) {
        prevFocus = getCurrentFocus();
        panel.setFocusBlocked(true);
        prompt.open(title, hint, Prefs.gets(key, ""), new TextPrompt.Callback() {
            @Override public void onText(String text) {
                Prefs.puts(key, text);
                textChanged(key);
            }
        });
    }

    /** Kolom teks tertutup: panel boleh menerima fokus lagi dan sorotan kembali ke baris tadi. */
    private void promptClosed() {
        panel.setFocusBlocked(false);
        View f = prevFocus;
        prevFocus = null;
        if (f != null && f.isAttachedToWindow()) f.requestFocus();
    }

    private void textChanged(String key) {
        if (key.equals("tmdb_key") && !Prefs.gets(key, "").isEmpty()) {
            Prefs.puti("wp_src", 3);
            applyWallpaper(true);
        } else if (key.equals("wp_url") && !Prefs.gets(key, "").isEmpty()) {
            Prefs.puti("wp_src", 5);
            applyWallpaper(true);
        } else if (key.equals("cam_url")) {
            Prefs.putb("cam_on", !Prefs.gets(key, "").isEmpty());
            syncCamera();
        }
        changed();
        toast("Tersimpan");
    }

    @Override
    public void disableSystemSounds() {
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 0);
                toast("Suara klik bawaan TV dimatikan");
                return;
            }
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast("Izinkan Advanced Home TV mengubah pengaturan sistem, lalu tekan tombol ini lagi");
            return;
        } catch (Exception ignored) { }
        toast("Lewat ADB: adb shell settings put system sound_effects_enabled 0");
    }

    @Override
    public void readChapter() {
        if (currentRef == null) {
            toast("Pasal ini tidak tersedia untuk ayat saat ini");
            return;
        }
        reader.open(currentRef, Prefs.geti("vlang", 0));
    }
}
