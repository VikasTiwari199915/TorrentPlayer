package com.vikas.torrentplayer.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.GitHubApiService;
import com.vikas.torrentplayer.api.models.github.GithubRelease;
import com.vikas.torrentplayer.api.models.github.ReleaseAssets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Drives an in-app auto-update workflow against GitHub releases.
 *
 * <p>Usage from a host app:
 * <pre>{@code
 * AppAutoUpdater.configure("VikasTiwari199915", "TorrentPlayer", null);
 * AppAutoUpdater.checkForUpdates(context, listener);
 * }</pre>
 *
 * <p>If {@code assetNameContains} is non-null, only assets whose file name
 * contains that substring are considered — lets the phone and TV apps share
 * a repo while picking up their own APK builds.
 *
 * <p>The updater handles the entire flow: list releases → pick newest with a
 * matching APK asset → notify caller → on demand, stream the APK to local
 * cache → fire ACTION_VIEW so the system installer prompts the user.
 *
 * @author Vikas Tiwari (original GTR-2e version)
 */
public final class AppAutoUpdater {

    private static final String TAG = "AppAutoUpdater";
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private static String sOwner = "";
    private static String sRepo = "";
    @Nullable private static String sAssetNameContains;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AppAutoUpdater() {}

    /** Configure the GitHub repo to check. {@code assetNameContains} narrows
     *  the search to APK assets whose file name contains that substring (e.g.
     *  "phone" vs "tv"). Pass {@code null} to pick the first APK asset. */
    public static void configure(@NonNull String owner, @NonNull String repo,
                                 @Nullable String assetNameContains) {
        sOwner = owner;
        sRepo = repo;
        sAssetNameContains = assetNameContains;
    }

    // ---------------------------------------------------------------------
    // Check
    // ---------------------------------------------------------------------

    public static void checkForUpdates(@NonNull Context context,
                                       @NonNull AppUpdateListener listener) {
        if (sOwner.isEmpty() || sRepo.isEmpty()) {
            listener.onError(new IllegalStateException("AppAutoUpdater.configure() not called"));
            return;
        }
        final String currentVersion = getAppVersionName(context);
        GitHubApiService api = ApiClient.github();
        api.getReleases(sOwner, sRepo).enqueue(new Callback<List<GithubRelease>>() {
            @Override
            public void onResponse(@NonNull Call<List<GithubRelease>> call,
                                   @NonNull retrofit2.Response<List<GithubRelease>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    listener.onError(new IOException("GitHub HTTP " + response.code()));
                    return;
                }
                String bestVersion = "";
                String bestUrl = "";
                for (GithubRelease release : response.body()) {
                    if (release == null
                            || release.isDraft()
                            || release.isPrerelease()
                            || release.getTagName() == null) {
                        continue;
                    }
                    String tag = release.getTagName();
                    String candidate = bestVersion.isEmpty() ? currentVersion : bestVersion;
                    if (!isNewVersionAvailable(candidate, tag)) continue;

                    String url = findApkAssetUrl(release.getAssets());
                    if (url == null) continue;

                    bestVersion = stripVersionPrefix(tag);
                    bestUrl = url;
                }
                if (bestVersion.isEmpty()) {
                    listener.onUpToDate(currentVersion);
                } else {
                    listener.onUpdateAvailable(currentVersion, bestVersion, bestUrl);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<GithubRelease>> call, @NonNull Throwable t) {
                Log.e(TAG, "GitHub releases fetch failed", t);
                listener.onError(t);
            }
        });
    }

    @Nullable
    private static String findApkAssetUrl(@Nullable List<ReleaseAssets> assets) {
        if (assets == null) return null;
        for (ReleaseAssets a : assets) {
            if (a == null || a.getContentType() == null || a.getBrowserDownloadUrl() == null) continue;
            if (!APK_MIME.equals(a.getContentType())) continue;
            if (sAssetNameContains != null && !sAssetNameContains.isEmpty()) {
                String name = a.getName();
                if (name == null || !name.contains(sAssetNameContains)) continue;
            }
            return a.getBrowserDownloadUrl();
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Download + install
    // ---------------------------------------------------------------------

    /**
     * Streams the APK to {@code <externalCache>/updates/<version>.apk}, then
     * calls {@link AppUpdateListener#onDownloadComplete(File)}. Caller is
     * expected to invoke {@link #install(Context, File)} from there (or
     * automatically — see {@link #downloadAndInstall(Context, String, String, AppUpdateListener)}).
     */
    public static void downloadApk(@NonNull Context context,
                                   @NonNull String url,
                                   @NonNull String versionName,
                                   @NonNull AppUpdateListener listener) {
        IO.execute(() -> {
            File cacheRoot = context.getExternalCacheDir();
            if (cacheRoot == null) cacheRoot = context.getCacheDir();
            File outDir = new File(cacheRoot, "updates");
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
            // Wipe any old apks lingering from previous releases
            File[] kids = outDir.listFiles();
            if (kids != null) for (File f : kids) //noinspection ResultOfMethodCallIgnored
                f.delete();
            File apkFile = new File(outDir, sanitize("update-" + versionName) + ".apk");

            OkHttpClient client = new OkHttpClient();
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    postError(listener, new IOException("HTTP " + resp.code()));
                    return;
                }
                ResponseBody body = resp.body();
                long total = body.contentLength();
                try (InputStream in = body.byteStream();
                     OutputStream out = new FileOutputStream(apkFile)) {
                    byte[] buf = new byte[16 * 1024];
                    long read = 0;
                    int n;
                    int lastReported = -1;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        read += n;
                        if (total > 0) {
                            int pct = (int) (read * 100 / total);
                            if (pct != lastReported) {
                                lastReported = pct;
                                final int p = pct;
                                MAIN.post(() -> listener.onDownloadProgress(p));
                            }
                        }
                    }
                    out.flush();
                }
                final File finalFile = apkFile;
                MAIN.post(() -> listener.onDownloadComplete(finalFile));
            } catch (IOException e) {
                postError(listener, e);
            }
        });
    }

    /** Convenience: download, then automatically fire the install intent. */
    public static void downloadAndInstall(@NonNull Context context,
                                          @NonNull String url,
                                          @NonNull String versionName,
                                          @NonNull AppUpdateListener listener) {
        downloadApk(context, url, versionName, new AppUpdateListener() {
            @Override
            public void onUpdateAvailable(String c, String l, String u) { /* unused */ }
            @Override public void onDownloadProgress(int p) { listener.onDownloadProgress(p); }
            @Override public void onDownloadComplete(File apk) {
                listener.onDownloadComplete(apk);
                install(context, apk);
            }
            @Override public void onError(Throwable t) { listener.onError(t); }
        });
    }

    /**
     * Fires ACTION_VIEW for the APK file via FileProvider. On Android 8+ the
     * user must have granted "Install unknown apps" for this package — if they
     * haven't, we redirect them to the Settings screen for it.
     */
    public static void install(@NonNull Context context, @NonNull File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent ask = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(ask); } catch (ActivityNotFoundException ignored) {}
            return;
        }

        Uri apkUri;
        try {
            apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "FileProvider can't expose " + apkFile, e);
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, APK_MIME);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(install);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No package installer available", e);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    public static String getAppVersionName(@NonNull Context context) {
        try {
            PackageInfo p = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return p.versionName != null ? p.versionName : "0";
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't read versionName", e);
            return "0";
        }
    }

    /**
     * Numeric dotted-version comparison. Strips a leading {@code v} or
     * {@code version} prefix. Non-numeric segments compare to zero so a tag
     * like {@code "v1.2.3-beta"} is parsed as {@code 1.2.3}.
     */
    public static boolean isNewVersionAvailable(String currentVersion, String latestGitHubVersion) {
        try {
            String latest = stripVersionPrefix(latestGitHubVersion);
            String current = stripVersionPrefix(currentVersion);
            String[] currentParts = current.split("[^0-9]+");
            String[] latestParts = latest.split("[^0-9]+");
            int len = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < len; i++) {
                int c = parseOrZero(currentParts, i);
                int l = parseOrZero(latestParts, i);
                if (l > c) return true;
                if (l < c) return false;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "version parse failed: " + currentVersion + " vs " + latestGitHubVersion, e);
            return false;
        }
    }

    private static int parseOrZero(String[] parts, int idx) {
        if (idx >= parts.length) return 0;
        String s = parts[idx];
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String stripVersionPrefix(String v) {
        if (v == null) return "0";
        return v.toLowerCase()
                .replace("version", "")
                .replaceFirst("^v", "")
                .trim();
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static void postError(@NonNull AppUpdateListener listener, @NonNull Throwable t) {
        Objects.requireNonNull(listener);
        MAIN.post(() -> listener.onError(t));
    }
}
