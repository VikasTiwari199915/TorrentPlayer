package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonDetails;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TMDBApiService {

    @GET("tv/{seriesId}")
    Call<TMDBSeriesDetails> seriesDetails(
            @Header("Authorization") String bearer,
            @Path("seriesId") long seriesId,
            @Query("api_key") String apiKey,
            @Query("language") String language
    );

    @GET("tv/{seriesId}/season/{seasonNumber}")
    Call<TMDBSeasonDetails> seasonDetails(
            @Header("Authorization") String bearer,
            @Path("seriesId") long seriesId,
            @Path("seasonNumber") int seasonNumber,
            @Query("api_key") String apiKey,
            @Query("language") String language
    );
}
