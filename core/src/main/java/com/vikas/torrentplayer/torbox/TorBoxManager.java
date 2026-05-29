package com.vikas.torrentplayer.torbox;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.torrent.TorrentManager;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Drives the TorBox flows (kept fully separate from the libtorrent engine):
 * preparing a magnet, listing the account, resolving a streamable URL, deleting
 * a torrent, and downloading a chosen file to the device.
 *
 * <p>Network work runs on a single background thread; results are posted to the
 * main thread. Local downloads are tracked as {@link Download} items exposed via
 * LiveData so the Downloads screen can render live progress.
 */
public final class TorBoxManager {

    private static final String TAG = "TorBoxManager";
    private static final long POLL_INTERVAL_MS = 4000L;
    private static final long REMOTE_TIMEOUT_MS = 30 * 60 * 1000L;

    private static final TorBoxManager INSTANCE = new TorBoxManager();
    public static TorBoxManager get() { return INSTANCE; }

    private Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final OkHttpClient fileHttp = new OkHttpClient();

    private final Map<String, Download> items = new LinkedHashMap<>();
    private final MutableLiveData<List<Download>> downloads =
            new MutableLiveData<>(new ArrayList<>());

    private TorBoxManager() {}

    public void init(Context ctx) {
        if (appContext == null && ctx != null) appContext = ctx.getApplicationContext();
    }

    public boolean hasKey() {
        return appContext != null && new PrefsManager(appContext).hasTorBoxKey();
    }

    public LiveData<List<Download>> downloads() { return downloads; }

    // ------------------------------------------------------------------
    // Callbacks (always invoked on the main thread)
    // ------------------------------------------------------------------

    public interface Callback<T> {
        void onResult(T result);
        void onError(String message);
    }

    /** Progress while TorBox prepares (downloads) the torrent on its servers. */
    public interface PrepareCallback {
        void onProgress(int percent, String state);
        void onReady(TorBoxClient.TbTorrent torrent);
        void onError(String message);
    }

    // ------------------------------------------------------------------
    // Local download tracking
    // ------------------------------------------------------------------

    public enum State { DOWNLOADING, DONE, ERROR }

    public static final class Download {
        public final String key;
        public final String title;
        public State state = State.DOWNLOADING;
        public int percent;
        public long speed;
        public File file;
        public String error;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        Download(String key, String title) { this.key = key; this.title = title; }
    }

    // ------------------------------------------------------------------
    // Public async API
    // ------------------------------------------------------------------

    private TorBoxClient client() throws IOException {
        String key = appContext != null ? new PrefsManager(appContext).getTorBoxKey() : "";
        if (key == null || key.isEmpty()) throw new IOException("No TorBox API key set");
        return new TorBoxClient(key);
    }

    /**
     * Add a magnet and wait until TorBox has the content (instant if cached),
     * then deliver the torrent with its file list. Reports preparation progress.
     */
    public void addAndPrepare(@NonNull String magnet, @NonNull PrepareCallback cb) {
        io.execute(() -> {
            try {
                TorBoxClient c = client();
                TorBoxClient.AddResult added = c.addMagnet(magnet);
                long id = added.torrentId;

                TorBoxClient.TbTorrent t = c.getTorrent(id);
                long deadline = System.currentTimeMillis() + REMOTE_TIMEOUT_MS;
                while ((t == null || !t.finished) && System.currentTimeMillis() < deadline) {
                    int pct = t != null ? (int) Math.round(t.progress * 100) : 0;
                    String state = t != null ? t.downloadState : "queued";
                    main.post(() -> cb.onProgress(pct, state));
                    sleep(POLL_INTERVAL_MS);
                    t = c.getTorrent(id);
                }
                if (t == null || !t.finished) {
                    postErr(cb, "TorBox timed out preparing the torrent");
                    return;
                }
                final TorBoxClient.TbTorrent ready = t;
                main.post(() -> cb.onReady(ready));
            } catch (Throwable e) {
                postErr(cb, msg(e));
            }
        });
    }

    /** Resolve a direct, streamable HTTPS URL for a file. */
    public void resolveStreamUrl(long torrentId, int fileId, @NonNull Callback<String> cb) {
        io.execute(() -> {
            try {
                String url = client().requestDownloadUrl(torrentId, fileId);
                main.post(() -> cb.onResult(url));
            } catch (Throwable e) {
                main.post(() -> cb.onError(msg(e)));
            }
        });
    }

    /** List every torrent currently in the user's TorBox account. */
    public void listAccount(@NonNull Callback<List<TorBoxClient.TbTorrent>> cb) {
        io.execute(() -> {
            try {
                List<TorBoxClient.TbTorrent> list = client().listTorrents();
                main.post(() -> cb.onResult(list));
            } catch (Throwable e) {
                main.post(() -> cb.onError(msg(e)));
            }
        });
    }

    /** Delete a torrent from the user's TorBox account. */
    public void deleteFromAccount(long torrentId, @NonNull Callback<Void> cb) {
        io.execute(() -> {
            try {
                client().controlTorrent(torrentId, "delete");
                main.post(() -> cb.onResult(null));
            } catch (Throwable e) {
                main.post(() -> cb.onError(msg(e)));
            }
        });
    }

    /**
     * Download a specific file of an already-prepared torrent to the device.
     * Shows up in the Downloads list with live progress.
     */
    @MainThread
    public Download downloadFile(long torrentId, int fileId, @NonNull String fileName,
                                 @NonNull String title) {
        String key = "tb:" + torrentId + ":" + fileId;
        Download existing = items.get(key);
        if (existing != null && existing.state != State.ERROR) return existing;
        Download d = new Download(key, title);
        items.put(key, d);
        publish();
        io.execute(() -> runFileDownload(d, torrentId, fileId, fileName));
        return d;
    }

    @MainThread
    public void remove(String key) {
        Download d = items.remove(key);
        if (d != null) d.cancelled.set(true);
        publish();
    }

    // ------------------------------------------------------------------
    // Local download worker
    // ------------------------------------------------------------------

    private void runFileDownload(Download d, long torrentId, int fileId, String fileName) {
        try {
            String url = client().requestDownloadUrl(torrentId, fileId);
            File out = destinationFor(fileName);
            update(d, State.DOWNLOADING, 0, 0);
            downloadToFile(d, url, out);
            if (d.cancelled.get()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                return;
            }
            d.file = out;
            update(d, State.DONE, 100, 0);
            notifyMediaScanner(out);
            Log.i(TAG, "TorBox download complete: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Log.e(TAG, "TorBox download failed", e);
            fail(d, msg(e));
        }
    }

    private void downloadToFile(Download d, String url, File out) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        out.getParentFile().mkdirs();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = fileHttp.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("empty body");
            long total = body.contentLength();
            long done = 0, lastBytes = 0, lastTs = System.currentTimeMillis();
            byte[] buf = new byte[256 * 1024];
            try (InputStream in = body.byteStream();
                 OutputStream os = new TruncatingFileOutputStream(out)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (d.cancelled.get()) return;
                    os.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastTs >= 700) {
                        long speed = (done - lastBytes) * 1000 / Math.max(1, now - lastTs);
                        int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                        update(d, State.DOWNLOADING, pct, speed);
                        lastTs = now; lastBytes = done;
                    }
                }
            }
        }
    }

    private static final class TruncatingFileOutputStream extends OutputStream {
        private final RandomAccessFile raf;
        TruncatingFileOutputStream(File f) throws IOException {
            this.raf = new RandomAccessFile(f, "rw");
            this.raf.setLength(0);
        }
        @Override public void write(int b) throws IOException { raf.write(b); }
        @Override public void write(@NonNull byte[] b, int off, int len) throws IOException {
            raf.write(b, off, len);
        }
        @Override public void close() throws IOException { raf.close(); }
    }

    private File destinationFor(String fileName) {
        File base = TorrentManager.get().getSaveDir();
        if (base == null) base = appContext.getExternalFilesDir(null);
        File dir = new File(base, "TorBox");
        String leaf = fileName != null ? fileName : "torbox.mp4";
        int slash = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (slash >= 0 && slash < leaf.length() - 1) leaf = leaf.substring(slash + 1);
        leaf = leaf.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (leaf.isEmpty()) leaf = "torbox.mp4";
        return new File(dir, leaf);
    }

    private void notifyMediaScanner(File file) {
        if (appContext == null || file == null || !file.exists()) return;
        try {
            MediaScannerConnection.scanFile(appContext,
                    new String[]{file.getAbsolutePath()}, null, null);
        } catch (Throwable ignored) {}
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String msg(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void postErr(PrepareCallback cb, String m) { main.post(() -> cb.onError(m)); }

    private void update(Download d, State state, int percent, long speed) {
        main.post(() -> { d.state = state; d.percent = percent; d.speed = speed; publish(); });
    }

    private void fail(Download d, String m) {
        main.post(() -> { d.state = State.ERROR; d.error = m; publish(); });
    }

    @MainThread
    private void publish() {
        downloads.setValue(new ArrayList<>(items.values()));
    }
}
