package com.vikas.torrentplayer.torrent;

import java.util.Comparator;

/** Numeric comparison avoids E10 sorting ahead of E2; torrent indices remain unchanged. */
public final class EpisodeOrder implements Comparator<String> {
    public static final EpisodeOrder INSTANCE = new EpisodeOrder();
    private EpisodeOrder() {}
    @Override public int compare(String left, String right) {
        int a = 0, b = 0;
        while (a < left.length() && b < right.length()) {
            char x = left.charAt(a), y = right.charAt(b);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int ae = a, be = b;
                while (ae < left.length() && Character.isDigit(left.charAt(ae))) ae++;
                while (be < right.length() && Character.isDigit(right.charAt(be))) be++;
                int az = a, bz = b;
                while (az < ae - 1 && left.charAt(az) == '0') az++;
                while (bz < be - 1 && right.charAt(bz) == '0') bz++;
                int result = Integer.compare(ae - az, be - bz);
                if (result == 0) result = left.substring(az, ae).compareTo(right.substring(bz, be));
                if (result != 0) return result;
                a = ae; b = be;
            } else {
                int result = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (result != 0) return result;
                a++; b++;
            }
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
}
