package com.vikas.torrentplayer;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.vikas.torrentplayer.service.TorrentDownloadService;

/**
 * Application entry point.
 * <ul>
 *   <li>Wires Material 3 dynamic colors ("Material You") across all activities.</li>
 *   <li>Starts {@link TorrentDownloadService} as a foreground service so the
 *       libtorrent session and the user's downloads survive the app being
 *       backgrounded / killed.</li>
 * </ul>
 */
public class TorrentPlayerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        // Start the foreground service. The service initialises TorrentManager
        // and restores any persisted downloads from Room.
        TorrentDownloadService.start(this);
    }
}
