package com.vikas.torrentplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TorBoxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TorBoxEntity entity);

    @Query("SELECT * FROM torbox_downloads ORDER BY addedAt DESC")
    List<TorBoxEntity> getAll();

    @Query("DELETE FROM torbox_downloads WHERE `key` = :key")
    void deleteByKey(String key);

    @Query("DELETE FROM torbox_downloads")
    void deleteAll();
}
