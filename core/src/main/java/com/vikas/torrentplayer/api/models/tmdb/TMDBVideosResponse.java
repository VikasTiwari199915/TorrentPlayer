package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TMDBVideosResponse {
    @SerializedName("id")
    public long id;

    @SerializedName("results")
    public List<TMDBVideo> results;
}
