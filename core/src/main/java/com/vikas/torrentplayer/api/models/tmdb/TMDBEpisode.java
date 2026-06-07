package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class TMDBEpisode implements Serializable {
    @SerializedName("id") public long id;
    @SerializedName("air_date") public String airDate;
    @SerializedName("episode_number") public int episodeNumber;
    @SerializedName("name") public String name;
    @SerializedName("overview") public String overview;
    @SerializedName("runtime") public Integer runtime;
    @SerializedName("season_number") public int seasonNumber;

    public boolean isReleased() {
        if (airDate == null || airDate.isEmpty()) return false;
        try { return !LocalDate.parse(airDate).isAfter(LocalDate.now()); }
        catch (DateTimeParseException e) { return false; }
    }

    public String displayTitle() {
        return "E" + episodeNumber + (name == null || name.isEmpty() ? "" : " · " + name);
    }
}
