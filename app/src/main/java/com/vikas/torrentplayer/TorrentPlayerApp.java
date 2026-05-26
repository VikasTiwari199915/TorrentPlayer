package com.vikas.torrentplayer;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.vikas.torrentplayer.service.TorrentDownloadService;

/**
 * Phone application entry point. Configures the engine's foreground service
 * with phone-specific resources, then starts it.
 */
public class TorrentPlayerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        TorrentDownloadService.configure(new TorrentDownloadService.Config(
                MainActivity.class,
                R.drawable.rounded_download_24,
                getString(R.string.app_name),
                getString(R.string.notif_idle_text)
        ));
        TorrentDownloadService.start(this);
    }
}
