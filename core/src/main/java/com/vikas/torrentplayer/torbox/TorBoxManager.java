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

    private boolean loaded;

    public void init(Context ctx) {
        if (appContext == null && ctx != null) appContext = ctx.getApplicationContext();
        if (appContext != null && !loaded) {
            loaded = true;
            io.execute(this::restoreFromDb);
        }
    }

    /** Rebuild the in-memory list from Room. Finished files that still exist stay
     *  DONE (tap-to-play); interrupted ones resume from where they left off. */
    private void restoreFromDb() {
        List<com.vikas.torrentplayer.db.TorBoxEntity> rows;
        try { rows = dao().getAll(); }
        catch (Throwable t) { Log.e(TAG, "TorBox DB read failed", t); return; }
        if (rows == null) return;
        List<Runnable> toResume = new ArrayList<>();
        for (com.vikas.torrentplayer.db.TorBoxEntity e : rows) {
            Download d = new Download(e.key, e.title != null ? e.title : "Download");
            d.torrentId = e.torrentId;
            d.fileId = e.fileId;
            d.fileName = e.fileName;
            d.size = e.sizeBytes;
            d.percent = e.lastProgress;
            d.file = e.filePath != null ? new File(e.filePath) : null;
            State saved = e.lastState >= 0 && e.lastState < State.values().length
                    ? State.values()[e.lastState] : State.ERROR;
            boolean fileOk = d.file != null && d.file.exists() && d.file.length() > 0;
            if (saved == State.DONE && fileOk) {
                d.state = State.DONE;
            } else if (saved == State.PAUSED) {
                // Stay paused; the user resumes manually.
                d.state = State.PAUSED;
                d.pauseRequested.set(true);
            } else if (fileOk || saved == State.DOWNLOADING) {
                // Interrupted mid-download — resume in the background.
                d.state = State.DOWNLOADING;
                toResume.add(() -> runFileDownload(d));
            } else {
                d.state = State.ERROR;
                d.error = "File missing";
            }
            items.put(d.key, d);
        }
        main.post(this::publish);
        for (Runnable r : toResume) io.execute(r);
    }

    private com.vikas.torrentplayer.db.TorBoxDao dao() {
        return com.vikas.torrentplayer.db.AppDatabase.get(appContext).torBoxDao();
    }

    private void persist(Download d) {
        if (appContext == null) return;
        com.vikas.torrentplayer.db.TorBoxEntity e = new com.vikas.torrentplayer.db.TorBoxEntity();
        e.key = d.key;
        e.title = d.title;
        e.torrentId = d.torrentId;
        e.fileId = d.fileId;
        e.fileName = d.fileName;
        e.filePath = d.file != null ? d.file.getAbsolutePath() : null;
        e.sizeBytes = d.size;
        e.lastState = d.state.ordinal();
        e.lastProgress = d.percent;
        e.addedAt = System.currentTimeMillis();
        io.execute(() -> { try { dao().upsert(e); } catch (Throwable ignored) {} });
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

    public enum State { DOWNLOADING, PAUSED, DONE, ERROR }

    public static final class Download {
        public final String key;
        public final String title;
        public long torrentId;
        public int fileId;
        public String fileName;
        public long size;          // expected total bytes
        public State state = State.DOWNLOADING;
        public int percent;
        public long speed;
        public File file;
        public String error;
        final AtomicBoolean cancelled = new AtomicBoolean(false);    // remove: stop + delete
        final AtomicBoolean pauseRequested = new AtomicBoolean(false); // pause: stop + keep file
        boolean shouldStop() { return cancelled.get() || pauseRequested.get(); }
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
                                 long size, @NonNull String title) {
        String key = "tb:" + torrentId + ":" + fileId;
        Download existing = items.get(key);
        if (existing != null && existing.state != State.ERROR) return existing;
        Download d = new Download(key, title);
        d.torrentId = torrentId;
        d.fileId = fileId;
        d.fileName = fileName;
        d.size = size;
        d.file = destinationFor(fileName);   // known up front so partial-play has a path
        items.put(key, d);
        publish();
        persist(d);
        io.execute(() -> runFileDownload(d));
        return d;
    }

    /** Cancel + forget every TorBox download (used by the cache cleaner). The
     *  DB rows and on-disk files are wiped by {@code CacheCleaner} itself. */
    public void clearAll() {
        for (Download d : items.values()) d.cancelled.set(true);
        items.clear();
        main.post(this::publish);
    }

    @MainThread
    public void pause(String key) {
        Download d = items.get(key);
        if (d == null || d.state != State.DOWNLOADING) return;
        d.pauseRequested.set(true);    // worker stops at the next chunk, keeps the file
        d.state = State.PAUSED;
        d.speed = 0;
        publish();
        persist(d);
    }

    @MainThread
    public void resume(String key) {
        Download d = items.get(key);
        if (d == null || d.state != State.PAUSED) return;
        d.pauseRequested.set(false);
        d.state = State.DOWNLOADING;
        publish();
        persist(d);
        io.execute(() -> runFileDownload(d));   // resumes via HTTP Range
    }

    @MainThread
    public void remove(String key) {
        Download d = items.remove(key);
        if (d != null) d.cancelled.set(true);
        publish();
        io.execute(() -> { try { dao().deleteByKey(key); } catch (Throwable ignored) {} });
    }

    // ------------------------------------------------------------------
    // Local download worker
    // ------------------------------------------------------------------

    private void runFileDownload(Download d) {
        try {
            String url = client().requestDownloadUrl(d.torrentId, d.fileId);
            File out = d.file != null ? d.file : destinationFor(d.fileName);
            d.file = out;
            update(d, State.DOWNLOADING, d.percent, 0);
            downloadToFile(d, url, out);
            if (d.cancelled.get()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                return;
            }
            if (d.pauseRequested.get()) return;   // paused: keep partial file, state already PAUSED
            update(d, State.DONE, 100, 0);
            notifyMediaScanner(out);
            Log.i(TAG, "TorBox download complete: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Log.e(TAG, "TorBox download failed", e);
            fail(d, msg(e));
        }
    }

    /** Streams the file to disk, resuming via a Range request if a partial file
     *  already exists. The file grows sequentially, which is what lets a player
     *  read the head while the rest is still arriving. */
    private void downloadToFile(Download d, String url, File out) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        out.getParentFile().mkdirs();
        long existing = out.exists() ? out.length() : 0;
        boolean resume = existing > 0 && (d.size <= 0 || existing < d.size);

        Request.Builder rb = new Request.Builder().url(url).get();
        if (resume) rb.header("Range", "bytes=" + existing + "-");
        try (Response resp = fileHttp.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("empty body");
            boolean append = resume && resp.code() == 206;  // server honoured the range
            long total = d.size > 0 ? d.size
                    : (append ? existing + body.contentLength() : body.contentLength());
            long done = append ? existing : 0;
            long lastBytes = done, lastTs = System.currentTimeMillis();
            byte[] buf = new byte[256 * 1024];
            try (InputStream in = body.byteStream();
                 RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
                if (append) raf.seek(existing); else raf.setLength(0);
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (d.shouldStop()) return;   // pause (keep file) or remove (deleted by caller)
                    raf.write(buf, 0, n);
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
        main.post(() -> {
            // A late progress tick must not clobber a pause the user just made.
            if (state == State.DOWNLOADING && d.pauseRequested.get()) return;
            boolean stateChanged = d.state != state;
            d.state = state; d.percent = percent; d.speed = speed;
            publish();
            // Persist on state transitions and completion (not every speed tick).
            if (stateChanged || state == State.DONE) persist(d);
        });
    }

    private void fail(Download d, String m) {
        main.post(() -> { d.state = State.ERROR; d.error = m; publish(); persist(d); });
    }

    @MainThread
    private void publish() {
        downloads.setValue(new ArrayList<>(items.values()));
    }
}
