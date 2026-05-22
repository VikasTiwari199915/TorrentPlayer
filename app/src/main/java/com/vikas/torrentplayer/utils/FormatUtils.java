package com.vikas.torrentplayer.utils;

import java.util.Locale;

public final class FormatUtils {

    private FormatUtils() {}

    /** Human-readable bytes (1.45 GB / 720 MB / ...). */
    public static String humanBytes(long bytes) {
        if (bytes <= 0) return "—";
        final String[] units = { "B", "KB", "MB", "GB", "TB" };
        int unit = 0;
        double value = bytes;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    /** Human-readable bits/sec rate. */
    public static String humanSpeed(long bytesPerSecond) {
        return humanBytes(bytesPerSecond) + "/s";
    }

    /** "S01E03" style label when both available. */
    public static String seasonEpisodeLabel(Integer season, Integer episode) {
        if (season == null && episode == null) return null;
        StringBuilder sb = new StringBuilder();
        if (season != null) sb.append(String.format(Locale.US, "S%02d", season));
        if (episode != null) sb.append(String.format(Locale.US, "E%02d", episode));
        return sb.toString();
    }
}
