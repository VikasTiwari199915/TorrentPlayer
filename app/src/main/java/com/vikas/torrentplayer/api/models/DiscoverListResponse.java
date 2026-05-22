package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Paged wrapper used by /popular, /recent, /trending. */
public class DiscoverListResponse {

    @SerializedName("items")
    public List<DiscoverItem> items;

    @SerializedName("total")
    public int total;

    @SerializedName("page")
    public int page;

    @SerializedName("pageSize")
    public int pageSize;

    /** Only set by /trending. */
    @SerializedName("period")
    public String period;
}
