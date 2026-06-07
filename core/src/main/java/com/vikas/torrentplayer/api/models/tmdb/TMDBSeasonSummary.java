package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class TMDBSeasonSummary implements Serializable {
    @SerializedName("id") public long id;
    @SerializedName("air_date") public String airDate;
    @SerializedName("episode_count") public int episodeCount;
    @SerializedName("name") public String name;
    @SerializedName("season_number") public int seasonNumber;

    public String displayTitle() {
        String base = seasonNumber == 0 ? "Specials" : "Season " + seasonNumber;
        return base + " (" + episodeCount + ")";
    }
}
