package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.DiscoverListResponse;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface TorrentClawApi {

    /**
     * GET https://torrentclaw.com/api/v1/search
     *
     * @param query        Search keyword (e.g. movie or show title)
     * @param type         "movie" | "show" | null for both
     * @param availability "available" | null
     * @param sort         "seeders" | "leechers" | "date" | "size" etc.
     * @param page         1-based page index
     * @param limit        page size
     * @param verified     true to return only verified
     */
    @GET("api/v1/search")
    Call<SearchResponse> search(
            @Header("Authorization") String bearer,
            @Query("q") String query,
            @Query("type") String type,
            @Query("availability") String availability,
            @Query("sort") String sort,
            @Query("page") int page,
            @Query("limit") int limit,
            @Query("verified") boolean verified,
            @Query("api_key") String apiKey
    );

    /** Returns full detail (including torrents) for a given result. */
    @GET("api/v1/content/{id}")
    Call<SearchResult> getContent(
            @Header("Authorization") String bearer,
            @Path("id") long id,
            @Query("api_key") String apiKey
    );

    // ---- Discover endpoints --------------------------------------------------

    /** Most-popular content overall. Sorted by maxSeeders desc. */
    @GET("api/v1/popular")
    Call<DiscoverListResponse> getPopular(
            @Header("Authorization") String bearer,
            @Query("limit") int limit,
            @Query("page") int page,
            @Query("api_key") String apiKey
    );

    /** Newest items in the catalogue. */
    @GET("api/v1/recent")
    Call<DiscoverListResponse> getRecent(
            @Header("Authorization") String bearer,
            @Query("limit") int limit,
            @Query("page") int page,
            @Query("api_key") String apiKey
    );

    /**
     * Trending content for a period (e.g. "daily"). Items carry clickCount and
     * trendScore in addition to the basic discover fields.
     */
    @GET("api/v1/trending")
    Call<DiscoverListResponse> getTrending(
            @Header("Authorization") String bearer,
            @Query("period") String period,
            @Query("limit") int limit,
            @Query("page") int page,
            @Query("api_key") String apiKey
    );

    /**
     * Downloads the raw .torrent file for a given infoHash. Skipping DHT
     * metadata fetch (which is what fails with "No Torrent info could be found"
     * in TorrentStream) means streams start almost immediately.
     */
    @GET("api/v1/torrent/{infoHash}")
    @Headers({ "Accept: application/x-bittorrent, application/octet-stream, */*" })
    @Streaming
    Call<ResponseBody> downloadTorrentFile(
            @Header("Authorization") String bearer,
            @Path("infoHash") String infoHash,
            @Query("api_key") String apiKey
    );

    /**
     * Top items on a streaming service in a country. Note: returns a bare JSON
     * array (no envelope), and uses {@code contentId} (not {@code id}) to point
     * back at the catalogue row.
     *
     * @param service  netflix | prime | disney | apple | crunchyroll
     * @param country  ISO country, e.g. "US"
     * @param showType movie | series
     */
    @GET("api/v1/streaming-top")
    Call<List<DiscoverItem>> getStreamingTop(
            @Header("Authorization") String bearer,
            @Query("service") String service,
            @Query("country") String country,
            @Query("show_type") String showType,
            @Query("api_key") String apiKey
    );
}
