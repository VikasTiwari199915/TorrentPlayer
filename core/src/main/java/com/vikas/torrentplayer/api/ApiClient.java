package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.core.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit factory + cached singletons for the APIs the engine talks to.
 *
 * <ul>
 *   <li>{@link #get()} → TorrentClaw API (torrents catalogue + download).</li>
 *   <li>{@link #github()} → GitHub REST API (used by the auto-updater).</li>
 * </ul>
 */
public final class ApiClient {

    public static final String BASE_URL = "https://torrentclaw.com/";
    public static final String GITHUB_BASE_URL = "https://api.github.com/";
    public static final String TMDB_BASE_URL = "https://api.themoviedb.org/3/";

    private static volatile TorrentClawApi torrentClaw;
    private static volatile GitHubApiService github;
    private static volatile TMDBApiService tmdb;

    private ApiClient() {}

    public static TorrentClawApi get() {
        if (torrentClaw == null) {
            synchronized (ApiClient.class) {
                if (torrentClaw == null) {
                    torrentClaw = buildRetrofit(BASE_URL).create(TorrentClawApi.class);
                }
            }
        }
        return torrentClaw;
    }

    public static GitHubApiService github() {
        if (github == null) {
            synchronized (ApiClient.class) {
                if (github == null) {
                    github = buildRetrofit(GITHUB_BASE_URL).create(GitHubApiService.class);
                }
            }
        }
        return github;
    }

    public static TMDBApiService tmdb() {
        if (tmdb == null) {
            synchronized (ApiClient.class) {
                if (tmdb == null) {
                    tmdb = buildRetrofit(TMDB_BASE_URL).create(TMDBApiService.class);
                }
            }
        }
        return tmdb;
    }

    private static Retrofit buildRetrofit(String baseUrl) {
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

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /** Helper to build a "Bearer &lt;token&gt;" header value. */
    public static String bearer(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) return null;
        return "Bearer " + apiKey;
    }
}
