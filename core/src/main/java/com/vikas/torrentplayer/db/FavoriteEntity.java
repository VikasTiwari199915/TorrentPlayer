package com.vikas.torrentplayer.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {
    @PrimaryKey @NonNull public String key = "";
    public String metadata;
    public long addedAt;
    public String lastEpisode;
    public boolean updated;
    public boolean ended;
}
