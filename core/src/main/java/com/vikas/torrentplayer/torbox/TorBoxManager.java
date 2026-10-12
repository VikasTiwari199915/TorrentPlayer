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
 * <p>Transfers run on a bounded pool; Room writes are serialized. UI snapshots
 * are published on the main thread, grouped by torrent without flattening files.
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
    private final ExecutorService transfers = Executors.newFixedThreadPool(3);
    private final ExecutorService api = Executors.newFixedThreadPool(2);
    private final OkHttpClient fileHttp = new OkHttpClient();

    private final Map<String, Download> items = new LinkedHashMap<>();
    private final MutableLiveData<List<Download>> downloads =
            new MutableLiveData<>(new ArrayList<>());

    private TorBoxManager() {}

    private boolean loaded;
    private boolean restored;
    private final List<Runnable> afterRestore = new ArrayList<>();
    private final MutableLiveData<List<TorBoxDownload>> torrentDownloads = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<TorBoxDownload>> torrentDownloads() { return torrentDownloads; }

    public void init(Context ctx) {
        if (appContext == null && ctx != null) appContext = ctx.getApplicationContext();
        if (appContext != null && !loaded) {
            loaded = true;
            io.execute(this::restoreFromDb);
        }
    }

    /** Restore history without automatically restarting paused, interrupted or missing files. */
    private void restoreFromDb() {
        List<com.vikas.torrentplayer.db.TorBoxEntity> rows;
        try { rows = dao().getAll(); }
        catch (Throwable t) { Log.e(TAG, "TorBox DB read failed", t); return; }
        if (rows == null) return;
        for (com.vikas.torrentplayer.db.TorBoxEntity e : rows) {
            Download d = new Download(e.key, e.title != null ? e.title : "Download");
            d.torrentId = e.torrentId;
            d.fileId = e.fileId;
            d.fileName = e.fileName;
            d.torrentName = e.torrentName;
            d.infoHash = e.infoHash;
            d.posterUrl = e.posterUrl;
            d.description = e.description;
            d.size = e.sizeBytes;
            d.percent = e.lastProgress;
            d.file = e.filePath != null ? new File(e.filePath) : null;
            d.downloaded = d.file != null && d.file.isFile() ? d.file.length() : 0;
            d.percent = d.size > 0 ? (int) Math.min(100, d.downloaded * 100 / d.size) : 0;
            State saved = e.lastState >= 0 && e.lastState < State.values().length
                    ? State.values()[e.lastState] : State.ERROR;
            boolean fileOk = d.file != null && d.file.isFile();
            if (saved == State.DONE) {
                d.state = fileOk && (d.size <= 0 || d.file.length() == d.size) ? State.DONE : State.ERROR;
                if (d.state == State.ERROR) d.error = "File missing";
            } else if (saved == State.PAUSED) {
                // Stay paused; the user resumes manually.
                d.state = State.PAUSED;
                d.pauseRequested.set(true);
            } else if (saved == State.DOWNLOADING) {
                d.state = State.PAUSED;
                d.pauseRequested.set(true);
            } else {
                d.state = State.ERROR;
                d.error = fileOk ? "Download interrupted; retry to resume" : "File missing";
            }
            main.post(() -> items.putIfAbsent(d.key, d));
        }
        main.post(() -> {
            restored = true;
            publish();
            for (Runnable action : afterRestore) action.run();
            afterRestore.clear();
        });
    }

    private com.vikas.torrentplayer.db.TorBoxDao dao() {
        return com.vikas.torrentplayer.db.AppDatabase.get(appContext).torBoxDao();
    }

    private void persist(Download d) {
        if (appContext == null) return;
        com.vikas.torrentplayer.db.TorBoxEntity e = new com.vikas.torrentplayer.db.TorBoxEntity();
        e.key = d.key;
        e.title = d.title;
        e.torrentName = d.torrentName;
        e.infoHash = d.infoHash;
        e.posterUrl = d.posterUrl;
        e.description = d.description;
        e.torrentId = d.torrentId;
        e.fileId = d.fileId;
        e.fileName = d.fileName;
        e.filePath = d.file != null ? d.file.getAbsolutePath() : null;
        e.sizeBytes = d.size;
        e.lastState = d.state.ordinal();
        e.lastProgress = d.percent;
        e.addedAt = System.currentTimeMillis();
        io.execute(() -> { try { dao().upsert(e); } catch (Throwable error) { Log.e(TAG, "Could not save TorBox download", error); } });
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
        public String torrentName, infoHash, posterUrl, description;
        public long downloaded;
        final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
        final AtomicBoolean running = new AtomicBoolean();
        volatile okhttp3.Call call;
        public long size;          // expected total bytes
        public State state = State.DOWNLOADING;
        public int percent;
        public long speed;
        public File file;
        public String error;
        final AtomicBoolean cancelled = new AtomicBoolean(false);    // remove: stop, retain local data
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
        api.execute(() -> {
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
        api.execute(() -> {
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
        api.execute(() -> {
            try {
                List<TorBoxClient.TbTorrent> list = client().listTorrents();
                main.post(() -> {
                    Runnable enrich = () -> enrichLibrary(list);
                    if (restored) enrich.run(); else afterRestore.add(enrich);
                    cb.onResult(list);
                });
            } catch (Throwable e) {
                main.post(() -> cb.onError(msg(e)));
            }
        });
    }

    /** Delete a torrent from the user's TorBox account. */
    public void deleteFromAccount(long torrentId, @NonNull Callback<Void> cb) {
        api.execute(() -> {
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
        if (existing != null) return existing;
        Download d = new Download(key, title);
        d.torrentId = torrentId;
        d.fileId = fileId;
        d.fileName = fileName;
        d.size = size;
        try {
            d.file = destinationFor(torrentId, fileId, fileName);
            for (Download other : items.values()) {
                if (d.file.equals(other.file)) {
                    d.file = null;
                    throw new IOException("Duplicate file path in torrent");
                }
            }
        }
        catch (IOException e) { d.state = State.ERROR; d.error = e.getMessage(); }
        items.put(key, d);
        publish();
        persist(d);
        if (d.state == State.DOWNLOADING) {
            com.vikas.torrentplayer.service.TorrentDownloadService.start(appContext);
            schedule(d);
        }
        return d;
    }

    @MainThread
    public void downloadTorrent(TorBoxClient.TbTorrent torrent, List<TorBoxClient.TbFile> files,
                                String poster, String description) {
        if (!restored) {
            afterRestore.add(() -> downloadTorrent(torrent, files, poster, description));
            return;
        }
        for (TorBoxClient.TbFile file : files) {
            Download d = downloadFile(torrent.id, file.id, file.name, file.size, torrent.name);
            d.torrentName = torrent.name;
            d.infoHash = torrent.hash;
            d.fileName = file.name;
            if (poster != null) d.posterUrl = poster;
            if (description != null) d.description = description;
            persist(d);
        }
        publish();
    }

    private void enrichLibrary(List<TorBoxClient.TbTorrent> library) {
        for (TorBoxClient.TbTorrent torrent : library) {
            for (TorBoxClient.TbFile file : torrent.files) {
                Download d = items.get("tb:" + torrent.id + ":" + file.id);
                if (d == null) continue;
                if (java.util.Objects.equals(d.torrentName, torrent.name)
                        && java.util.Objects.equals(d.infoHash, torrent.hash)
                        && java.util.Objects.equals(d.fileName, file.name)) continue;
                d.torrentName = torrent.name; d.infoHash = torrent.hash; d.fileName = file.name;
                // Existing flattened downloads keep their actual on-disk path.
                persist(d);
            }
        }
        publish();
    }

    public void pauseTorrent(long id) {
        for (Download d : new ArrayList<>(items.values())) if (d.torrentId == id) pause(d.key);
    }
    public void resumeTorrent(long id) {
        for (Download d : new ArrayList<>(items.values())) if (d.torrentId == id) resume(d.key);
    }
    public void removeTorrent(long id) {
        for (Download d : new ArrayList<>(items.values())) if (d.torrentId == id) remove(d.key);
    }

    private void interrupt(Download d) {
        d.generation.incrementAndGet();
        okhttp3.Call call = d.call;
        if (call != null) call.cancel();
    }

    private void schedule(Download d) {
        if (!d.running.compareAndSet(false, true)) return;
        int generation = d.generation.get();
        // Queue behind Room writes so a process interruption cannot lose a new transfer.
        io.execute(() -> transfers.execute(() -> {
            try { runFileDownload(d, generation); }
            finally { main.post(() -> {
                d.running.set(false);
                if (items.get(d.key) == d && d.state == State.DOWNLOADING
                        && d.generation.get() != generation) schedule(d);
            }); }
        }));
    }

    private boolean stopped(Download d, int generation) {
        return d.shouldStop() || d.generation.get() != generation;
    }

    /** Cancel + forget every TorBox download (used by the cache cleaner). The
     *  DB rows and on-disk files are wiped by {@code CacheCleaner} itself. */
    public void clearAll() {
        for (Download d : items.values()) { d.cancelled.set(true); interrupt(d); }
        items.clear();
        main.post(this::publish);
    }

    @MainThread
    public void pause(String key) {
        Download d = items.get(key);
        if (d == null || d.state != State.DOWNLOADING) return;
        d.pauseRequested.set(true);    // worker stops at the next chunk, keeps the file
        interrupt(d);
        d.state = State.PAUSED;
        d.speed = 0;
        publish();
        persist(d);
    }

    @MainThread
    public void resume(String key) {
        Download d = items.get(key);
        if (d == null || (d.state != State.PAUSED && d.state != State.ERROR) || d.file == null) return;
        d.generation.incrementAndGet();
        d.pauseRequested.set(false);
        d.error = null;
        d.state = State.DOWNLOADING;
        publish();
        persist(d);
        com.vikas.torrentplayer.service.TorrentDownloadService.start(appContext);
        schedule(d);
    }

    @MainThread
    public void remove(String key) {
        Download d = items.remove(key);
        if (d != null) { d.cancelled.set(true); interrupt(d); }
        publish();
        io.execute(() -> { try { dao().deleteByKey(key); } catch (Throwable ignored) {} });
    }

    // ------------------------------------------------------------------
    // Local download worker
    // ------------------------------------------------------------------

    private void runFileDownload(Download d, int generation) {
        if (stopped(d, generation)) return;
        try {
            String url = client().requestDownloadUrl(d.torrentId, d.fileId);
            File out = d.file != null ? d.file : destinationFor(d.torrentId, d.fileId, d.fileName);
            d.file = out;
            if (stopped(d, generation)) return;
            downloadToFile(d, generation, url, out);
            if (stopped(d, generation)) return;
            if (d.size > 0 && out.length() != d.size) throw new IOException("Incomplete download; retry to resume");
            update(d, generation, State.DONE, out.length(), 0);
            notifyMediaScanner(out);
            Log.i(TAG, "TorBox download complete: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Log.e(TAG, "TorBox download failed", e);
            if (!stopped(d, generation)) main.post(() -> {
                if (items.get(d.key) != d || stopped(d, generation)) return;
                d.state = State.ERROR; d.error = msg(e); d.speed = 0; publish(); persist(d);
            });
        }
    }

    /** Streams the file to disk, resuming via a Range request if a partial file
     *  already exists. The file grows sequentially, which is what lets a player
     *  read the head while the rest is still arriving. */
    private void downloadToFile(Download d, int generation, String url, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("Could not create download folder");
        long existing = out.exists() ? out.length() : 0;
        if (out.isFile() && existing == d.size) return;
        boolean resume = existing > 0 && (d.size <= 0 || existing < d.size);

        Request.Builder rb = new Request.Builder().url(url).get();
        if (resume) rb.header("Range", "bytes=" + existing + "-");
        d.call = fileHttp.newCall(rb.build());
        if (stopped(d, generation)) return;
        try (Response resp = d.call.execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("empty body");
            boolean append = resume && resp.code() == 206;  // server honoured the range
            if (resp.code() == 206 && (!append || resp.header("Content-Range") == null
                    || !resp.header("Content-Range").startsWith("bytes " + existing + "-")))
                throw new IOException("Invalid download range");
            long done = append ? existing : 0;
            long lastBytes = done, lastTs = System.currentTimeMillis();
            byte[] buf = new byte[256 * 1024];
            try (InputStream in = body.byteStream();
                 RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
                if (append) raf.seek(existing); else raf.setLength(0);
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (stopped(d, generation)) return;
                    raf.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastTs >= 700) {
                        long speed = (done - lastBytes) * 1000 / Math.max(1, now - lastTs);
                        update(d, generation, State.DOWNLOADING, done, speed);
                        lastTs = now; lastBytes = done;
                    }
                }
            }
        }
    }

    private File destinationFor(long torrentId, int fileId, String fileName) throws IOException {
        File base = TorrentManager.get().getSaveDir();
        if (base == null) base = appContext.getExternalFilesDir(null);
        if (base == null) throw new IOException("Download storage unavailable");
        return TorBoxPaths.resolve(new File(base, "TorBox/" + torrentId), fileName);
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

    private void update(Download d, int generation, State state, long downloaded, long speed) {
        main.post(() -> {
            // A late progress tick must not clobber a pause the user just made.
            if (items.get(d.key) != d || stopped(d, generation)) return;
            boolean stateChanged = d.state != state;
            d.downloaded = downloaded;
            d.state = state; d.percent = d.size > 0 ? (int) Math.min(100, downloaded * 100 / d.size) : state == State.DONE ? 100 : 0; d.speed = speed;
            publish();
            // Persist on state transitions and completion (not every speed tick).
            if (stateChanged || state == State.DONE) persist(d);
        });
    }

    @MainThread
    private void publish() {
        List<Download> snapshot = new ArrayList<>();
        for (Download item : items.values()) {
            Download copy = new Download(item.key, item.title);
            copy.torrentId = item.torrentId;
            copy.fileId = item.fileId;
            copy.fileName = item.fileName;
            copy.torrentName = item.torrentName;
            copy.posterUrl = item.posterUrl;
            copy.infoHash = item.infoHash;
            copy.description = item.description;
            copy.downloaded = item.downloaded;
            copy.size = item.size;
            copy.state = item.state;
            copy.percent = item.percent;
            copy.speed = item.speed;
            copy.file = item.file;
            copy.error = item.error;
            snapshot.add(copy);
        }
        downloads.setValue(snapshot);
        torrentDownloads.setValue(TorBoxDownload.group(snapshot));
    }
}
