package com.vikas.torrentplayer.api.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class Streaming implements Serializable {
    @SerializedName("flatrate")
    public List<Provider> flatrate;

    @SerializedName("rent")
    public List<Provider> rent;

    @SerializedName("buy")
    public List<Provider> buy;

    @SerializedName("free")
    public List<Provider> free;

    public static class Provider implements Serializable {
        @SerializedName("providerId") public int providerId;
        @SerializedName("name") public String name;
        @SerializedName("logo") public String logo;
        @SerializedName("link") public String link;
    }
}
