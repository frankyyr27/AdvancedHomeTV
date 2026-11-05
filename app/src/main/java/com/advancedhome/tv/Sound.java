package com.advancedhome.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Efek suara sintetis (dibuat oleh kode, bukan rekaman), terinspirasi karakter menu konsol.
 * Tidak ada berkas audio, jadi memorinya kecil. Suara diputar lewat volume media TV (bukan volume
 * "suara sistem" yang sering dibuat kecil) dan boleh saling tumpang tindih sampai 3 suara.
 */
final class Sound {
    static final String[] NAMES = {"Mati", "PS2", "PS3", "PS4", "PS5", "Nintendo", "XBOX", "SEGA", "Retro 8-bit"};
    // wave (0 sinus, 1 segitiga, 2 kotak), nada navigasi, nada konfirmasi, durasi nada (detik)
    private static final Object[][] P = {
            {}, {0, 520, new int[]{392, 587}, .22}, {1, 880, new int[]{660, 990}, .12},
            {0, 1200, new int[]{900, 1350}, .06}, {0, 700, new int[]{523, 784, 1047}, .14},
            {2, 988, new int[]{784, 1175}, .07}, {0, 330, new int[]{262, 392}, .10},
            {2, 1320, new int[]{988, 1480}, .06}, {2, 440, new int[]{660, 880}, .05}};
    private static final int RATE = 22050;
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService REL = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final Map<String, short[]> CACHE = new HashMap<>();

    private Sound() { }

    /** kind: "nav", "ok", atau "back". */
    static void play(final String kind) {
        final int pack = Prefs.geti("sound", 4);
        final int vol = Prefs.geti("vol", 60);
        if (pack <= 0 || pack >= P.length || vol <= 0 || ACTIVE.get() >= 3) return;
        ACTIVE.incrementAndGet();
        EX.execute(new Runnable() {
            @Override public void run() {
                AudioTrack t = null;
                boolean scheduled = false;
                try {
                    final short[] pcm = render(pack, kind, vol);
                    final AudioTrack track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(pcm.length * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC).build();
                    t = track;
                    track.write(pcm, 0, pcm.length);
                    track.play();
                    REL.schedule(new Runnable() {
                        @Override public void run() {
                            try { track.release(); } catch (Exception ignored) { }
                            ACTIVE.decrementAndGet();
                        }
                    }, pcm.length * 1000L / RATE + 120, TimeUnit.MILLISECONDS);
                    scheduled = true;
                } catch (Exception ignored) {
                    // perangkat tanpa audio: tidak ada suara
                } finally {
                    if (!scheduled) {
                        if (t != null) try { t.release(); } catch (Exception ignored) { }
                        ACTIVE.decrementAndGet();
                    }
                }
            }
        });
    }

    private static synchronized short[] render(int pack, String kind, int vol) {
        String key = pack + "|" + kind + "|" + vol;
        short[] c = CACHE.get(key);
        if (c != null) return c;
        Object[] p = P[pack];
        int wave = (Integer) p[0], nav = (Integer) p[1];
        int[] ok = (int[]) p[2];
        double d = (Double) p[3];
        short[] pcm;
        if (kind.equals("ok")) pcm = tones(ok, d, wave, vol);
        else if (kind.equals("back")) pcm = tones(new int[]{(int) (nav * 0.7)}, d * 1.6, wave, vol);
        else pcm = tones(new int[]{nav}, d, wave, vol);
        if (CACHE.size() > 80) CACHE.clear();
        CACHE.put(key, pcm);
        return pcm;
    }

    private static short[] tones(int[] freqs, double dur, int wave, int vol) {
        int per = (int) (RATE * dur);
        int step = (int) (per * 0.7);
        short[] out = new short[step * (freqs.length - 1) + per];
        // lebih keras dari sebelumnya; pembatas halus (tanh) mencegah pecah saat nada bertumpuk
        double amp = 1.5 * vol / 100.0 * (wave == 2 ? 0.55 : 1.0);
        int attack = Math.max(1, RATE / 400);   // 2,5 ms agar tidak berdetak di awal
        float[] mix = new float[out.length];
        for (int n = 0; n < freqs.length; n++) {
            for (int i = 0; i < per; i++) {
                double ph = (double) i * freqs[n] / RATE;
                double frac = ph - Math.floor(ph);
                double s = wave == 0 ? Math.sin(2 * Math.PI * ph)
                        : wave == 1 ? 4 * Math.abs(frac - 0.5) - 1
                        : (frac < 0.5 ? 1 : -1);
                double env = Math.exp(-3.0 * i / per) * Math.min(1.0, (double) i / attack);
                mix[n * step + i] += (float) (s * env * amp);
            }
        }
        for (int k = 0; k < out.length; k++) out[k] = (short) (32767 * Math.tanh(mix[k]));
        return out;
    }
}
