package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SearchResponse {
    @SerializedName("total")
    public int total;

    @SerializedName("page")
    public int page;

    @SerializedName("pageSize")
    public int pageSize;

    @SerializedName("results")
    public List<SearchResult> results;
}
