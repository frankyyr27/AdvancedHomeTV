package com.advancedhome.tv;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Ayat harian tanpa jaringan. Bawaan: King James Version (domain publik).
 * Untuk ayat bahasa Indonesia pilihan Anda sendiri, buat berkas verses.txt di folder
 * Android/data/com.advancedhome.tv/files/ dengan satu ayat per baris: Referensi|Teks
 */
final class Verses {
    private static final String[][] KJV = {
            {"Mazmur 23:1", "The LORD is my shepherd; I shall not want."},
            {"Filipi 4:13", "I can do all things through Christ which strengtheneth me."},
            {"Amsal 3:5", "Trust in the LORD with all thine heart; and lean not unto thine own understanding."},
            {"Matius 11:28", "Come unto me, all ye that labour and are heavy laden, and I will give you rest."},
            {"Mazmur 121:2", "My help cometh from the LORD, which made heaven and earth."},
            {"Mazmur 119:105", "Thy word is a lamp unto my feet, and a light unto my path."},
            {"Yesaya 41:10", "Fear thou not; for I am with thee: be not dismayed; for I am thy God: I will strengthen thee; yea, I will help thee; yea, I will uphold thee with the right hand of my righteousness."},
            {"Yosua 1:9", "Have not I commanded thee? Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee whithersoever thou goest."},
            {"Yohanes 3:16", "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."},
            {"Roma 8:28", "And we know that all things work together for good to them that love God, to them who are the called according to his purpose."},
            {"Mazmur 46:1", "God is our refuge and strength, a very present help in trouble."},
            {"Mazmur 27:1", "The LORD is my light and my salvation; whom shall I fear? the LORD is the strength of my life; of whom shall I be afraid?"},
            {"Amsal 16:3", "Commit thy works unto the LORD, and thy thoughts shall be established."},
            {"Matius 6:33", "But seek ye first the kingdom of God, and his righteousness; and all these things shall be added unto you."},
            {"Mazmur 37:4", "Delight thyself also in the LORD; and he shall give thee the desires of thine heart."},
            {"Yesaya 40:31", "But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles; they shall run, and not be weary; and they shall walk, and not faint."}};

    /** {referensi, teks, sumber} untuk hari ini. */
    static String[] today(Context c) {
        List<String[]> list = custom(c);
        boolean own = !list.isEmpty();
        if (!own) for (String[] v : KJV) list.add(v);
        String[] v = list.get((int) ((System.currentTimeMillis() / 86400000L) % list.size()));
        return new String[]{v[0], v[1], own ? "verses.txt" : "KJV"};
    }

    private static List<String[]> custom(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            File dir = c.getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, "verses.txt");
            if (f == null || !f.isFile() || f.length() > 200000) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf('|');
                if (i > 0 && i < line.length() - 1) out.add(new String[]{line.substring(0, i).trim(), line.substring(i + 1).trim()});
            }
            r.close();
        } catch (Exception ignored) { }
        return out;
    }
}
