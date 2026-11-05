package com.advancedhome.tv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Kolom input teks di tengah layar untuk pengaturan yang butuh teks (kunci API, URL gambar, alamat kamera).
 * Menekan OK pada kolom teks memunculkan keyboard TV. Tersedia juga tombol Tempel (dari clipboard),
 * Hapus, Simpan, dan Batal. Cocok untuk teks panjang seperti tautan berbagi kamera.
 */
final class TextPrompt {

    interface Callback { void onText(String text); }

    private final Context ctx;
    private final float u;
    private final View dim;
    private final LinearLayout card;
    private final TextView titleTv, hintTv;
    private final EditText edit;
    private Callback cb;
    private Runnable onClose;
    private boolean open;

    TextPrompt(Context c, FrameLayout host, float u) {
        this.ctx = c;
        this.u = u;
        dim = new View(c);
        dim.setBackgroundColor(0xB3000000);
        dim.setVisibility(View.GONE);
        host.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01C1E24);
        bg.setCornerRadius(34 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x38FFFFFF);
        card.setBackground(bg);
        int pad = Math.round(30 * u);
        card.setPadding(pad, Math.round(26 * u), pad, Math.round(26 * u));
        card.setVisibility(View.GONE);

        titleTv = label(26, true, 0xFFFFFFFF);
        card.addView(titleTv);
        hintTv = label(16, false, 0x99FFFFFF);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Math.round(6 * u);
        card.addView(hintTv, hlp);

        edit = new EditText(c);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u);
        edit.setTextColor(0xFFFFFFFF);
        edit.setHintTextColor(0x66FFFFFF);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable eb = new GradientDrawable();
        eb.setColor(0x33FFFFFF);
        eb.setCornerRadius(16 * u);
        edit.setBackground(eb);
        int ep = Math.round(16 * u);
        edit.setPadding(ep, ep, ep, ep);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Math.round(18 * u);
        card.addView(edit, elp);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboard(); }
        });
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                save();
                return true;
            }
        });

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(button("Simpan", new Runnable() { @Override public void run() { save(); } }));
        row.addView(button("Tempel", new Runnable() { @Override public void run() { paste(); } }));
        row.addView(button("Hapus", new Runnable() { @Override public void run() { edit.setText(""); } }));
        row.addView(button("Batal", new Runnable() { @Override public void run() { close(); } }));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Math.round(20 * u);
        card.addView(row, rlp);

        host.addView(card, new FrameLayout.LayoutParams(Math.round(820 * u), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private TextView label(float size, boolean bold, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String text, final Runnable action) {
        final TextView b = label(20, true, 0xFFFFFFFF);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        final GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(26 * u);
        g.setColor(0x33FFFFFF);
        b.setBackground(g);
        int px = Math.round(26 * u), py = Math.round(12 * u);
        b.setPadding(px, py, px, py);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Math.round(12 * u);
        b.setLayoutParams(lp);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                g.setColor(f ? 0xFFFFFFFF : 0x33FFFFFF);
                b.setTextColor(f ? 0xFF111111 : 0xFFFFFFFF);
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return b;
    }

    boolean isOpen() { return open; }

    /** Dipanggil setiap kali kolom teks tertutup (Simpan, Batal, atau Back). */
    void setOnClose(Runnable r) { onClose = r; }

    void open(String title, String hint, String current, Callback callback) {
        cb = callback;
        open = true;
        titleTv.setText(title);
        hintTv.setText(hint);
        edit.setText(current == null ? "" : current);
        edit.setSelection(edit.getText().length());
        dim.setVisibility(View.VISIBLE);
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start();
        edit.requestFocus();
        card.postDelayed(new Runnable() { @Override public void run() { if (open) showKeyboard(); } }, 350);
    }

    void close() {
        if (!open) return;
        open = false;
        hideKeyboard();
        dim.setVisibility(View.GONE);
        card.setVisibility(View.GONE);
        if (onClose != null) onClose.run();
    }

    private void save() {
        Callback c = cb;
        String t = edit.getText().toString().trim();
        close();
        if (c != null) c.onText(t);
    }

    private void paste() {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0 && d.getItemAt(0).getText() != null) {
                edit.setText(d.getItemAt(0).getText().toString().trim());
                edit.setSelection(edit.getText().length());
            }
        } catch (Exception ignored) {
            // clipboard kosong atau tidak tersedia
        }
    }

    private void showKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) { }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
}
