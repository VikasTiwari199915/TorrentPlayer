package com.vikas.torrentplayer.service;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.vikas.torrentplayer.torrent.DownloadHandle;
import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.FormatUtils;

import java.util.List;

/**
 * Foreground service that owns the libtorrent session for its entire lifetime.
 *
 * <p>The service has no UI/resource references baked in — host apps configure
 * it via {@link #configure(Config)} before starting it. That keeps the engine
 * module strictly UI-agnostic so both the phone and TV apps can share it.
 */
public class TorrentDownloadService extends Service {

    private static final String TAG = "TorrentDownloadService";
    private static final String CHANNEL_ID = "torrents_download";
    private static final int NOTIFICATION_ID = 0xD0;

    /**
     * Per-app config supplied by the host {@code Application} before the
     * service starts. Static lifetime is fine — there's only one running
     * service per process.
     */
    public static final class Config {
        public final Class<? extends Activity> launcherActivity;
        @DrawableRes public final int notificationIconRes;
        public final String notificationTitle;
        public final String idleText;

        public Config(@NonNull Class<? extends Activity> launcherActivity,
                      @DrawableRes int notificationIconRes,
                      @NonNull String notificationTitle,
                      @NonNull String idleText) {
            this.launcherActivity = launcherActivity;
            this.notificationIconRes = notificationIconRes;
            this.notificationTitle = notificationTitle;
            this.idleText = idleText;
        }
    }

    private static volatile Config sConfig;
    @Nullable private PowerManager.WakeLock wakeLock;
    @Nullable private WifiManager.WifiLock wifiLock;

    /** Host apps call this from {@code Application.onCreate()} before
     *  {@link #start(Context)} so the notification has valid content. */
    public static void configure(@NonNull Config config) {
        sConfig = config;
    }

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
            updateForCurrentDownloads(TorrentManager.get().downloads().getValue());
            refreshHandler.postDelayed(this, 2_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();

        postForeground(buildNotification(idleText(), 0, false));

        TorrentManager.get().init(getApplicationContext());
        TorrentManager.get().downloads().observeForever(listObserver);
        refreshHandler.post(refreshTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateForCurrentDownloads(TorrentManager.get().downloads().getValue());
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
        updatePowerLocks(false);
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
        updatePowerLocks(active > 0);

        String text;
        boolean indeterminate;
        int progress;
        if (active == 0) {
            text = idleText();
            indeterminate = false;
            progress = 0;
        } else if (active == 1) {
            text = (highlight != null ? highlight + " · " : "")
                    + totalProgress + "%  ·  "
                    + FormatUtils.humanSpeed(totalSpeed);
            indeterminate = totalProgress == 0;
            progress = totalProgress;
        } else {
            int avg = totalProgress / Math.max(1, active);
            text = active + " downloads · " + avg + "%  ·  " + FormatUtils.humanSpeed(totalSpeed);
            indeterminate = avg == 0;
            progress = avg;
        }

        postForeground(buildNotification(text, progress, indeterminate));
    }

    private Notification buildNotification(String text, int progress, boolean indeterminate) {
        Config cfg = sConfig;
        @DrawableRes int icon = cfg != null ? cfg.notificationIconRes : android.R.drawable.stat_sys_download;
        String title = cfg != null ? cfg.notificationTitle : "Downloads";

        PendingIntent pi = null;
        if (cfg != null) {
            Intent open = new Intent(this, cfg.launcherActivity);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        if (pi != null) b.setContentIntent(pi);
        if (progress > 0 || indeterminate) b.setProgress(100, progress, indeterminate);

        return b.build();
    }

    private void postForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE /* 34 */) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification);
    }

    private void updatePowerLocks(boolean active) {
        if (active) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "TorrentPlayer:downloads");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1
                            ? WifiManager.WIFI_MODE_FULL_HIGH_PERF
                            : WifiManager.WIFI_MODE_FULL;
                    wifiLock = wm.createWifiLock(mode, "TorrentPlayer:downloads-wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            try {
                if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            } catch (Throwable t) {
                Log.w(TAG, "wake lock acquire failed", t);
            }
            try {
                if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
            } catch (Throwable t) {
                Log.w(TAG, "wifi lock acquire failed", t);
            }
        } else {
            try {
                if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            } catch (Throwable t) {
                Log.w(TAG, "wifi lock release failed", t);
            }
            try {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            } catch (Throwable t) {
                Log.w(TAG, "wake lock release failed", t);
            }
        }
    }

    private String idleText() {
        Config cfg = sConfig;
        return cfg != null ? cfg.idleText : "Ready";
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Torrent download progress");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
