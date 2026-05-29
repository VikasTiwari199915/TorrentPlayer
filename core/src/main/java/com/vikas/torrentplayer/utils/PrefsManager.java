package com.vikas.torrentplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Thin wrapper around SharedPreferences for app-wide settings.
 *
 * <p>The SharedPreferences name mirrors what {@code PreferenceManager
 * .getDefaultSharedPreferences()} would have used —
 * {@code "<pkg>_preferences"} — so the phone's PreferenceFragmentCompat keeps
 * reading/writing the same store.
 *
 * <p>Keys here MUST match the keys declared in each app's preferences.xml.
 */
public final class PrefsManager {

    public static final String KEY_API_KEY = "pref_api_key";
    public static final String KEY_DEFAULT_QUALITY = "pref_default_quality";
    public static final String KEY_VERIFIED_ONLY = "pref_verified_only";
    public static final String KEY_DELETE_ON_REMOVE = "pref_delete_on_remove";
    public static final String KEY_AUTO_RESUME = "pref_auto_resume";
    public static final String KEY_SAVE_VOLUME_PATH = "pref_save_volume_path";
    public static final String KEY_TV_BACKDROP = "pref_tv_backdrop";
    public static final String KEY_TORBOX_KEY = "pref_torbox_key";

    private final SharedPreferences sp;

    public PrefsManager(Context context) {
        Context app = context.getApplicationContext();
        this.sp = app.getSharedPreferences(
                app.getPackageName() + "_preferences",
                Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        return sp.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        sp.edit().putString(KEY_API_KEY, key == null ? "" : key.trim()).apply();
    }

    public boolean hasApiKey() {
        String k = getApiKey();
        return k != null && !k.isEmpty();
    }

    public String getDefaultQuality() {
        return sp.getString(KEY_DEFAULT_QUALITY, "any");
    }

    public boolean isVerifiedOnly() {
        return sp.getBoolean(KEY_VERIFIED_ONLY, false);
    }

    public boolean isDeleteOnRemove() {
        return sp.getBoolean(KEY_DELETE_ON_REMOVE, true);
    }

    /** When true, persisted downloads resume automatically on app start.
     *  Default OFF so the engine never silently fills the storage right
     *  after launch — the user has to explicitly tap Resume per download. */
    public boolean isAutoResume() {
        return sp.getBoolean(KEY_AUTO_RESUME, false);
    }

    /** Path of the user-selected volume root (one of getExternalMediaDirs() entries).
     *  Null means auto-pick (largest free space). */
    public String getSaveVolumePath() {
        return sp.getString(KEY_SAVE_VOLUME_PATH, null);
    }

    public void setSaveVolumePath(String path) {
        if (path == null) sp.edit().remove(KEY_SAVE_VOLUME_PATH).apply();
        else sp.edit().putString(KEY_SAVE_VOLUME_PATH, path).apply();
    }

    /** Show full-screen backdrop art on the TV details screen. Default ON;
     *  can be turned off on weak boxes where the large image hurts performance. */
    public boolean isTvBackdropEnabled() {
        return sp.getBoolean(KEY_TV_BACKDROP, true);
    }

    public void setTvBackdropEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_TV_BACKDROP, enabled).apply();
    }

    /** TorBox API key — enables the "Download via TorBox" path (server-side
     *  torrent fetch + full-speed HTTP download). Empty = feature off. */
    public String getTorBoxKey() {
        return sp.getString(KEY_TORBOX_KEY, "");
    }

    public void setTorBoxKey(String key) {
        sp.edit().putString(KEY_TORBOX_KEY, key == null ? "" : key.trim()).apply();
    }

    public boolean hasTorBoxKey() {
        String k = getTorBoxKey();
        return k != null && !k.isEmpty();
    }
}
