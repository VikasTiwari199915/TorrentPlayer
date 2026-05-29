package com.vikas.torrentplayer.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.vikas.torrentplayer.db.AppDatabase;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.torrent.TorrentManager;

import java.io.File;

/**
 * Wipes everything the app caches to disk — downloaded torrent payloads,
 * .torrent/.resume metadata, Glide / APK staging files, and Room download
 * rows — while preserving SharedPreferences so the API key, quality
 * preference, etc. all survive.
 *
 * <p>Designed for TVs with tight storage where the user wants a single button
 * to free space without losing setup.
 */
public final class CacheCleaner {

    private static final String TAG = "CacheCleaner";

    private CacheCleaner() {}

    /** Wall-clock measurement of how much disk we'd actually free. */
    public static long getCacheSize(@NonNull Context ctx) {
        long total = 0;
        File save = TorrentManager.get().getSaveDir();
        if (save != null) total += sizeOf(save);
        total += sizeOf(ctx.getExternalCacheDir());
        total += sizeOf(ctx.getCacheDir());
        return total;
    }

    /**
     * Synchronous; call from a background thread.
     *
     * @return number of bytes freed (approximate — equals the pre-clear size).
     */
    public static long clearAll(@NonNull Context ctx) {
        long before = getCacheSize(ctx);

        // 1. Stop libtorrent on every download so files aren't held open, and
        //    cancel/forget in-flight TorBox downloads (files swept in step 2).
        try {
            TorrentManager.get().removeAllSilently(/* deleteFiles = */ true);
        } catch (Throwable t) {
            Log.e(TAG, "removeAllSilently failed", t);
        }
        try {
            TorBoxManager.get().clearAll();
        } catch (Throwable t) {
            Log.e(TAG, "TorBox clearAll failed", t);
        }

        // 2. Sweep anything still on disk under the save dir (covers .meta/,
        //    .resume blobs, the videos themselves, orphaned folders).
        File save = TorrentManager.get().getSaveDir();
        if (save != null) deleteContents(save);

        // 3. Wipe external + internal cache (Glide bitmaps, downloaded APKs).
        deleteContents(ctx.getExternalCacheDir());
        deleteContents(ctx.getCacheDir());

        // 4. Drop Room rows so we don't try to restore non-existent torrents
        //    next launch.
        try {
            AppDatabase.get(ctx).downloadDao().deleteAll();
            AppDatabase.get(ctx).torBoxDao().deleteAll();
        } catch (Throwable t) {
            Log.e(TAG, "DB clear failed", t);
        }

        Log.i(TAG, "Cleared ~" + before + " bytes");
        return before;
    }

    private static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids == null) return 0;
        for (File k : kids) total += sizeOf(k);
        return total;
    }

    private static void deleteContents(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) deleteRecursive(k);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
