package com.vikas.torrentplayer.api.models.tmdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.vikas.torrentplayer.api.models.DiscoverItem;

import java.util.Locale;

/** Union of the movie and TV item shapes returned by TMDB list endpoints. */
public class TMDBDiscoveryItem {
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/";

    @SerializedName("id")
    public long id;

    @SerializedName("title")
    public String movieTitle;

    @SerializedName("name")
    public String showTitle;

    @SerializedName("media_type")
    public String mediaType;

    @SerializedName("release_date")
    public String releaseDate;

    @SerializedName("first_air_date")
    public String firstAirDate;

    @SerializedName("overview")
    public String overview;

    @SerializedName("poster_path")
    public String posterPath;

    @SerializedName("backdrop_path")
    public String backdropPath;

    @SerializedName("vote_average")
    public Double voteAverage;

    @Nullable
    public DiscoverItem toDiscoverItem(@NonNull String fallbackType) {
        String type = normalizeType(mediaType, fallbackType);
        if (type == null || id <= 0) return null;
        String title = "show".equals(type) ? showTitle : movieTitle;
        if (title == null || title.trim().isEmpty()) {
            title = movieTitle != null ? movieTitle : showTitle;
        }
        if (title == null || title.trim().isEmpty()) return null;

        DiscoverItem out = new DiscoverItem();
        // Keep TorrentClaw's catalogue id empty. DetailActivity will resolve
        // torrents by title/year while preserving this TMDB id and artwork.
        out.tmdbId = id;
        out.title = title;
        out.contentType = type;
        out.year = parseYear("show".equals(type) ? firstAirDate : releaseDate);
        out.overview = overview;
        out.posterUrl = imageUrl("w500", posterPath);
        out.backdropUrl = imageUrl("w1280", backdropPath);
        if (voteAverage != null && voteAverage > 0) {
            out.ratingTmdb = String.format(Locale.US, "%.1f", voteAverage);
        }
        return out;
    }

    @Nullable
    private static String normalizeType(@Nullable String mediaType,
                                        @NonNull String fallbackType) {
        String raw = mediaType == null || mediaType.isEmpty() ? fallbackType : mediaType;
        if ("tv".equalsIgnoreCase(raw) || "series".equalsIgnoreCase(raw)
                || "show".equalsIgnoreCase(raw)) return "show";
        if ("movie".equalsIgnoreCase(raw)) return "movie";
        // Trending-all can also contain people; they are not playable content.
        return null;
    }

    @Nullable
    private static Integer parseYear(@Nullable String date) {
        if (date == null || date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static String imageUrl(@NonNull String size, @Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        return IMAGE_BASE + size + (path.startsWith("/") ? path : "/" + path);
    }
}
