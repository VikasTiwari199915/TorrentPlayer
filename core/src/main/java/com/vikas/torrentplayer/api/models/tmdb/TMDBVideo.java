package com.vikas.torrentplayer.api.models.tmdb;

import com.google.gson.annotations.SerializedName;

public class TMDBVideo {
    @SerializedName("id")
    public String id;

    @SerializedName("key")
    public String key;

    @SerializedName("name")
    public String name;

    @SerializedName("site")
    public String site;

    @SerializedName("type")
    public String type;

    @SerializedName("official")
    public boolean official;

    @SerializedName("size")
    public int size;

    @SerializedName("published_at")
    public String publishedAt;

    public boolean isYouTube() {
        return key != null && !key.isEmpty() && "youtube".equalsIgnoreCase(site);
    }

    public String watchUrl() {
        return "https://www.youtube.com/watch?v=" + key;
    }

    public String thumbnailUrl() {
        return "https://i.ytimg.com/vi/" + key + "/hqdefault.jpg";
    }
}
