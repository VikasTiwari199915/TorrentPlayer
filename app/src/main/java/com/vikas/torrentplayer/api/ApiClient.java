package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client for the TorrentClaw API.
 */
public final class ApiClient {

    public static final String BASE_URL = "https://torrentclaw.com/";

    private static volatile TorrentClawApi instance;

    private ApiClient() {}

    public static TorrentClawApi get() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = build();
                }
            }
        }
        return instance;
    }

    private static TorrentClawApi build() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(TorrentClawApi.class);
    }

    /** Helper to build a "Bearer <token>" header value. */
    public static String bearer(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) return null;
        return "Bearer " + apiKey;
    }
}
