package com.vikas.torrentplayer.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

/**
 * Helpers for the "All files access" ({@code MANAGE_EXTERNAL_STORAGE}) permission.
 *
 * <p>Scoped storage ({@code getExternalMediaDirs()}) does NOT expose portable
 * USB drives or removable SD cards — those are only reachable through the
 * Storage Access Framework, which hands back {@code content://} URIs that
 * libtorrent4j can't write to. To let the download engine write a real
 * {@code File} path onto a USB drive we need broad filesystem access.
 *
 * <p>The app is sideloaded (not on Play Store) so the Play policy restriction
 * on this permission does not apply.
 */
public final class StoragePermissions {

    private StoragePermissions() {}

    /** True if the app can read/write arbitrary external volumes via File APIs. */
    public static boolean hasAllFilesAccess() {
        return Environment.isExternalStorageManager();
    }

    /** Per-app "All files access" settings screen. May not exist on some TV boxes. */
    public static Intent buildRequestIntent(Context ctx) {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        return i;
    }

    /** Generic "All files access" app list — fallback when the per-app screen is absent. */
    public static Intent buildFallbackIntent() {
        return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    }
}
