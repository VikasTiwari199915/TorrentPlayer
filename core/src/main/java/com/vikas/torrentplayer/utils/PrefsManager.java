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
}
