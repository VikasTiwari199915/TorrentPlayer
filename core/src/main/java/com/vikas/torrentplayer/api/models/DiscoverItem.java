package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Common shape used by /popular, /recent, /trending and /streaming-top.
 *
 * <p>Gson tolerates missing fields, so we accept the union of every endpoint's
 * fields here. Callers should prefer {@link #effectiveId()} which collapses the
 * {@code id} vs {@code contentId} naming difference between endpoints.
 */
public class DiscoverItem implements Serializable {

    /** Used by /popular, /recent, /trending. */
    @SerializedName("id")
    public Long id;

    /** Used by /streaming-top (the underlying content row id). */
    @SerializedName("contentId")
    public Long contentId;

    @SerializedName("title")
    public String title;

    @SerializedName("year")
    public Integer year;

    /** "movie" or "show". */
    @SerializedName("contentType")
    public String contentType;

    @SerializedName("posterUrl")
    public String posterUrl;

    /** Only present on /streaming-top. */
    @SerializedName("backdropUrl")
    public String backdropUrl;

    @SerializedName("ratingImdb")
    public String ratingImdb;

    @SerializedName("ratingTmdb")
    public String ratingTmdb;

    /** Single aggregated rating on /streaming-top responses. */
    @SerializedName("rating")
    public String rating;

    @SerializedName("overview")
    public String overview;

    /** ISO-8601 added-at timestamp on /recent. */
    @SerializedName("createdAt")
    public String createdAt;

    /** Best seeders observed across torrents (popular/recent/trending/streaming-top). */
    @SerializedName("maxSeeders")
    public Integer maxSeeders;

    @SerializedName("clickCount")
    public Integer clickCount;

    @SerializedName("trendScore")
    public Integer trendScore;

    /** 1-based rank in /streaming-top response. */
    @SerializedName("rank")
    public Integer rank;

    @SerializedName("genres")
    public List<String> genres;

    @SerializedName("hasTorrents")
    public Boolean hasTorrents;

    /** Streaming-top reaches across to torrentclaw's local mirror of the poster. */
    @SerializedName("localPosterUrl")
    public String localPosterUrl;

    /** IMDb id (string with "tt" prefix). */
    @SerializedName("imdbId")
    public String imdbId;

    /** TMDb id (number). */
    @SerializedName("tmdbId")
    public Long tmdbId;

    public long effectiveId() {
        if (contentId != null && contentId > 0) return contentId;
        if (id != null && id > 0) return id;
        return 0L;
    }

    public String effectivePoster() {
        if (localPosterUrl != null && !localPosterUrl.isEmpty()) return localPosterUrl;
        return posterUrl;
    }

    /** Returns the best non-zero rating available, or null. */
    public String displayRating() {
        if (rating != null && isMeaningfulRating(rating)) return rating;
        if (ratingImdb != null && isMeaningfulRating(ratingImdb)) return ratingImdb;
        if (ratingTmdb != null && isMeaningfulRating(ratingTmdb)) return ratingTmdb;
        return null;
    }

    private static boolean isMeaningfulRating(String r) {
        if (r == null || r.isEmpty()) return false;
        // The API returns "0.0" placeholders we don't want to show
        try {
            return Double.parseDouble(r) > 0.0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public boolean isShow() {
        return "show".equalsIgnoreCase(contentType);
    }
}
