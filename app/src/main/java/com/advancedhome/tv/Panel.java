package com.advancedhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel pengaturan kaca di tengah layar. Terbuka seperti balon dari tombol asalnya
 * (membesar sambil bergeser ke tengah) dan menutup kembali ke sana.
 */
final class Panel {

    interface Value { String get(); }
    interface Step { void step(int dir); }
    interface Listener { void onOpenChanged(boolean open, View source); }

    static final class Row {
        final String label;
        Value value;
        Runnable click;
        Step step;
        boolean toggle;
        /** true: suara tombol global tidak diputar untuk baris ini (baris yang memutar suaranya sendiri). */
        boolean silent;
        TextView valueView;
        Switch sw;

        Row(String label) { this.label = label; }
        Row value(Value v) { value = v; return this; }
        Row click(Runnable r) { click = r; return this; }
        Row step(Step s) { step = s; return this; }
        Row toggle() { toggle = true; return this; }
        Row silent() { silent = true; return this; }
    }

    static final class Page {
        final String title;
        final List<Row> rows = new ArrayList<>();
        Page(String title) { this.title = title; }
        Page add(Row r) { rows.add(r); return this; }
    }

    private final Context ctx;
    private final FrameLayout host;
    private final float u;
    private final View dim;
    private final LinearLayout card, list;
    private final ScrollView sv;
    private final TextView titleView;
    private final List<Page> stack = new ArrayList<>();
    /** Per halaman: indeks baris yang terfokus saat halaman lain dibuka di atasnya. */
    private final List<Integer> idx = new ArrayList<>();
    private Listener listener;
    private View source;
    private float srcDx, srcDy;

    Panel(Context c, FrameLayout host, float u) {
        this.ctx = c;
        this.host = host;
        this.u = u;

        dim = new View(c);
        dim.setBackgroundColor(0x59000000);
        dim.setVisibility(View.GONE);
        host.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61C1E24);
        bg.setCornerRadius(36 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x38FFFFFF);
        card.setBackground(bg);
        int pad = Math.round(26 * u);
        card.setPadding(pad, Math.round(22 * u), pad, pad);
        card.setVisibility(View.GONE);
        titleView = new TextView(c);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * u);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(0xFFFFFFFF);
        card.addView(titleView);
        ((LinearLayout.LayoutParams) titleView.getLayoutParams()).bottomMargin = Math.round(12 * u);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv = new ScrollView(c);
        sv.setFocusable(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(card, new FrameLayout.LayoutParams(Math.round(560 * u), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    void setListener(Listener l) { listener = l; }
    boolean isOpen() { return !stack.isEmpty(); }

    /** Baris yang sedang terfokus memutar suaranya sendiri, jadi suara tombol global dilewati. */
    boolean focusedSilent() {
        if (stack.isEmpty()) return false;
        View f = list.getFocusedChild();
        if (f == null) return false;
        int i = list.indexOfChild(f);
        List<Row> rows = top().rows;
        return i >= 0 && i < rows.size() && rows.get(i).silent;
    }

    /** Saat kolom teks tampil di atas panel, panel tidak boleh merebut fokus. */
    void setFocusBlocked(boolean blocked) {
        card.setDescendantFocusability(blocked ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    // ---------- membuka dan menutup ----------

    void open(Page p, View from) {
        stack.clear();
        idx.clear();
        stack.add(p);
        idx.add(0);
        source = from;
        render(0);
        dim.setAlpha(0f);
        dim.setVisibility(View.VISIBLE);
        dim.animate().alpha(1f).setDuration(260).start();
        card.setVisibility(View.VISIBLE);
        int[] a = new int[2], b = new int[2];
        from.getLocationInWindow(a);
        host.getLocationInWindow(b);
        srcDx = a[0] - b[0] + from.getWidth() / 2f - host.getWidth() / 2f;
        srcDy = a[1] - b[1] + from.getHeight() / 2f - host.getHeight() / 2f;
        card.setTranslationX(srcDx);
        card.setTranslationY(srcDy);
        card.setScaleX(0.08f);
        card.setScaleY(0.08f);
        card.setAlpha(0f);
        card.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(430).setInterpolator(new OvershootInterpolator(0.9f)).start();
        if (listener != null) listener.onOpenChanged(true, from);
    }

    void push(Page p) {
        idx.set(stack.size() - 1, focusedIndex());
        stack.add(p);
        idx.add(0);
        render(0);
        pulse();
    }

    /** Mengganti halaman teratas (mis. daftar yang berubah) dengan tetap mempertahankan baris yang terfokus. */
    void replaceTop(Page p) {
        if (stack.isEmpty()) return;
        int f = focusedIndex();
        stack.set(stack.size() - 1, p);
        render(f);
    }

    /** Kembali satu halaman, atau menutup panel kalau sudah di halaman pertama. */
    void back() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            idx.remove(idx.size() - 1);
            render(idx.get(stack.size() - 1));   // sorotan kembali ke baris yang membuka halaman ini
            pulse();
        } else {
            close();
        }
    }

    void close() {
        if (stack.isEmpty()) return;
        stack.clear();
        idx.clear();
        final View from = source;
        dim.animate().alpha(0f).setDuration(220).start();
        card.animate().translationX(srcDx).translationY(srcDy).scaleX(0.08f).scaleY(0.08f).alpha(0f)
                .setDuration(260).setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!stack.isEmpty()) return; // dibuka lagi sebelum animasi selesai
                        card.setVisibility(View.GONE);
                        dim.setVisibility(View.GONE);
                    }
                }).start();
        if (listener != null) listener.onOpenChanged(false, from);
    }

    private void pulse() {
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
    }

    // ---------- isi halaman ----------

    private Page top() { return stack.get(stack.size() - 1); }

    private int focusedIndex() {
        View f = list.getFocusedChild();
        return f == null ? 0 : Math.max(0, list.indexOfChild(f));
    }

    private void render(int focus) {
        Page p = top();
        titleView.setText(p.title);
        list.removeAllViews();
        int rowH = Math.round(54 * u);
        for (Row r : p.rows) {
            View v = makeRow(r);
            list.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
        }
        ViewGroup.LayoutParams lp = sv.getLayoutParams();
        lp.height = Math.min(p.rows.size() * rowH, Math.round(500 * u));
        sv.setLayoutParams(lp);
        refresh();
        int n = list.getChildCount();
        if (n > 0) list.getChildAt(Math.max(0, Math.min(focus, n - 1))).requestFocus();
    }

    private void refresh() {
        for (Row r : top().rows) {
            String v = r.value == null ? "" : r.value.get();
            if (r.sw != null) r.sw.setOn("Nyala".equals(v));
            else if (r.valueView != null) r.valueView.setText(v);
        }
    }

    private View makeRow(final Row r) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int pad = Math.round(18 * u);
        row.setPadding(pad, 0, pad, 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * u);
        bg.setColor(0x00FFFFFF);
        row.setBackground(bg);

        final TextView name = new TextView(ctx);
        name.setText(r.label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21 * u);
        name.setTextColor(0xFFFFFFFF);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (r.toggle) {
            r.sw = new Switch(ctx);
            row.addView(r.sw, new LinearLayout.LayoutParams(Math.round(48 * u), Math.round(29 * u)));
        } else {
            r.valueView = new TextView(ctx);
            r.valueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * u);
            r.valueView.setTextColor(0xA6FFFFFF);
            r.valueView.setSingleLine(true);
            row.addView(r.valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                bg.setColor(f ? 0xFFFFFFFF : 0x00FFFFFF);
                name.setTextColor(f ? 0xFF111111 : 0xFFFFFFFF);
                if (r.valueView != null) r.valueView.setTextColor(f ? 0xFF55555C : 0xA6FFFFFF);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Page before = top();
                if (r.click != null) r.click.run();
                else if (r.step != null) r.step.step(1);
                if (!stack.isEmpty() && top() == before) refresh();
            }
        });
        row.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN || r.step == null) return false;
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                r.step.step(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                refresh();
                return true;
            }
        });
        return row;
    }

    /** Saklar bergaya iOS. */
    static final class Switch extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private boolean on;

        Switch(Context c) { super(c); }

        void setOn(boolean v) {
            if (v != on) {
                on = v;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? 0xFF34C759 : 0x4DFFFFFF);
            rf.set(0, 0, w, h);
            c.drawRoundRect(rf, h / 2f, h / 2f, p);
            p.setColor(0xFFFFFFFF);
            float r = h / 2f - h * 0.08f;
            c.drawCircle(on ? w - h / 2f : h / 2f, h / 2f, r, p);
        }
    }
}
