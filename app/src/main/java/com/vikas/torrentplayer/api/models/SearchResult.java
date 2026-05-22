package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class SearchResult implements Serializable {
    @SerializedName("id")
    public long id;

    @SerializedName("imdbId")
    public String imdbId;

    /** API sends this as a JSON number for free-tier responses. */
    @SerializedName("tmdbId")
    public Long tmdbId;

    /** Present on results matched via an alternate title; null otherwise. */
    @SerializedName("matchedAlt")
    public String matchedAlt;

    /** "movie" or "show" */
    @SerializedName("contentType")
    public String contentType;

    @SerializedName("title")
    public String title;

    @SerializedName("titleOriginal")
    public String titleOriginal;

    @SerializedName("year")
    public Integer year;

    @SerializedName("overview")
    public String overview;

    @SerializedName("posterUrl")
    public String posterUrl;

    @SerializedName("backdropUrl")
    public String backdropUrl;

    @SerializedName("genres")
    public List<String> genres;

    @SerializedName("ratingImdb")
    public String ratingImdb;

    @SerializedName("ratingTmdb")
    public String ratingTmdb;

    @SerializedName("contentUrl")
    public String contentUrl;

    @SerializedName("hasTorrents")
    public boolean hasTorrents;

    @SerializedName("streaming")
    public Streaming streaming;

    @SerializedName("torrents")
    public List<TorrentItem> torrents;

    public boolean isShow() {
        return "show".equalsIgnoreCase(contentType);
    }

    public String getDisplayRating() {
        if (ratingImdb != null && !ratingImdb.isEmpty()) return ratingImdb;
        if (ratingTmdb != null && !ratingTmdb.isEmpty()) return ratingTmdb;
        return null;
    }
}
