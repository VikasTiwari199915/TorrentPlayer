package com.vikas.torrentplayer.tv.discover;

import androidx.annotation.DrawableRes;

/** Action cards on the "Library" row (Search / Downloads / TorBox / Settings). */
public class ActionCard {
    public static final int SEARCH = 1;
    public static final int DOWNLOADS = 2;
    public static final int SETTINGS = 3;
    public static final int TORBOX = 4;

    public final int id;
    public final String label;
    @DrawableRes public final int iconRes;

    public ActionCard(int id, String label, @DrawableRes int iconRes) {
        this.id = id;
        this.label = label;
        this.iconRes = iconRes;
    }
}
