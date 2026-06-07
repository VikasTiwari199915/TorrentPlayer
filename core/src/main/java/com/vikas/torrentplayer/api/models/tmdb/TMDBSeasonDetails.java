package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class TMDBSeasonDetails implements Serializable {
    @SerializedName("id") public long id;
    @SerializedName("name") public String name;
    @SerializedName("season_number") public int seasonNumber;
    @SerializedName("episodes") public List<TMDBEpisode> episodes;
}
