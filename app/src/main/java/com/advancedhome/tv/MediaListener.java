package com.advancedhome.tv;

import android.service.notification.NotificationListenerService;

/**
 * Tidak melakukan apa-apa. Keberadaannya (setelah diberi akses notifikasi) adalah syarat dari Android
 * agar widget Now Playing boleh membaca sesi media yang sedang berjalan.
 */
public class MediaListener extends NotificationListenerService { }
