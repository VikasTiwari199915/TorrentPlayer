package com.vikas.torrentplayer;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.AppAutoUpdater;

/**
 * Phone application entry point. Initialises the torrent engine synchronously
 * (so UI code can call into it the moment any activity loads), then starts
 * the foreground service for long-lived persistence.
 */
public class TorrentPlayerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Init the engine BEFORE the service — service onCreate runs
        // asynchronously and the user can fire a click handler that calls
        // startStream() before the service finishes coming up. Doing init
        // here makes the engine ready by the time any Activity is shown.
        TorrentManager.get().init(this);

        TorrentDownloadService.configure(new TorrentDownloadService.Config(
                MainActivity.class,
                R.drawable.rounded_download_24,
                getString(R.string.app_name),
                getString(R.string.notif_idle_text)
        ));
        TorrentDownloadService.start(this);

        // Auto-updater: configure the GitHub repo to poll. `assetNameContains`
        // narrows asset matching for repos that ship both phone & TV APKs in
        // the same release ("app" picks up app-debug.apk / app-release.apk).
        AppAutoUpdater.configure("VikasTiwari199915", "TorrentPlayer", "app");
    }
}
