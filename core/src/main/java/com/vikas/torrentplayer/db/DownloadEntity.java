package com.vikas.torrentplayer.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Persisted record for one torrent download. Restored on app start so downloads
 * survive crashes and process death.
 */
@Entity(tableName = "downloads")
public class DownloadEntity {

    @PrimaryKey
    @NonNull
    public String infoHash = "";

    public String title;
    public String posterUrl;
    public String quality;
    public long sizeBytes;

    /** Original magnet URL (we keep this so we can re-add via magnet if the
     *  cached .torrent file is gone). */
    public String magnetUrl;

    /** Absolute path of the cached .torrent file on disk. */
    public String torrentFilePath;

    /** Absolute path of the (largest) video file once known. */
    public String videoFilePath;

    /** Wall-clock millis when the user added this download. */
    public long addedAt;

    /** Ordinal of {@link com.vikas.torrentplayer.torrent.DownloadHandle.State} as of last update. */
    public int lastState;

    /** 0..100 progress as of last update. */
    public int lastProgress;
}
