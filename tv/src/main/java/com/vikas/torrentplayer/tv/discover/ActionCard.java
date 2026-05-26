package com.vikas.torrentplayer.tv.discover;

/** Action cards on the "Library" row (Search / Downloads / Settings). */
public class ActionCard {
    public static final int SEARCH = 1;
    public static final int DOWNLOADS = 2;
    public static final int SETTINGS = 3;

    public final int id;
    public final String label;

    public ActionCard(int id, String label) {
        this.id = id;
        this.label = label;
    }
}
