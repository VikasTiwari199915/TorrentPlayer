package com.vikas.torrentplayer.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Persisted record for one TorBox device-download, so finished files stay listed
 * (and tap-to-play) and interrupted ones can resume after the app restarts.
 */
@Entity(tableName = "torbox_downloads")
public class TorBoxEntity {

    @PrimaryKey
    @NonNull
    public String key = "";   // "tb:<torrentId>:<fileId>"

    public String title;
    public long torrentId;
    public int fileId;
    public String fileName;
    public String filePath;   // absolute path of the local file
    public long sizeBytes;    // expected total size
    public int lastState;     // ordinal of TorBoxManager.State
    public int lastProgress;  // 0..100
    public long addedAt;
}
