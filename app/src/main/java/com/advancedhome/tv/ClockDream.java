package com.advancedhome.tv;

import android.service.dreams.DreamService;

/** Screensaver sistem: bisa dipilih di Pengaturan TV > Screen saver, memakai jenis jam yang sama dengan Home. */
public class ClockDream extends DreamService {

    private SaverLayer layer;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Prefs.init(this);
        setInteractive(false);
        setFullscreen(true);
        float u = getResources().getDisplayMetrics().heightPixels / 720f;
        layer = new SaverLayer(this, u);
        setContentView(layer);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (layer != null) layer.start();
    }

    @Override
    public void onDreamingStopped() {
        if (layer != null) layer.stop();
        super.onDreamingStopped();
    }
}
