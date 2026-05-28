package com.vikas.torrentplayer.tv;

import android.app.Application;

import com.vikas.torrentplayer.service.TorrentDownloadService;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.AppAutoUpdater;

public class TorrentPlayerTvApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Init the engine synchronously here so UI code can use it immediately
        // — the service's onCreate runs asynchronously and a fast click would
        // race ahead of TorrentManager.init() otherwise.
        TorrentManager.get().init(this);

        TorrentDownloadService.configure(new TorrentDownloadService.Config(
                TvMainActivity.class,
                android.R.drawable.stat_sys_download,
                getString(R.string.app_name),
                getString(R.string.notif_idle_text)
        ));
        TorrentDownloadService.start(this);

        // Auto-updater: TV-specific asset name match keeps phone and TV builds
        // distinguishable in the same GitHub release.
        AppAutoUpdater.configure("VikasTiwari199915", "TorrentPlayer", "tv");
    }
}
