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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Drives the "Download via TorBox" flow, kept completely separate from the
 * libtorrent engine so it can't destabilise it:
 *
 * <ol>
 *   <li>add the magnet to TorBox ({@link TorBoxClient#addMagnet})</li>
 *   <li>poll until TorBox has the torrent on its servers</li>
 *   <li>resolve a direct HTTPS URL for the largest (video) file</li>
 *   <li>stream that URL to the app's save folder at full speed</li>
 * </ol>
 *
 * Progress is exposed as LiveData so the UI can render it; the actual work runs
 * on a single background thread (one download at a time — easy on weak boxes).
 */
public final class TorBoxManager {

    private static final String TAG = "TorBoxManager";
    private static final long POLL_INTERVAL_MS = 4000L;
    private static final long REMOTE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 min for TorBox to fetch

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
        if (appContext == null) appContext = ctx.getApplicationContext();
    }

    public LiveData<List<Download>> downloads() { return downloads; }

    public enum State { ADDING, REMOTE, DOWNLOADING, DONE, ERROR }

    /** One TorBox-backed download. Mutated on the main thread only. */
    public static final class Download {
        public final String key;       // infoHash (lower) or magnet
        public final String title;
        public long torrentId;
        public State state = State.ADDING;
        public int percent;            // overall 0..100 for the current phase
        public long speed;             // local download bytes/s
        public File file;              // set when DONE
        public String error;
        public final AtomicBoolean cancelled = new AtomicBoolean(false);

        Download(String key, String title) { this.key = key; this.title = title; }
    }

    /**
     * Begin a TorBox download for the given magnet. Safe to call from the main
     * thread. If a download with the same key already exists it's returned as-is.
     */
    @MainThread
    public Download startDownload(@NonNull String magnet, @NonNull String infoHash,
                                  @NonNull String title) {
        String key = (infoHash != null && !infoHash.isEmpty())
                ? infoHash.toLowerCase(Locale.US) : magnet;
        Download existing = items.get(key);
        if (existing != null
                && existing.state != State.ERROR) {
            return existing;
        }
        Download d = new Download(key, title);
        items.put(key, d);
        publish();

        final String apiKey = new PrefsManager(appContext).getTorBoxKey();
        io.execute(() -> runDownload(d, magnet, apiKey));
        return d;
    }

    @MainThread
    public void remove(String key) {
        Download d = items.remove(key);
        if (d != null) d.cancelled.set(true);
        publish();
    }

    // ------------------------------------------------------------------

    private void runDownload(Download d, String magnet, String apiKey) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                fail(d, "No TorBox API key set");
                return;
            }
            TorBoxClient client = new TorBoxClient(apiKey);

            update(d, State.ADDING, 0, 0);
            long torrentId = client.addMagnet(magnet);
            d.torrentId = torrentId;

            // Wait for TorBox to have the content on its servers.
            TorBoxClient.TbTorrent t = null;
            long deadline = System.currentTimeMillis() + REMOTE_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (d.cancelled.get()) return;
                t = client.getTorrent(torrentId);
                if (t != null && t.finished) break;
                int pct = t != null ? (int) Math.round(t.progress * 100) : 0;
                update(d, State.REMOTE, pct, t != null ? t.downloadSpeed : 0);
                sleep(POLL_INTERVAL_MS);
            }
            if (t == null || !t.finished) {
                fail(d, "TorBox timed out preparing the torrent");
                return;
            }

            // Pick the largest file (the feature presentation / main video).
            TorBoxClient.TbFile target = largest(t.files);
            if (target == null) {
                fail(d, "TorBox reported no files");
                return;
            }

            String url = client.requestDownloadUrl(torrentId, target.id);
            File out = destinationFor(t.name, target.name);
            update(d, State.DOWNLOADING, 0, 0);
            downloadToFile(d, url, out, target.size);
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
            fail(d, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void downloadToFile(Download d, String url, File out, long expectedSize)
            throws IOException {
        //noinspection ResultOfMethodCallIgnored
        out.getParentFile().mkdirs();
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = fileHttp.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("empty body");
            long total = body.contentLength() > 0 ? body.contentLength() : expectedSize;
            long done = 0;
            long lastTs = System.currentTimeMillis();
            long lastBytes = 0;
            byte[] buf = new byte[256 * 1024];
            try (InputStream in = body.byteStream();
                 OutputStream os = new RandomAccessFileOutputStream(out)) {
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

    /** Minimal OutputStream over a fresh file (truncating). */
    private static final class RandomAccessFileOutputStream extends OutputStream {
        private final RandomAccessFile raf;
        RandomAccessFileOutputStream(File f) throws IOException {
            this.raf = new RandomAccessFile(f, "rw");
            this.raf.setLength(0);
        }
        @Override public void write(int b) throws IOException { raf.write(b); }
        @Override public void write(@NonNull byte[] b, int off, int len) throws IOException {
            raf.write(b, off, len);
        }
        @Override public void close() throws IOException { raf.close(); }
    }

    private File destinationFor(String torrentName, String fileName) {
        File base = TorrentManager.get().getSaveDir();
        if (base == null) base = appContext.getExternalFilesDir(null);
        File dir = new File(base, "TorBox");
        // Use just the leaf file name (TorBox file names can contain subpaths).
        String leaf = fileName;
        int slash = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (slash >= 0 && slash < leaf.length() - 1) leaf = leaf.substring(slash + 1);
        if (leaf.isEmpty()) leaf = sanitize(torrentName) + ".mp4";
        return new File(dir, leaf);
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "torbox";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static TorBoxClient.TbFile largest(List<TorBoxClient.TbFile> files) {
        TorBoxClient.TbFile best = null;
        for (TorBoxClient.TbFile f : files) {
            if (best == null || f.size > best.size) best = f;
        }
        return best;
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

    private void update(Download d, State state, int percent, long speed) {
        main.post(() -> {
            d.state = state;
            d.percent = percent;
            d.speed = speed;
            publish();
        });
    }

    private void fail(Download d, String msg) {
        main.post(() -> {
            d.state = State.ERROR;
            d.error = msg;
            publish();
        });
    }

    @MainThread
    private void publish() {
        downloads.setValue(new ArrayList<>(items.values()));
    }
}
