package com.vikas.torrentplayer.utils;

import java.io.File;

/**
 * Callbacks for the auto-updater. All methods are invoked on the main thread.
 *
 * <p>Default no-ops let callers override only the events they care about.
 */
public interface AppUpdateListener {

    /** Fired once when a newer release is detected on GitHub. */
    void onUpdateAvailable(String currentVersion, String latestVersion, String downloadUrl);

    /** No newer release found. Optional override. */
    default void onUpToDate(String currentVersion) {}

    /** Fatal error (network, parsing, etc.). Optional override. */
    default void onError(Throwable t) {}

    /** Download progress 0..100. Fired only during APK download. */
    default void onDownloadProgress(int percent) {}

    /** APK fully on disk. Caller can now hand it to the system installer. */
    default void onDownloadComplete(File apk) {}
}
