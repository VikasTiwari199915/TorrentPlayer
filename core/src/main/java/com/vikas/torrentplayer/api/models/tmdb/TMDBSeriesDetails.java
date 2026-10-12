package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class TMDBSeriesDetails implements Serializable {
    @SerializedName("id") public long id;
    @SerializedName("name") public String name;
    @SerializedName("status") public String status;
    @SerializedName("last_episode_to_air") public Episode lastEpisode;
    public static class Episode implements Serializable {
        @SerializedName("season_number") public int season;
        @SerializedName("episode_number") public int episode;
        @SerializedName("air_date") public String airDate;
    }
    @SerializedName("number_of_episodes") public int numberOfEpisodes;
    @SerializedName("number_of_seasons") public int numberOfSeasons;
    @SerializedName("seasons") public List<TMDBSeasonSummary> seasons;
}
