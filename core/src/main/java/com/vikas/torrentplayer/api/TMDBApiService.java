package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonDetails;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;
import com.vikas.torrentplayer.api.models.tmdb.TMDBDiscoveryResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TMDBApiService {

    @GET("discover/movie")
    Call<TMDBDiscoveryResponse> discoverMovies(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("include_adult") boolean includeAdult,
            @Query("include_video") boolean includeVideo,
            @Query("language") String language,
            @Query("page") int page,
            @Query("sort_by") String sortBy
    );

    @GET("discover/tv")
    Call<TMDBDiscoveryResponse> discoverShows(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("include_adult") boolean includeAdult,
            @Query("include_null_first_air_dates") boolean includeNullFirstAirDates,
            @Query("language") String language,
            @Query("page") int page,
            @Query("sort_by") String sortBy
    );

    @GET("movie/now_playing")
    Call<TMDBDiscoveryResponse> nowPlaying(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("language") String language,
            @Query("page") int page
    );

    @GET("movie/upcoming")
    Call<TMDBDiscoveryResponse> upcomingMovies(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("language") String language,
            @Query("page") int page
    );

    @GET("trending/all/day")
    Call<TMDBDiscoveryResponse> trendingToday(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("language") String language,
            @Query("page") int page
    );

    @GET("tv/on_the_air")
    Call<TMDBDiscoveryResponse> onTheAir(
            @Header("Authorization") String bearer,
            @Query("api_key") String apiKey,
            @Query("language") String language,
            @Query("page") int page
    );

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
