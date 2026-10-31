package com.advancedhome.tv;

import java.util.Calendar;

/** Matahari terbit dan terbenam dari koordinat (rumus NOAA yang disederhanakan). */
final class Sky {
    static boolean isDay(double lat, double lon, Calendar c) {
        int n = c.get(Calendar.DAY_OF_YEAR);
        double g = 2 * Math.PI / 365 * (n - 1);
        double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double la = Math.toRadians(lat);
        double cosH = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        if (cosH > 1) return false;
        if (cosH < -1) return true;
        double ha = Math.toDegrees(Math.acos(cosH));
        double rise = mod(720 - 4 * (lon + ha) - eq);
        double set = mod(720 - 4 * (lon - ha) - eq);
        double off = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 60000.0;
        double now = mod(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) - off);
        return rise < set ? (now >= rise && now < set) : (now >= rise || now < set);
    }

    private static double mod(double m) {
        m %= 1440;
        return m < 0 ? m + 1440 : m;
    }
}
