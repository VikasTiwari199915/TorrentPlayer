package com.vikas.torrentplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.vikas.torrentplayer.MainActivity;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.List;

/**
 * Foreground service that owns the libtorrent session for its entire lifetime.
 *
 * <p>Without this, the session lives inside the app process and Android can
 * kill it at any moment when the user backgrounds or closes the app, dropping
 * all downloads. Promoting to a foreground service with a persistent
 * notification keeps the process alive and signals "this app is doing real
 * work" to the OS scheduler.
 */
public class TorrentDownloadService extends Service {

    private static final String TAG = "TorrentDownloadService";
    private static final String CHANNEL_ID = "torrents_download";
    private static final int NOTIFICATION_ID = 0xD0;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, TorrentDownloadService.class);
        ContextCompat.startForegroundService(ctx, i);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, TorrentDownloadService.class));
    }

    private final Observer<List<DownloadHandle>> listObserver = this::updateForCurrentDownloads;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            // Pull the latest snapshot from TorrentManager every 2s. LiveData
            // only fires when the LIST changes, so this is what keeps the
            // progress in the notification ticking up.
            updateForCurrentDownloads(TorrentManager.get().downloads().getValue());
            refreshHandler.postDelayed(this, 2_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();

        // Promote immediately — Android allows up to 5s after startForegroundService.
        Notification n = buildNotification(getString(R.string.notif_idle_text), 0, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE /* 34 */) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        TorrentManager.get().init(getApplicationContext());
        TorrentManager.get().downloads().observeForever(listObserver);
        refreshHandler.post(refreshTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: if Android kills us under memory pressure we'll come
        // back; the TorrentManager will rehydrate downloads from Room.
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // When the user swipes the app from recents, only keep running if
        // there's actually a download in flight. Otherwise stop self so we
        // don't hold a wakelock / foreground notification forever.
        if (countActiveDownloads() == 0) {
            Log.i(TAG, "task removed and no active downloads — stopping service");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else {
            Log.i(TAG, "task removed but " + countActiveDownloads()
                    + " download(s) still active — staying alive");
        }
    }

    @Override
    public void onDestroy() {
        refreshHandler.removeCallbacks(refreshTick);
        TorrentManager.get().downloads().removeObserver(listObserver);
        super.onDestroy();
    }

    private int countActiveDownloads() {
        List<DownloadHandle> all = TorrentManager.get().downloads().getValue();
        if (all == null) return 0;
        int n = 0;
        for (DownloadHandle h : all) {
            DownloadHandle.State s = h.state.getValue();
            if (s == DownloadHandle.State.STARTING
                    || s == DownloadHandle.State.BUFFERING
                    || s == DownloadHandle.State.READY) n++;
        }
        return n;
    }

    /**
     * Recompute notification text/progress from the current set of downloads.
     */
    private void updateForCurrentDownloads(List<DownloadHandle> all) {
        int active = 0;
        int totalProgress = 0;
        long totalSpeed = 0;
        String highlight = null;
        for (DownloadHandle h : all == null ? java.util.Collections.<DownloadHandle>emptyList() : all) {
            DownloadHandle.State s = h.state.getValue();
            if (s == DownloadHandle.State.STARTING
                    || s == DownloadHandle.State.BUFFERING
                    || s == DownloadHandle.State.READY) {
                active++;
                if (highlight == null) highlight = h.title;
                DownloadHandle.Progress p = h.progress.getValue();
                if (p != null) {
                    totalProgress += p.percent;
                    totalSpeed += p.downloadSpeed;
                }
            }
        }

        String text;
        boolean indeterminate;
        int progress;
        if (active == 0) {
            text = getString(R.string.notif_idle_text);
            indeterminate = false;
            progress = 0;
        } else if (active == 1) {
            text = (highlight != null ? highlight + " · " : "")
                    + (totalProgress) + "%  ·  "
                    + FormatUtils.humanSpeed(totalSpeed);
            indeterminate = totalProgress == 0;
            progress = totalProgress;
        } else {
            int avg = totalProgress / Math.max(1, active);
            text = active + " downloads · " + avg + "%  ·  " + FormatUtils.humanSpeed(totalSpeed);
            indeterminate = avg == 0;
            progress = avg;
        }

        Notification n = buildNotification(text, progress, indeterminate);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, n);
    }

    private Notification buildNotification(String text, int progress, boolean indeterminate) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);

        if (progress > 0 || indeterminate) {
            b.setProgress(100, progress, indeterminate);
        }

        return b.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Torrent download progress");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
