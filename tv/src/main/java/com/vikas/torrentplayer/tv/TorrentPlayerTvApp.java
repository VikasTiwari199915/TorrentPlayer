package com.vikas.torrentplayer.tv;

import android.app.Application;

import com.vikas.torrentplayer.service.TorrentDownloadService;

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
        TorrentDownloadService.start(this);
    }
}
