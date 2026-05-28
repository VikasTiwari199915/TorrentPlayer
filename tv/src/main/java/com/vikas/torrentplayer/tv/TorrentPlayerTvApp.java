package com.vikas.torrentplayer.tv;

import android.app.Application;

import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.AppAutoUpdater;

public class TorrentPlayerTvApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        TorrentDownloadService.configure(new TorrentDownloadService.Config(
                TvMainActivity.class,
                android.R.drawable.stat_sys_download,
                getString(R.string.app_name),
                getString(R.string.notif_idle_text)
        ));

        // Engine init does a JNI session.start() which can take a few seconds
        // on weak TV hardware — keep it off the main thread, otherwise the
        // launcher transition gets a long ANR-grade stall. The foreground
        // service still calls init() in its own onCreate() so it stays
        // idempotently safe; UI activities also guard with init() before any
        // startStream(). Errors are logged so adb logcat catches them next
        // time a cable is connected.
        new Thread(() -> {
            try { TorrentManager.get().init(this); }
            catch (Throwable t) { android.util.Log.e("TorrentPlayerTvApp",
                    "engine init failed", t); }
        }, "engine-init").start();

        TorrentDownloadService.start(this);

        // Auto-updater: TV-specific asset name match keeps phone and TV builds
        // distinguishable in the same GitHub release.
        AppAutoUpdater.configure("VikasTiwari199915", "TorrentPlayer", "tv");
    }
}
