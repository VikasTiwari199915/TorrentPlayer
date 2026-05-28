package com.vikas.torrentplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DownloadEntity entity);

    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    List<DownloadEntity> getAll();

    @Query("SELECT * FROM downloads WHERE infoHash = :hash LIMIT 1")
    DownloadEntity findByHash(String hash);

    @Query("DELETE FROM downloads WHERE infoHash = :hash")
    void deleteByHash(String hash);

    /** Used by CacheCleaner to wipe every persisted download row at once. */
    @Query("DELETE FROM downloads")
    void deleteAll();

    @Query("UPDATE downloads SET lastState = :state, lastProgress = :progress WHERE infoHash = :hash")
    void updateProgress(String hash, int state, int progress);

    @Query("UPDATE downloads SET videoFilePath = :path WHERE infoHash = :hash")
    void updateVideoPath(String hash, String path);
}
