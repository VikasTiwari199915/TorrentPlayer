package com.vikas.torrentplayer.torrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Observable handle around a single torrent download / stream.
 * Created and owned by {@link TorrentManager}.
 */
public class DownloadHandle {

    public enum State {
        STARTING,   // session is being prepared / connecting to peers
        BUFFERING,  // pieces flowing in but not yet enough to play
        READY,      // enough head data available — playable
        PAUSED,     // user paused
        FINISHED,   // 100% downloaded
        ERROR       // failed
    }

    public final String infoHash;
    public final String title;
    public final String posterUrl;
    public final String quality;
    public final long sizeBytes;
    public final String magnetUrl;

    public final MutableLiveData<State> state = new MutableLiveData<>(State.STARTING);
    public final MutableLiveData<Progress> progress = new MutableLiveData<>(new Progress(0, 0, 0, 0));
    public final MutableLiveData<File> videoFile = new MutableLiveData<>(null);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    /** External subtitle files discovered inside the torrent payload (.srt/.vtt/.ass…). */
    public final MutableLiveData<List<File>> subtitleFiles = new MutableLiveData<>(new ArrayList<>());

    DownloadHandle(@NonNull String infoHash,
                   @NonNull String title,
                   @Nullable String posterUrl,
                   @Nullable String quality,
                   long sizeBytes,
                   @NonNull String magnetUrl) {
        this.infoHash = infoHash;
        this.title = title;
        this.posterUrl = posterUrl;
        this.quality = quality;
        this.sizeBytes = sizeBytes;
        this.magnetUrl = magnetUrl;
    }

    /** Snapshot of streaming progress at a given moment. */
    public static class Progress {
        /** 0..100 */
        public final int percent;
        /** bytes per second */
        public final long downloadSpeed;
        public final int seeders;
        public final int bufferProgress; // 0..100, head-buffer for playback

        public Progress(int percent, long downloadSpeed, int seeders, int bufferProgress) {
            this.percent = percent;
            this.downloadSpeed = downloadSpeed;
            this.seeders = seeders;
            this.bufferProgress = bufferProgress;
        }
    }
}
