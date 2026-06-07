package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class TMDBSeriesDetails implements Serializable {
    @SerializedName("id") public long id;
    @SerializedName("name") public String name;
    @SerializedName("number_of_episodes") public int numberOfEpisodes;
    @SerializedName("number_of_seasons") public int numberOfSeasons;
    @SerializedName("seasons") public List<TMDBSeasonSummary> seasons;
}
