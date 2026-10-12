package com.vikas.torrentplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") LiveData<List<FavoriteEntity>> observeAll();
    @Query("SELECT * FROM favorites") List<FavoriteEntity> getAll();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void put(FavoriteEntity item);
    @Query("DELETE FROM favorites WHERE `key` = :key") void delete(String key);
    @Query("UPDATE favorites SET lastEpisode = :episode, updated = updated OR :changed, ended = :ended WHERE `key` = :key")
    void updateEpisode(String key, String episode, boolean changed, boolean ended);
    @Query("UPDATE favorites SET updated = 0 WHERE `key` = :key") void markSeen(String key);
}
