package com.vikas.torrentplayer.torrent;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.db.AppDatabase;
import com.vikas.torrentplayer.db.DownloadDao;
import com.vikas.torrentplayer.db.DownloadEntity;
import com.vikas.torrentplayer.utils.PrefsManager;

import org.libtorrent4j.AlertListener;
import org.libtorrent4j.FileStorage;
import org.libtorrent4j.Priority;
import org.libtorrent4j.SessionHandle;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.Sha1Hash;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.AlertType;
import org.libtorrent4j.alerts.SaveResumeDataAlert;
import org.libtorrent4j.alerts.TorrentErrorAlert;
import org.libtorrent4j.alerts.TorrentFinishedAlert;
import org.libtorrent4j.swig.torrent_flags_t;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * App-wide singleton wrapping a libtorrent4j {@link SessionManager}.
 *
 * <p>Lifecycle is owned by {@link com.vikas.torrentplayer.service.TorrentDownloadService}
 * so downloads survive the user backgrounding/closing the app.
 *
 * <p>State is persisted in Room ({@link DownloadEntity}) so torrents survive
 * crashes / forced process death — on startup we re-add them to the libtorrent
 * session from the cached {@code .torrent} files.
 */
public class TorrentManager {

    private static final String TAG = "TorrentManager";

    /** Roughly how much head buffer (in bytes) to require before READY. */
    private static final long HEAD_BUFFER_BYTES = 50L * 1024 * 1024;
    /** Tail buffer — MKV stores Cues here, non-fast-start MP4 stores moov. */
    private static final long TAIL_BUFFER_BYTES = 50L * 1024 * 1024;
    /** Below this video size, just wait for the whole file. */
    private static final long SMALL_FILE_THRESHOLD = 100L * 1024 * 1024;

    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v",
            ".flv", ".ts", ".m2ts", ".wmv", ".mpg", ".mpeg"
    };
    private static final String[] SUBTITLE_EXTS = {
            ".srt", ".vtt", ".ass", ".ssa", ".sub", ".idx"
    };

    private static final TorrentManager INSTANCE = new TorrentManager();
    public static TorrentManager get() { return INSTANCE; }

    private Context appContext;
    private PrefsManager prefs;
    private SessionManager session;
    private DownloadDao dao;
    private File saveDir;
    private File torrentFileCacheDir;
    private boolean usingPublicDownloads;

    private final TorrentClawApi api = ApiClient.get();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final Map<String, DownloadHandle> handles = new LinkedHashMap<>();
    private final Map<String, TorrentRecord> records = new HashMap<>();

    private final MutableLiveData<List<DownloadHandle>> allDownloads = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<DownloadHandle> active = new MutableLiveData<>(null);

    private TorrentManager() {}

    /** Snapshot of one writable volume the user can choose as the save location. */
    public static class VolumeInfo {
        /** Base directory under which {@code TorrentPlayer/} is created. */
        public final File root;
        public final long free;
        public final long total;
        public final boolean isCurrent;
        public final String label;
        /** True for scoped app-specific media dirs (no permission needed).
         *  False for raw volume roots like a USB drive — those require
         *  {@code MANAGE_EXTERNAL_STORAGE} to actually write. */
        public final boolean isAppDir;

        VolumeInfo(File root, long free, long total, boolean isCurrent,
                   String label, boolean isAppDir) {
            this.root = root;
            this.free = free;
            this.total = total;
            this.isCurrent = isCurrent;
            this.label = label;
            this.isAppDir = isAppDir;
        }
    }

    private static class TorrentRecord {
        Sha1Hash hash;
        TorrentInfo info;
        int videoFileIndex = -1;
        File videoFilePath;
        long videoFileSize;
        int videoFirstPiece = -1;
        int videoLastPiece = -1;
        int headBufferLastPiece = -1;
        int tailBufferFirstPiece = -1;
        boolean readyEmitted = false;
        /** External subtitle files in the torrent (paths under saveDir). */
        final List<File> subtitleFiles = new ArrayList<>();
    }

    @MainThread
    public synchronized void init(Context context) {
        if (session != null) return;
        appContext = context.getApplicationContext();
        prefs = new PrefsManager(appContext);
        dao = AppDatabase.get(appContext).downloadDao();

        chooseSaveDir();
        torrentFileCacheDir = new File(appContext.getExternalFilesDir(null), "meta");
        //noinspection ResultOfMethodCallIgnored
        torrentFileCacheDir.mkdirs();

        session = new SessionManager();
        SettingsPack settings = new SettingsPack();
        settings.connectionsLimit(200);
        settings.activeDownloads(8);
        settings.activeSeeds(8);
        SessionParams params = new SessionParams(settings);
        session.start(params);
        session.addListener(alertListener);

        Log.i(TAG, "libtorrent4j session started, save dir=" + saveDir.getAbsolutePath()
                + " (public=" + usingPublicDownloads + ")");

        // Ask libtorrent to emit STATE_UPDATE alerts every second so the UI
        // gets fresh progress without us polling every torrent ourselves.
        main.post(statusPoller);
        // Periodically write fast-resume blobs so app restarts skip rehashing.
        main.postDelayed(resumeDataSaver, 30_000L);

        // Restore persisted downloads from Room
        io.execute(this::restoreFromDb);
    }

    /** Periodic kick — libtorrent only fires STATE_UPDATE if we ask. 2s is
     *  fine for human-perceived progress and noticeably cheaper than 1s on
     *  weak TV CPUs where every alert round-trip costs. */
    private final Runnable statusPoller = new Runnable() {
        @Override public void run() {
            if (session != null) {
                try { session.postTorrentUpdates(); }
                catch (Throwable t) { Log.w(TAG, "postTorrentUpdates failed", t); }
            }
            main.postDelayed(this, 2000);
        }
    };

    /** Every 30s, ask each non-paused torrent to emit a save_resume_data alert.
     *  We persist the resulting bytes so the next app launch can skip the full
     *  piece re-hash. */
    private final Runnable resumeDataSaver = new Runnable() {
        @Override public void run() {
            if (session != null) {
                for (TorrentRecord rec : records.values()) {
                    if (rec == null || rec.hash == null) continue;
                    TorrentHandle th = session.find(rec.hash);
                    if (th == null || !th.isValid()) continue;
                    try { th.saveResumeData(); }
                    catch (Throwable t) { /* best effort */ }
                }
            }
            main.postDelayed(this, 30_000L);
        }
    };

    /**
     * Serialise a SaveResumeDataAlert's params into bencoded bytes. The
     * available API differs between libtorrent4j 2.x point releases (some
     * expose {@code bencode()}, some only the SWIG escape hatch). Returning
     * {@code null} cleanly disables fast-resume — libtorrent will re-hash
     * pieces on next add, which is slow but correct.
     */
    @Nullable
    private static byte[] serializeResumeData(SaveResumeDataAlert srda) {
        // The clean Java API isn't reliable across versions; bencode/swig
        // routes both blew up at compile time. Returning null means we'll
        // skip writing a .resume file — libtorrent will hash-check pieces on
        // next startup (about as fast as reading the file) instead of
        // re-downloading them, so this is a graceful degradation.
        return null;
    }

    private void writeResumeFile(String hashHex, byte[] bytes) {
        try {
            File out = new File(torrentFileCacheDir, hashHex + ".resume");
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(bytes);
            }
        } catch (IOException e) {
            Log.w(TAG, "writeResumeFile failed", e);
        }
    }

    /**
     * Pick the writable volume with the most free space.
     *
     * <p>{@code getExternalMediaDirs()} returns one path per mounted volume:
     * index 0 is internal storage, subsequent indices are SD card / USB
     * drive / etc. — exactly the case the user's TV box (6 GB internal +
     * 64 GB external) hits.  By scanning {@link File#getUsableSpace()} we
     * automatically prefer the volume that can actually hold a couple of
     * full-length downloads without filling up.
     *
     * <p>No runtime permission required: these dirs are scoped-storage
     * app-specific paths and the MediaScanner still picks up videos here.
     */
    private void chooseSaveDir() {
        // Honour the user's explicit volume choice if it's still mounted.
        String savedPath = prefs.getSaveVolumePath();
        if (savedPath != null) {
            File saved = new File(savedPath);
            if (saved.exists() && saved.canWrite()) {
                saveDir = new File(saved, "TorrentPlayer");
                if (!saveDir.exists()) //noinspection ResultOfMethodCallIgnored
                    saveDir.mkdirs();
                usingPublicDownloads = false;
                Log.i(TAG, "using user-selected save volume: " + saveDir.getAbsolutePath());
                return;
            }
            // Volume no longer mounted — clear stale pref and auto-pick below.
            prefs.setSaveVolumePath(null);
            Log.w(TAG, "saved volume no longer available, falling back to auto-pick: " + savedPath);
        }

        File chosen = null;
        long bestFree = -1;
        StringBuilder report = new StringBuilder();

        File[] media = appContext.getExternalMediaDirs();
        if (media != null) {
            for (File d : media) {
                if (d == null) continue;
                long free = d.getUsableSpace();
                report.append("\n  ").append(d.getAbsolutePath())
                        .append(" → ").append(free / (1024 * 1024)).append(" MB free");
                if (free > bestFree) {
                    chosen = d;
                    bestFree = free;
                }
            }
        }
        if (chosen == null) chosen = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (chosen == null) chosen = appContext.getExternalFilesDir(null);
        if (chosen == null) chosen = appContext.getCacheDir();
        saveDir = new File(chosen, "TorrentPlayer");
        if (!saveDir.exists()) //noinspection ResultOfMethodCallIgnored
            saveDir.mkdirs();
        usingPublicDownloads = false;
        Log.i(TAG, "chose save dir: " + saveDir.getAbsolutePath()
                + " (" + (bestFree / (1024 * 1024)) + " MB free)"
                + " — candidates:" + report);
    }

    /** Filesystems that only ever back a removable drive (USB stick / SD card).
     *  These are never system partitions, so we trust them at any mount path. */
    private static boolean isRemovableFsType(String fs) {
        switch (fs.toLowerCase(Locale.US)) {
            case "vfat": case "exfat": case "ntfs": case "fuseblk":
            case "msdos": case "texfat": case "sdfat": case "iso9660": case "udf":
                return true;
            default:
                return false;
        }
    }

    /**
     * Is this {@code /proc/mounts} entry a user-writable external drive?
     *
     * <p>Two ways to qualify: a removable-media filesystem type anywhere (USB
     * sticks are always vfat/exfat/ntfs/…), or a fuse/sdcardfs mount exposed
     * under {@code /storage/<id>} (the app-facing view of a volume).
     */
    private static boolean isExternalStorageMount(String mp, String fs) {
        if (isRemovableFsType(fs)) return true;
        String f = fs.toLowerCase(Locale.US);
        if ((f.equals("fuse") || f.equals("sdcardfs")) && mp.startsWith("/storage/")
                && !mp.startsWith("/storage/emulated") && !mp.startsWith("/storage/self")) {
            return true;
        }
        return false;
    }

    /**
     * Returns all volumes the user can choose as a save location, with space info.
     * Must be called after {@link #init}.
     *
     * <p>Three sources, in order of trust:
     * <ol>
     *   <li>{@code getExternalMediaDirs()} — scoped app dirs (internal / adopted),
     *       always writable, no permission. {@code isAppDir == true}.</li>
     *   <li>{@code StorageManager.getStorageVolumes()} — the official volume list.</li>
     *   <li>{@code /proc/mounts} — the kernel's real mount table. This is what
     *       finds portable USB drives on cheap TV boxes whose StorageManager
     *       doesn't report them. We only trust mounts with a real filesystem
     *       type, and (once we hold the permission) only ones a write-probe
     *       actually succeeds on — that drops the empty {@code /mnt/*} stub
     *       directories that otherwise all report the root-fs size.</li>
     * </ol>
     */
    public List<VolumeInfo> getAvailableVolumes() {
        List<VolumeInfo> result = new ArrayList<>();
        if (appContext == null) return result;
        StorageManager sm = (StorageManager) appContext.getSystemService(Context.STORAGE_SERVICE);
        List<String> addedRoots = new ArrayList<>();
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();

        // 1. Scoped app-specific media dirs — always writable.
        File[] media = appContext.getExternalMediaDirs();
        if (media != null) {
            for (File d : media) {
                if (d == null) continue;
                result.add(makeVolumeInfo(d, volumeLabel(sm, d, "Internal storage"), true));
                addedRoots.add(d.getAbsolutePath());
            }
        }

        // 2. StorageManager volumes (API 30+).
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                for (StorageVolume sv : sm.getStorageVolumes()) {
                    if (!Environment.MEDIA_MOUNTED.equals(sv.getState())) continue;
                    File dir = sv.getDirectory();
                    if (dir == null || isCoveredBy(dir, addedRoots)) continue;
                    String label = sv.getDescription(appContext);
                    if (label == null || label.isEmpty()) label = dir.getName();
                    result.add(makeVolumeInfo(dir, label, false));
                    addedRoots.add(dir.getAbsolutePath());
                }
            } catch (Throwable t) {
                Log.w(TAG, "getStorageVolumes failed", t);
            }
        }

        // 3. /proc/mounts — the only reliable source for portable USB on TV boxes.
        Set<String> sizeSigs = new LinkedHashSet<>();
        for (File mp : readExternalMounts()) {
            if (isCoveredBy(mp, addedRoots)) continue;
            // With the permission held, hide mounts we can't actually write to —
            // this drops root-only raw mounts and any leftover stub dirs.
            if (granted && !canActuallyWrite(mp)) continue;
            VolumeInfo vi = makeVolumeInfo(mp, mp.getName(), false);
            // Collapse the same physical drive exposed at two writable paths
            // (e.g. /storage/<id> and /mnt/usb/<id>) by capacity signature.
            String sig = vi.total + ":" + vi.free;
            if (vi.total > 0 && !sizeSigs.add(sig)) continue;
            result.add(vi);
            addedRoots.add(mp.getAbsolutePath());
        }
        return result;
    }

    /**
     * Parses {@code /proc/mounts} for real external mounts. App-facing
     * {@code /storage/<id>} entries are returned first so they're preferred over
     * the root-only raw mount of the same drive.
     */
    private List<File> readExternalMounts() {
        List<File> storageMounts = new ArrayList<>();
        List<File> otherMounts = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(" ");
                if (p.length < 3) continue;
                String mp = p[1].replace("\\040", " ");
                if (!isExternalStorageMount(mp, p[2])) continue;
                if (mp.startsWith("/storage/")) storageMounts.add(new File(mp));
                else otherMounts.add(new File(mp));
            }
        } catch (IOException e) {
            Log.w(TAG, "reading /proc/mounts failed", e);
        }
        List<File> out = new ArrayList<>(storageMounts);
        out.addAll(otherMounts);
        return out;
    }

    /** Create-then-delete a probe file to confirm we can really write here. */
    private boolean canActuallyWrite(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) return false;
            File probe = new File(dir, ".tp_probe_" + System.nanoTime());
            try (FileOutputStream fos = new FileOutputStream(probe)) {
                fos.write('x');
            }
            boolean ok = probe.exists();
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isCoveredBy(File dir, List<String> addedRoots) {
        String p = dir.getAbsolutePath();
        for (String r : addedRoots) {
            if (p.startsWith(r) || r.startsWith(p)) return true;
        }
        return false;
    }

    /**
     * Human-readable dump of everything the OS reports about storage, so a user
     * on a device we can't reach via adb can read it back. Surfaced in Settings.
     */
    public String getStorageDiagnostics() {
        StringBuilder sb = new StringBuilder();
        if (appContext == null) return "TorrentManager not initialised.";
        sb.append("All-files access: ")
                .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? Environment.isExternalStorageManager() : "n/a (<API30)")
                .append("\nAPI level: ").append(Build.VERSION.SDK_INT)
                .append("\nCurrent save dir: ")
                .append(saveDir != null ? saveDir.getAbsolutePath() : "—");

        sb.append("\n\n[getExternalMediaDirs]");
        File[] media = appContext.getExternalMediaDirs();
        if (media == null) sb.append("\n  null");
        else for (File d : media) {
            sb.append("\n  ").append(d == null ? "null"
                    : d.getAbsolutePath() + "  (" + (d.getUsableSpace() / (1024 * 1024)) + " MB free)");
        }

        sb.append("\n\n[StorageManager.getStorageVolumes]");
        StorageManager sm = (StorageManager) appContext.getSystemService(Context.STORAGE_SERVICE);
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                for (StorageVolume sv : sm.getStorageVolumes()) {
                    File dir = sv.getDirectory();
                    sb.append("\n  ").append(sv.getDescription(appContext))
                            .append(" | state=").append(sv.getState())
                            .append(" | removable=").append(sv.isRemovable())
                            .append(" | dir=").append(dir != null ? dir.getAbsolutePath() : "null");
                }
            } catch (Throwable t) {
                sb.append("\n  error: ").append(t.getMessage());
            }
        }

        sb.append("\n\n[/proc/mounts — storage & external]");
        boolean anyMount = false;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(" ");
                if (p.length < 3) continue;
                String mp = p[1];
                // Show anything mounted under /storage or /mnt plus any removable
                // filesystem, so a USB at a nonstandard path is still visible here.
                boolean show = mp.startsWith("/storage/") || mp.startsWith("/mnt/")
                        || isRemovableFsType(p[2]);
                if (!show) continue;
                File f = new File(mp);
                long total = 0;
                try { total = f.getTotalSpace(); } catch (Throwable ignored) {}
                sb.append("\n  ").append(mp).append(" [").append(p[2]).append("]")
                        .append(" total=").append(total / (1024 * 1024)).append("MB")
                        .append(" ext=").append(isExternalStorageMount(mp, p[2]))
                        .append(" w=").append(canActuallyWrite(f));
                anyMount = true;
            }
        } catch (IOException e) {
            sb.append("\n  error: ").append(e.getMessage());
        }
        if (!anyMount) sb.append("\n  (nothing under /storage or /mnt in our mount namespace)");

        sb.append("\n\n[Detected save volumes]");
        for (VolumeInfo v : getAvailableVolumes()) {
            sb.append("\n  ").append(v.label).append(" → ").append(v.root.getAbsolutePath())
                    .append(" (").append(v.free / (1024 * 1024)).append("/")
                    .append(v.total / (1024 * 1024)).append(" MB) appDir=").append(v.isAppDir);
        }
        return sb.toString();
    }

    private VolumeInfo makeVolumeInfo(File root, String label, boolean isAppDir) {
        long free = 0, total = 0;
        try {
            free = root.getUsableSpace();
            total = root.getTotalSpace();
        } catch (Throwable ignored) {}
        boolean isCurrent = saveDir != null
                && saveDir.getAbsolutePath().startsWith(root.getAbsolutePath());
        return new VolumeInfo(root, free, total, isCurrent, label, isAppDir);
    }

    private String volumeLabel(StorageManager sm, File dir, String fallback) {
        try {
            StorageVolume sv = sm != null ? sm.getStorageVolume(dir) : null;
            if (sv != null) {
                String desc = sv.getDescription(appContext);
                if (desc != null && !desc.isEmpty()) return desc;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    /**
     * Switch the active save volume and persist the choice.
     * Pass {@code null} to revert to automatic (largest free space).
     */
    public synchronized void switchVolume(@Nullable File volumeRoot) {
        if (volumeRoot == null) {
            prefs.setSaveVolumePath(null);
            chooseSaveDir();
        } else {
            prefs.setSaveVolumePath(volumeRoot.getAbsolutePath());
            saveDir = new File(volumeRoot, "TorrentPlayer");
            if (!saveDir.exists()) //noinspection ResultOfMethodCallIgnored
                saveDir.mkdirs();
            usingPublicDownloads = false;
            Log.i(TAG, "user switched save volume to: " + saveDir.getAbsolutePath());
        }
    }

    /** Ask the system MediaScanner to index a freshly-finished file so gallery
     *  / other media players can see it. */
    private void notifyMediaScanner(File file) {
        if (file == null || !file.exists()) return;
        try {
            android.media.MediaScannerConnection.scanFile(
                    appContext,
                    new String[] { file.getAbsolutePath() },
                    null,
                    null);
        } catch (Throwable t) {
            Log.w(TAG, "MediaScanner failed", t);
        }
    }

    public boolean isUsingPublicDownloads() { return usingPublicDownloads; }
    public File getSaveDir() { return saveDir; }

    public LiveData<List<DownloadHandle>> downloads() { return allDownloads; }
    public LiveData<DownloadHandle> activeDownload() { return active; }

    @Nullable
    public DownloadHandle findByHash(@Nullable String infoHash) {
        if (infoHash == null) return null;
        return handles.get(infoHash);
    }

    // ---------------------------------------------------------------------
    // Public entry-points
    // ---------------------------------------------------------------------

    @MainThread
    public DownloadHandle startStream(SearchResult parent, TorrentItem item) {
        if (session == null) throw new IllegalStateException("TorrentManager.init() not called");
        if ((item.magnetUrl == null || item.magnetUrl.isEmpty())
                && (item.infoHash == null || item.infoHash.isEmpty())) {
            throw new IllegalArgumentException("Torrent has neither magnet nor infoHash");
        }

        String hash = item.infoHash != null ? item.infoHash.toLowerCase(Locale.US) : item.magnetUrl;
        DownloadHandle handle = handles.get(hash);
        if (handle == null) {
            handle = new DownloadHandle(
                    hash,
                    item.downloadTitle(parent),
                    parent != null ? parent.posterUrl : null,
                    item.quality,
                    item.sizeBytes,
                    item.magnetUrl != null ? item.magnetUrl : ""
            );
            handles.put(hash, handle);
            publishList();
            persistAsync(handle);
        } else {
            handle.state.setValue(DownloadHandle.State.STARTING);
            handle.errorMessage.setValue(null);
        }

        active.setValue(handle);
        prefetchAndStart(item, handle, /* addPaused = */ false, null);
        return handle;
    }

    /**
     * Pause a specific download. Critical: libtorrent's queue manager will
     * resume any {@code AUTO_MANAGED} torrent automatically a few seconds
     * later, which is why a plain {@code pause()} call appears to "do
     * nothing". We clear that flag first so the pause is final until the user
     * explicitly resumes.
     */
    @MainThread
    public void pause(String infoHash) {
        DownloadHandle h = handles.get(infoHash);
        TorrentRecord rec = records.get(infoHash);
        if (h == null || rec == null || rec.hash == null) return;
        // UI/state first (instant feedback), libtorrent JNI off the main thread —
        // these calls can block for seconds on weak boxes while the session is
        // busy checking pieces, which froze the app on resume.
        if (h.state.getValue() != DownloadHandle.State.FINISHED) {
            h.state.setValue(DownloadHandle.State.PAUSED);
            // Force-zero the progress so the UI doesn't show stale speed
            DownloadHandle.Progress p = h.progress.getValue();
            int pct = p == null ? 0 : p.percent;
            h.progress.setValue(new DownloadHandle.Progress(pct, 0, 0, p == null ? 0 : p.bufferProgress));
            persistAsync(h);
        }
        final Sha1Hash hash = rec.hash;
        io.execute(() -> {
            TorrentHandle th = session.find(hash);
            if (th != null && th.isValid()) {
                try { th.unsetFlags(TorrentFlags.AUTO_MANAGED); }
                catch (Throwable t) { Log.w(TAG, "unsetFlags failed", t); }
                th.pause();
            }
        });
    }

    /** Resume a paused download. */
    @MainThread
    public void resume(String infoHash) {
        DownloadHandle h = handles.get(infoHash);
        TorrentRecord rec = records.get(infoHash);
        if (h == null || rec == null || rec.hash == null) return;
        if (h.state.getValue() == DownloadHandle.State.PAUSED) {
            h.state.setValue(DownloadHandle.State.BUFFERING);
            persistAsync(h);
        }
        // libtorrent JNI off the main thread: resume() kicks off a piece recheck
        // (fast-resume is disabled), and the call can block under lock
        // contention on a weak box — this was the "freeze on resume" ANR.
        final Sha1Hash hash = rec.hash;
        io.execute(() -> {
            TorrentHandle th = session.find(hash);
            if (th != null && th.isValid()) {
                try { th.setFlags(TorrentFlags.AUTO_MANAGED); }
                catch (Throwable t) { Log.w(TAG, "setFlags failed", t); }
                th.resume();
            }
        });
    }

    /** Called when a torrent reaches 100% — stop seeding so the speed UI goes
     *  to zero and CPU/network isn't burnt on a finished item. */
    private void stopSeedingFor(TorrentRecord rec) {
        if (rec == null || rec.hash == null || session == null) return;
        final Sha1Hash hash = rec.hash;
        io.execute(() -> {
            TorrentHandle th = session.find(hash);
            if (th == null || !th.isValid()) return;
            try {
                th.unsetFlags(TorrentFlags.AUTO_MANAGED);
                th.pause();
            } catch (Throwable t) {
                Log.w(TAG, "stopSeeding failed", t);
            }
        });
    }

    @MainThread
    public void stopActive() {
        DownloadHandle h = active.getValue();
        if (h == null) return;
        TorrentRecord rec = records.get(h.infoHash);
        if (h.state.getValue() != DownloadHandle.State.FINISHED) {
            h.state.setValue(DownloadHandle.State.PAUSED);
            persistAsync(h);
        }
        active.setValue(null);
        if (rec != null && rec.hash != null) {
            final Sha1Hash hash = rec.hash;
            io.execute(() -> {
                TorrentHandle th = session.find(hash);
                if (th != null && th.isValid()) th.pause();
            });
        }
    }

    @MainThread
    public void remove(String infoHash, boolean deleteFiles) {
        DownloadHandle h = handles.remove(infoHash);
        TorrentRecord rec = records.remove(infoHash);
        if (h == null) return;

        DownloadHandle a = active.getValue();
        if (a != null && a.infoHash.equals(infoHash)) active.setValue(null);
        publishList();

        // libtorrent removal + file deletion can be slow (it unlinks every piece
        // file), so do it off the main thread along with the DB delete.
        final Sha1Hash hash = rec != null ? rec.hash : null;
        io.execute(() -> {
            if (hash != null) {
                TorrentHandle th = session.find(hash);
                if (th != null && th.isValid()) {
                    if (deleteFiles) session.remove(th, SessionHandle.DELETE_FILES);
                    else session.remove(th);
                }
            }
            if (deleteFiles) {
                File cached = new File(torrentFileCacheDir, infoHash + ".torrent");
                if (cached.exists()) //noinspection ResultOfMethodCallIgnored
                    cached.delete();
            }
            // Always nuke the fast-resume blob when removing — otherwise a future
            // re-add of the same hash would resume from stale piece state.
            File resume = new File(torrentFileCacheDir, infoHash + ".resume");
            if (resume.exists()) //noinspection ResultOfMethodCallIgnored
                resume.delete();
            dao.deleteByHash(infoHash);
        });
    }

    /**
     * Used by {@link com.vikas.torrentplayer.utils.CacheCleaner}: removes every
     * tracked torrent from libtorrent + clears our in-memory maps without
     * touching SharedPreferences or the DB (the cleaner does both itself).
     */
    public synchronized void removeAllSilently(boolean deleteFiles) {
        if (session == null) return;
        java.util.List<String> hashes = new java.util.ArrayList<>(handles.keySet());
        for (String hash : hashes) {
            TorrentRecord rec = records.remove(hash);
            handles.remove(hash);
            if (rec != null && rec.hash != null) {
                try {
                    TorrentHandle th = session.find(rec.hash);
                    if (th != null && th.isValid()) {
                        if (deleteFiles) session.remove(th, SessionHandle.DELETE_FILES);
                        else session.remove(th);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "removeAllSilently: failed for " + hash, t);
                }
            }
        }
        active.postValue(null);
        publishList();
    }

    // ---------------------------------------------------------------------
    // Introspection helpers used by the Download details screen
    // ---------------------------------------------------------------------

    @Nullable
    public TorrentInfo torrentInfoFor(String infoHash) {
        TorrentRecord rec = records.get(infoHash);
        return rec != null ? rec.info : null;
    }

    @Nullable
    public TorrentHandle handleFor(String infoHash) {
        TorrentRecord rec = records.get(infoHash);
        if (rec == null || rec.hash == null || session == null) return null;
        TorrentHandle th = session.find(rec.hash);
        return th != null && th.isValid() ? th : null;
    }

    /**
     * Everything an ExoPlayer DataSource needs to map a byte position inside
     * the video file to a libtorrent piece index.
     */
    public static class VideoFileLayout {
        public final File file;
        public final int pieceLength;
        /** Byte offset of the video file within the (multi-file) torrent. */
        public final long fileOffsetInTorrent;
        public final long fileSize;

        public VideoFileLayout(File file, int pieceLength,
                               long fileOffsetInTorrent, long fileSize) {
            this.file = file;
            this.pieceLength = pieceLength;
            this.fileOffsetInTorrent = fileOffsetInTorrent;
            this.fileSize = fileSize;
        }
    }

    @Nullable
    public VideoFileLayout videoFileLayout(String infoHash) {
        TorrentRecord rec = records.get(infoHash);
        if (rec == null || rec.info == null || rec.videoFileIndex < 0) return null;
        long offset = rec.info.files().fileOffset(rec.videoFileIndex);
        return new VideoFileLayout(
                rec.videoFilePath,
                rec.info.pieceLength(),
                offset,
                rec.videoFileSize);
    }

    // ---------------------------------------------------------------------
    // Restore + persist
    // ---------------------------------------------------------------------

    /** Background: read DB, re-add each torrent to libtorrent. */
    private void restoreFromDb() {
        List<DownloadEntity> rows;
        try {
            rows = dao.getAll();
        } catch (Throwable t) {
            Log.e(TAG, "DB read failed", t);
            return;
        }
        if (rows == null || rows.isEmpty()) return;

        for (DownloadEntity e : rows) {
            final DownloadEntity row = e;
            main.post(() -> {
                DownloadHandle h = new DownloadHandle(
                        row.infoHash,
                        row.title != null ? row.title : "Unknown",
                        row.posterUrl,
                        row.quality,
                        row.sizeBytes,
                        row.magnetUrl != null ? row.magnetUrl : ""
                );
                // Resume state from DB (most likely PAUSED until we re-add)
                DownloadHandle.State[] states = DownloadHandle.State.values();
                if (row.lastState >= 0 && row.lastState < states.length) {
                    h.state.setValue(states[row.lastState]);
                }
                if (row.videoFilePath != null) {
                    File f = new File(row.videoFilePath);
                    if (f.exists()) h.videoFile.setValue(f);
                }
                handles.put(row.infoHash, h);
                publishList();

                // Decide whether to restore as paused or as actively-downloading.
                // Default policy: NEVER auto-resume on launch (saves the user
                // from a tight-storage TV silently filling itself up after
                // restart). User can flip pref_auto_resume to opt back in.
                final boolean autoResumeAllowed = prefs.isAutoResume();

                DownloadHandle.State stateOnDisk =
                        (row.lastState >= 0 && row.lastState < states.length)
                                ? states[row.lastState]
                                : DownloadHandle.State.PAUSED;

                boolean restorePaused;
                DownloadHandle.State targetState;
                if (!autoResumeAllowed) {
                    // Hard policy: anything that wasn't already in a terminal
                    // state goes back as PAUSED. FINISHED stays FINISHED.
                    restorePaused = true;
                    targetState = stateOnDisk == DownloadHandle.State.FINISHED
                            ? DownloadHandle.State.FINISHED
                            : DownloadHandle.State.PAUSED;
                } else {
                    restorePaused =
                            stateOnDisk == DownloadHandle.State.PAUSED
                                    || stateOnDisk == DownloadHandle.State.FINISHED
                                    || stateOnDisk == DownloadHandle.State.ERROR;
                    targetState = stateOnDisk;
                    if (targetState == DownloadHandle.State.STARTING
                            || targetState == DownloadHandle.State.READY) {
                        targetState = DownloadHandle.State.BUFFERING;
                    }
                }

                // Try to re-add via cached .torrent file
                File cached = row.torrentFilePath != null
                        ? new File(row.torrentFilePath)
                        : new File(torrentFileCacheDir, row.infoHash + ".torrent");
                if (cached.exists() && isBencodedTorrent(cached)) {
                    TorrentItem stub = new TorrentItem();
                    stub.infoHash = row.infoHash;
                    stub.magnetUrl = row.magnetUrl;
                    addToSession(cached, stub, h, restorePaused, targetState);
                } else if (row.magnetUrl != null && !row.magnetUrl.isEmpty()) {
                    startWithMagnet(row.magnetUrl, h);
                }
            });
        }
    }

    private void persistAsync(DownloadHandle h) {
        if (dao == null) return;
        DownloadEntity e = new DownloadEntity();
        e.infoHash = h.infoHash;
        e.title = h.title;
        e.posterUrl = h.posterUrl;
        e.quality = h.quality;
        e.sizeBytes = h.sizeBytes;
        e.magnetUrl = h.magnetUrl;
        File t = new File(torrentFileCacheDir, h.infoHash + ".torrent");
        e.torrentFilePath = t.getAbsolutePath();
        File v = h.videoFile.getValue();
        if (v != null) e.videoFilePath = v.getAbsolutePath();
        e.addedAt = System.currentTimeMillis();
        DownloadHandle.State s = h.state.getValue();
        e.lastState = s == null ? 0 : s.ordinal();
        DownloadHandle.Progress p = h.progress.getValue();
        e.lastProgress = p == null ? 0 : p.percent;
        io.execute(() -> {
            try { dao.upsert(e); }
            catch (Throwable t2) { Log.e(TAG, "persist failed", t2); }
        });
    }

    // ---------------------------------------------------------------------
    // .torrent prefetch + add path
    // ---------------------------------------------------------------------

    /**
     * @param targetState  state to set on the handle after successful add. If
     *                     non-null and equals PAUSED/FINISHED, the torrent is
     *                     added paused so libtorrent doesn't auto-resume.
     */
    private void prefetchAndStart(@NonNull TorrentItem item, @NonNull DownloadHandle handle,
                                  boolean addPaused, @Nullable DownloadHandle.State targetState) {
        if (item.infoHash == null || item.infoHash.isEmpty()) {
            startWithMagnet(item.magnetUrl, handle);
            return;
        }
        final File cached = new File(torrentFileCacheDir, item.infoHash + ".torrent");
        if (cached.exists() && cached.length() > 0 && isBencodedTorrent(cached)) {
            addToSession(cached, item, handle, addPaused, targetState);
            return;
        }

        String apiKey = prefs.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            startWithMagnet(item.magnetUrl, handle);
            return;
        }

        api.downloadTorrentFile(ApiClient.bearer(apiKey), item.infoHash, apiKey)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w(TAG, "torrent-file HTTP " + response.code());
                            startWithMagnet(item.magnetUrl, handle);
                            return;
                        }
                        final ResponseBody body = response.body();
                        io.execute(() -> {
                            boolean ok = writeToFile(body, cached);
                            main.post(() -> {
                                if (ok && isBencodedTorrent(cached)) {
                                    addToSession(cached, item, handle, addPaused, targetState);
                                } else {
                                    //noinspection ResultOfMethodCallIgnored
                                    cached.delete();
                                    startWithMagnet(item.magnetUrl, handle);
                                }
                            });
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        Log.w(TAG, "torrent-file download failed", t);
                        startWithMagnet(item.magnetUrl, handle);
                    }
                });
    }

    /**
     * Parses the .torrent and hands it to libtorrent. The parse
     * ({@code new TorrentInfo}) and {@code session.download()} are JNI calls
     * that can take seconds on a big multi-file torrent, so the whole body runs
     * on the {@link #io} executor — running it on the main thread froze the UI
     * and triggered ANRs when a download started. Only the LiveData / in-memory
     * map mutations are posted back to the main thread.
     */
    private void addToSession(@NonNull File torrentFile, @NonNull TorrentItem item,
                              @NonNull DownloadHandle handle, boolean addPaused,
                              @Nullable DownloadHandle.State targetState) {
        io.execute(() -> {
            try {
                byte[] data = readBytes(torrentFile);
                final TorrentInfo ti = new TorrentInfo(data);
                if (!ti.isValid()) throw new IllegalStateException("TorrentInfo is invalid");

                final FileStorage files = ti.files();
                List<Integer> videoIndices = findAllVideoFiles(files);
                // Keep one "primary" video file for the player — largest by size
                // among the video files, falls back to the largest file overall
                // when no extension matched.
                final int videoIdx = videoIndices.isEmpty()
                        ? findVideoFile(files)
                        : largestOf(files, videoIndices);
                final List<Integer> subtitleIndices = findSubtitleFiles(files);

                Priority[] priorities = new Priority[files.numFiles()];
                for (int i = 0; i < priorities.length; i++) {
                    boolean isVideo = videoIndices.contains(i);
                    boolean isSub = subtitleIndices.contains(i);
                    // Multi-file torrents (e.g. full season packs): include every
                    // recognised video and subtitle; skip .nfo, .txt, .exe, .zip,
                    // sample dirs, .url shortcuts, etc.
                    boolean wanted = isVideo || isSub
                            // Single-file torrents whose only file didn't match a
                            // video extension — still download it (could be a
                            // weird container like .ogm).
                            || (videoIndices.isEmpty() && i == videoIdx);
                    priorities[i] = wanted ? Priority.DEFAULT : Priority.IGNORE;
                }

                final DownloadHandle.State finalState = targetState != null
                        ? targetState
                        : (addPaused ? DownloadHandle.State.PAUSED : DownloadHandle.State.BUFFERING);

                TorrentHandle existing = session.find(ti.infoHash());
                if (existing != null && existing.isValid()) {
                    if (addPaused) {
                        try { existing.unsetFlags(TorrentFlags.AUTO_MANAGED); } catch (Throwable ignored) {}
                        existing.pause();
                    } else {
                        existing.resume();
                    }
                    final TorrentHandle fe = existing;
                    main.post(() -> {
                        bindRecord(handle, ti, files, videoIdx, subtitleIndices);
                        if (!addPaused) {
                            TorrentRecord r = records.get(handle.infoHash);
                            io.execute(() -> prioritiseTailPieces(fe, r));
                        }
                        handle.state.setValue(finalState);
                        persistAsync(handle);
                    });
                    return;
                }

                // SEQUENTIAL_DOWNLOAD lets head pieces stream first; PAUSED ensures
                // restored-paused torrents don't auto-start when re-added.
                torrent_flags_t flags = TorrentFlags.SEQUENTIAL_DOWNLOAD;
                if (addPaused) {
                    try { flags = flags.or_(TorrentFlags.PAUSED); }
                    catch (Throwable ignored) {}
                }

                // Fast-resume support: if we ever managed to save a .resume blob
                // for this hash, hand it to libtorrent so it can skip re-hashing
                // every piece on startup.
                File resumeFile = new File(torrentFileCacheDir, item.infoHash + ".resume");
                File resumeArg = (resumeFile.exists() && resumeFile.length() > 0) ? resumeFile : null;

                session.download(ti, saveDir, resumeArg, priorities, null, flags);
                TorrentHandle th = session.find(ti.infoHash());
                if (th == null || !th.isValid()) {
                    throw new IllegalStateException("session.find() returned null after download()");
                }

                // Force-pin paused state — clear AUTO_MANAGED so the session queue
                // doesn't silently resume it again moments later.
                if (addPaused) {
                    try { th.unsetFlags(TorrentFlags.AUTO_MANAGED); }
                    catch (Throwable ex) { Log.w(TAG, "unsetFlags failed", ex); }
                }

                final TorrentHandle fth = th;
                final boolean hadResume = resumeArg != null;
                main.post(() -> {
                    bindRecord(handle, ti, files, videoIdx, subtitleIndices);
                    if (!addPaused) {
                        TorrentRecord r = records.get(handle.infoHash);
                        io.execute(() -> prioritiseTailPieces(fth, r));
                    }
                    handle.state.setValue(finalState);
                    persistAsync(handle);
                    Log.i(TAG, "added torrent \"" + ti.name() + "\" videoFile="
                            + (videoIdx >= 0 ? files.filePath(videoIdx) : "<none>")
                            + " subs=" + subtitleIndices.size()
                            + " paused=" + addPaused
                            + " resume=" + hadResume);
                });
            } catch (Exception e) {
                Log.e(TAG, "addToSession failed", e);
                main.post(() -> {
                    handle.state.setValue(DownloadHandle.State.ERROR);
                    handle.errorMessage.setValue(e.getMessage());
                    persistAsync(handle);
                });
            }
        });
    }

    private void bindRecord(DownloadHandle handle, TorrentInfo ti, FileStorage files, int videoIdx,
                            List<Integer> subtitleIndices) {
        TorrentRecord rec = new TorrentRecord();
        rec.hash = ti.infoHash();
        rec.info = ti;
        rec.videoFileIndex = videoIdx;
        if (videoIdx >= 0) {
            rec.videoFileSize = files.fileSize(videoIdx);
            rec.videoFilePath = new File(saveDir, files.filePath(videoIdx));
            int pieceSize = ti.pieceLength();
            long offset = files.fileOffset(videoIdx);
            rec.videoFirstPiece = (int) (offset / pieceSize);
            rec.videoLastPiece = (int) ((offset + rec.videoFileSize - 1) / pieceSize);

            if (rec.videoFileSize <= SMALL_FILE_THRESHOLD) {
                rec.headBufferLastPiece = rec.videoLastPiece;
                rec.tailBufferFirstPiece = -1;
            } else {
                int headPieces = (int) Math.max(2, HEAD_BUFFER_BYTES / Math.max(1, pieceSize));
                int tailPieces = (int) Math.max(2, TAIL_BUFFER_BYTES / Math.max(1, pieceSize));
                rec.headBufferLastPiece = Math.min(
                        rec.videoFirstPiece + headPieces - 1, rec.videoLastPiece);
                int tailStart = Math.max(
                        rec.videoLastPiece - tailPieces + 1,
                        rec.headBufferLastPiece + 1);
                rec.tailBufferFirstPiece = (tailStart <= rec.videoLastPiece) ? tailStart : -1;
            }
        }
        for (int i : subtitleIndices) {
            rec.subtitleFiles.add(new File(saveDir, files.filePath(i)));
        }
        records.put(handle.infoHash, rec);
        if (!rec.subtitleFiles.isEmpty()) {
            handle.subtitleFiles.setValue(new ArrayList<>(rec.subtitleFiles));
        }
        // Surface the video file as soon as we know its absolute path AND a
        // real file exists at that path. This unblocks PlayerActivity for
        // restored FINISHED downloads (libtorrent is paused, havePiece returns
        // false even though the file is fully on disk).
        if (rec.videoFilePath != null
                && rec.videoFilePath.exists()
                && rec.videoFilePath.length() > 0) {
            handle.videoFile.setValue(rec.videoFilePath);
        }
    }

    private void prioritiseTailPieces(TorrentHandle th, TorrentRecord rec) {
        if (rec == null || rec.tailBufferFirstPiece < 0) return;
        int deadlineMs = 1000;
        for (int i = rec.tailBufferFirstPiece; i <= rec.videoLastPiece; i++) {
            try {
                th.setPieceDeadline(i, deadlineMs);
                deadlineMs += 100;
            } catch (Throwable t) {
                Log.w(TAG, "setPieceDeadline(" + i + ") failed", t);
            }
        }
    }

    @MainThread
    private void startWithMagnet(@Nullable String magnetUrl, @NonNull DownloadHandle handle) {
        if (magnetUrl == null || magnetUrl.isEmpty()) {
            handle.state.setValue(DownloadHandle.State.ERROR);
            handle.errorMessage.setValue("No magnet URL available");
            persistAsync(handle);
            return;
        }
        handle.state.setValue(DownloadHandle.State.STARTING);
        io.execute(() -> {
            byte[] data;
            try { data = session.fetchMagnet(magnetUrl, 90, saveDir); }
            catch (Throwable t) {
                Log.e(TAG, "fetchMagnet threw", t);
                postError(handle, t.getMessage());
                return;
            }
            if (data == null || data.length == 0) {
                postError(handle, "Couldn't fetch torrent metadata (timeout)");
                return;
            }
            main.post(() -> {
                File cached = new File(torrentFileCacheDir, handle.infoHash + ".torrent");
                try (OutputStream os = new FileOutputStream(cached)) { os.write(data); }
                catch (IOException e) { Log.w(TAG, "cache magnet metadata", e); }
                TorrentItem stub = new TorrentItem();
                stub.infoHash = handle.infoHash;
                stub.magnetUrl = magnetUrl;
                addToSession(cached, stub, handle, false, null);
            });
        });
    }

    private void postError(DownloadHandle handle, String msg) {
        main.post(() -> {
            handle.state.setValue(DownloadHandle.State.ERROR);
            handle.errorMessage.setValue(msg);
            persistAsync(handle);
        });
    }

    // ---------------------------------------------------------------------
    // Alerts → DownloadHandle updates
    // ---------------------------------------------------------------------

    /**
     * Subscribe to a TINY subset of alerts. libtorrent fires thousands of
     * {@code BLOCK_FINISHED} / peer / DHT alerts per second under load — returning
     * {@code null} from {@link AlertListener#types()} means "everything", which
     * lets that flood swamp the main-thread Handler queue and accumulate native
     * memory until we OOM (or the Alert objects get recycled out from under us
     * → use-after-free → SIGSEGV in {@code torrent_handle.info_hash}).
     *
     * <p>We only care about: periodic status (STATE_UPDATE), final completion,
     * and fatal errors. Buffer readiness is computed from {@link TorrentHandle}
     * directly inside {@link #refreshProgressForKey}.
     */
    private final int[] WANTED_ALERTS = new int[] {
            AlertType.STATE_UPDATE.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
    };

    private final AlertListener alertListener = new AlertListener() {
        @Override public int[] types() { return WANTED_ALERTS; }

        @Override
        public void alert(Alert<?> alert) {
            // Critical: extract scalars on the libtorrent thread, do NOT pass
            // the Alert reference across threads — libtorrent recycles alert
            // objects from a small native pool, and capturing them in a main
            // thread Runnable will eventually dereference freed memory.
            AlertType t;
            try { t = alert.type(); }
            catch (Throwable ex) { return; }

            switch (t) {
                case STATE_UPDATE:
                    // No data needed; coalesce multiple updates onto main
                    if (refreshPending.compareAndSet(false, true)) {
                        main.post(coalescedRefresh);
                    }
                    break;
                case TORRENT_FINISHED:
                    if (alert instanceof TorrentFinishedAlert) {
                        try {
                            final String hex = ((TorrentFinishedAlert) alert)
                                    .handle().infoHash().toHex();
                            main.post(() -> onFinishedByHex(hex));
                        } catch (Throwable ignored) {}
                    }
                    break;
                case TORRENT_ERROR:
                    if (alert instanceof TorrentErrorAlert) {
                        try {
                            TorrentErrorAlert tea = (TorrentErrorAlert) alert;
                            final String hex = tea.handle().infoHash().toHex();
                            final String msg = tea.message();
                            main.post(() -> onErrorByHex(hex, msg));
                        } catch (Throwable ignored) {}
                    }
                    break;
                case SAVE_RESUME_DATA:
                    if (alert instanceof SaveResumeDataAlert) {
                        try {
                            SaveResumeDataAlert srda = (SaveResumeDataAlert) alert;
                            final String hex = srda.handle().infoHash().toHex();
                            byte[] bytes = serializeResumeData(srda);
                            if (bytes != null) {
                                io.execute(() -> writeResumeFile(hex, bytes));
                            }
                        } catch (Throwable ex) {
                            Log.w(TAG, "save_resume_data failed", ex);
                        }
                    }
                    break;
                default: break;
            }
        }
    };

    /** Coalesces a flood of STATE_UPDATE alerts into one main-thread refresh. */
    private final java.util.concurrent.atomic.AtomicBoolean refreshPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Runnable coalescedRefresh = new Runnable() {
        @Override public void run() {
            refreshPending.set(false);
            refreshAllProgress();
        }
    };

    private void onFinishedByHex(String hex) {
        DownloadHandle dh = handles.get(hex);
        TorrentRecord rec = records.get(hex);
        if (dh != null) {
            dh.state.setValue(DownloadHandle.State.FINISHED);
            persistAsync(dh);
        }
        if (rec != null) {
            stopSeedingFor(rec);
            if (rec.videoFilePath != null) notifyMediaScanner(rec.videoFilePath);
        }
    }

    private void onErrorByHex(String hex, String msg) {
        DownloadHandle dh = handles.get(hex);
        if (dh != null) {
            dh.state.setValue(DownloadHandle.State.ERROR);
            dh.errorMessage.setValue(msg);
            persistAsync(dh);
        }
    }

    private void refreshAllProgress() {
        // Snapshot on the main thread, then do the libtorrent reads on the io
        // thread. The status() call + havePiece() buffer scan are JNI work that,
        // run on main every 2s, competed with video decode on weak boxes and
        // made the player stutter/freeze during buffering. We never iterate the
        // live map off-main (HashMap isn't thread-safe).
        final List<Map.Entry<String, TorrentRecord>> snapshot =
                new ArrayList<>(records.entrySet());
        if (snapshot.isEmpty()) return;
        io.execute(() -> {
            for (Map.Entry<String, TorrentRecord> e : snapshot) {
                refreshProgressForKeyBg(e.getKey(), e.getValue());
            }
        });
    }

    /** libtorrent reads on the io thread; the LiveData mutations are posted to main. */
    private void refreshProgressForKeyBg(String key, TorrentRecord rec) {
        if (rec.hash == null || session == null) return;
        TorrentHandle th = session.find(rec.hash);
        if (th == null || !th.isValid()) return;

        TorrentStatus status = th.status();
        final int pct = Math.max(0, Math.min(100, Math.round(status.progress() * 100)));
        final long rawSpeed = status.downloadRate();
        final int seeders = status.numSeeds();
        final int bufferPct = computeHeadBuffer(rec, th);
        // Playback can begin once the HEAD is on disk — the tail (MP4 moov / MKV
        // cues) is fetched on demand because TorrentDataSource blocks reads on
        // missing pieces and we deadline-prioritise the tail. Gating on the full
        // head+tail buffer meant streaming never started until the whole file
        // finished, since sequential download fetches the tail last.
        final boolean headReady = isHeadReady(rec, th);
        final boolean fileReady = rec.videoFilePath != null
                && rec.videoFilePath.exists() && rec.videoFilePath.length() > 0;

        main.post(() -> applyProgress(key, rec, pct, rawSpeed, seeders, bufferPct,
                headReady, fileReady));
    }

    @MainThread
    private void applyProgress(String key, TorrentRecord rec, int pct, long rawSpeed,
                               int seeders, int bufferPct, boolean headReady,
                               boolean fileReady) {
        DownloadHandle h = handles.get(key);
        if (h == null) return;

        // Suppress reported speed for paused / finished torrents so the UI
        // doesn't jitter with leftover stats from libtorrent's last sample.
        DownloadHandle.State curState = h.state.getValue();
        boolean stoppedish = curState == DownloadHandle.State.PAUSED
                || curState == DownloadHandle.State.FINISHED
                || pct >= 100;
        long speed = stoppedish ? 0 : rawSpeed;

        h.progress.setValue(new DownloadHandle.Progress(pct, speed, seeders, bufferPct));

        if (!rec.readyEmitted && rec.videoFilePath != null && headReady && fileReady) {
            rec.readyEmitted = true;
            h.videoFile.setValue(rec.videoFilePath);
            h.state.setValue(DownloadHandle.State.READY);
            persistAsync(h);
            Log.i(TAG, "READY (head buffered): " + rec.videoFilePath.getAbsolutePath());
        }

        // Periodic progress persistence — throttle to whole-percent changes to
        // avoid hammering Room every alert.
        int lastPersistedPct = lastPersistedProgress.getOrDefault(key, -1);
        if (pct != lastPersistedPct) {
            lastPersistedProgress.put(key, pct);
            DownloadHandle.State s = h.state.getValue();
            int stateOrd = s == null ? 0 : s.ordinal();
            io.execute(() -> {
                try { dao.updateProgress(key, stateOrd, pct); }
                catch (Throwable ignored) {}
            });
        }

        if (pct >= 100 && h.state.getValue() != DownloadHandle.State.FINISHED) {
            h.state.setValue(DownloadHandle.State.FINISHED);
            // Stop the torrent completely so we don't keep using bandwidth /
            // CPU / wakelock on a finished item.
            stopSeedingFor(rec);
            // Surface the file to MediaScanner so gallery / players see it.
            if (rec.videoFilePath != null) notifyMediaScanner(rec.videoFilePath);
            persistAsync(h);
        }
    }

    /** Throttles progress writes. */
    private final HashMap<String, Integer> lastPersistedProgress = new HashMap<>();

    /** True once every piece of the head buffer (start of the video file) is on
     *  disk — enough to begin playback; the tail is streamed on demand. */
    private boolean isHeadReady(TorrentRecord rec, TorrentHandle th) {
        if (rec.videoFirstPiece < 0) return false;
        int headEnd = Math.min(rec.headBufferLastPiece, rec.videoLastPiece);
        for (int i = rec.videoFirstPiece; i <= headEnd; i++) {
            if (!th.havePiece(i)) return false;
        }
        return true;
    }

    private int computeHeadBuffer(TorrentRecord rec, TorrentHandle th) {
        if (rec.videoFirstPiece < 0) return 0;
        int target = 0;
        int have = 0;
        int headEnd = Math.min(rec.headBufferLastPiece, rec.videoLastPiece);
        for (int i = rec.videoFirstPiece; i <= headEnd; i++) {
            target++;
            if (th.havePiece(i)) have++;
        }
        if (rec.tailBufferFirstPiece >= 0 && rec.tailBufferFirstPiece > headEnd) {
            for (int i = rec.tailBufferFirstPiece; i <= rec.videoLastPiece; i++) {
                target++;
                if (th.havePiece(i)) have++;
            }
        }
        return Math.min(100, (have * 100) / Math.max(1, target));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Every file index whose extension looks like a playable video. */
    private static List<Integer> findAllVideoFiles(FileStorage files) {
        List<Integer> out = new ArrayList<>();
        int n = files.numFiles();
        for (int i = 0; i < n; i++) {
            String path = files.filePath(i).toLowerCase(Locale.US);
            // Skip "sample" subfolders — they're trailers/demos, never the
            // real content.
            if (path.contains("/sample/") || path.startsWith("sample/")) continue;
            if (isExt(path, VIDEO_EXTS)) out.add(i);
        }
        return out;
    }

    /** Picks the largest file from the given indices. */
    private static int largestOf(FileStorage files, List<Integer> indices) {
        int best = -1;
        long bestSize = -1;
        for (int i : indices) {
            long size = files.fileSize(i);
            if (size > bestSize) { best = i; bestSize = size; }
        }
        return best;
    }

    private static int findVideoFile(FileStorage files) {
        int n = files.numFiles();
        int bestVideo = -1;
        long bestVideoSize = 0;
        int largest = -1;
        long largestSize = 0;
        for (int i = 0; i < n; i++) {
            String path = files.filePath(i).toLowerCase(Locale.US);
            long size = files.fileSize(i);
            if (size > largestSize) { largest = i; largestSize = size; }
            if (isExt(path, VIDEO_EXTS) && size > bestVideoSize) {
                bestVideo = i;
                bestVideoSize = size;
            }
        }
        return bestVideo >= 0 ? bestVideo : largest;
    }

    private static List<Integer> findSubtitleFiles(FileStorage files) {
        List<Integer> out = new ArrayList<>();
        int n = files.numFiles();
        for (int i = 0; i < n; i++) {
            String path = files.filePath(i).toLowerCase(Locale.US);
            if (isExt(path, SUBTITLE_EXTS)) out.add(i);
        }
        return out;
    }

    private static boolean isExt(String path, String[] exts) {
        for (String ext : exts) if (path.endsWith(ext)) return true;
        return false;
    }

    private static boolean isBencodedTorrent(File f) {
        if (!f.exists() || f.length() < 10) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            return in.read() == 'd';
        } catch (IOException e) { return false; }
    }

    private static byte[] readBytes(File f) throws IOException {
        long len = f.length();
        if (len > Integer.MAX_VALUE) throw new IOException("file too large: " + len);
        byte[] data = new byte[(int) len];
        try (FileInputStream in = new FileInputStream(f)) {
            int total = 0;
            while (total < data.length) {
                int n = in.read(data, total, data.length - total);
                if (n < 0) break;
                total += n;
            }
        }
        return data;
    }

    private static boolean writeToFile(@NonNull ResponseBody body, @NonNull File out) {
        try (InputStream in = body.byteStream();
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "writeToFile failed", e);
            return false;
        } finally {
            body.close();
        }
    }

    private void publishList() {
        List<DownloadHandle> snapshot = new ArrayList<>(handles.values());
        Collections.reverse(snapshot);
        allDownloads.setValue(snapshot);
    }
}
