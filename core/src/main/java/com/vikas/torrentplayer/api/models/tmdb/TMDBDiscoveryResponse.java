package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Paged response shared by TMDB discover, trending, and release-list endpoints. */
public class TMDBDiscoveryResponse {
    @SerializedName("page")
    public int page;

    @SerializedName("results")
    public List<TMDBDiscoveryItem> results;

    @SerializedName("total_pages")
    public int totalPages;

    @SerializedName("total_results")
    public int totalResults;
}
