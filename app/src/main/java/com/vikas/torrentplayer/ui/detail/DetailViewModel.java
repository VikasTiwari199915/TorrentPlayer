package com.vikas.torrentplayer.ui.detail;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TMDBApiService;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.api.models.tmdb.TMDBEpisode;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonDetails;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeasonSummary;
import com.vikas.torrentplayer.api.models.tmdb.TMDBSeriesDetails;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DetailViewModel extends AndroidViewModel {

    private static final String TAG = "DetailViewModel";

    public enum UiState { LOADING, SUCCESS, EMPTY, ERROR }

    private final TorrentClawApi api = ApiClient.get();
    private final TMDBApiService tmdb = ApiClient.tmdb();
    private final PrefsManager prefs;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.LOADING);
    private final MutableLiveData<List<TorrentItem>> torrents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<SearchResult> resultLive = new MutableLiveData<>(null);
    private final MutableLiveData<TMDBSeriesDetails> seriesDetails = new MutableLiveData<>(null);
    private final MutableLiveData<List<TMDBSeasonSummary>> seasons = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<TMDBEpisode>> episodes = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> episodeMetadataMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> episodeLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> selectedSeason = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> selectedEpisode = new MutableLiveData<>(null);
    private SearchResult result;

    @Nullable private Call<SearchResult> detailCall;
    @Nullable private Call<SearchResponse> searchCall;
    @Nullable private Call<TMDBSeriesDetails> tmdbSeriesCall;
    @Nullable private Call<TMDBSeasonDetails> tmdbSeasonCall;

    public DetailViewModel(@NonNull Application app) {
        super(app);
        this.prefs = new PrefsManager(app);
    }

    public LiveData<UiState> state() { return state; }
    public LiveData<List<TorrentItem>> torrents() { return torrents; }
    /** Live view of {@link #result()} — emits whenever the VM swaps in a richer
     *  SearchResult (e.g. after the discover → search fallback resolves and
     *  brings the backdrop with it). */
    public LiveData<SearchResult> resultLive() { return resultLive; }
    public LiveData<TMDBSeriesDetails> seriesDetails() { return seriesDetails; }
    public LiveData<List<TMDBSeasonSummary>> seasons() { return seasons; }
    public LiveData<List<TMDBEpisode>> episodes() { return episodes; }
    public LiveData<String> episodeMetadataMessage() { return episodeMetadataMessage; }
    public LiveData<Boolean> episodeLoading() { return episodeLoading; }
    public LiveData<Integer> selectedSeason() { return selectedSeason; }
    public LiveData<Integer> selectedEpisode() { return selectedEpisode; }
    public SearchResult result() { return result; }

    public void load(SearchResult initial) {
        this.result = initial;
        resultLive.setValue(initial);
        loadSeriesDetailsIfNeeded(initial);

        // If we already have torrents from the search response, surface them now.
        if (initial.torrents != null && !initial.torrents.isEmpty()) {
            publish(initial.torrents);
            return;
        }

        if (!prefs.hasApiKey()) {
            state.setValue(UiState.ERROR);
            return;
        }
        state.setValue(UiState.LOADING);
        cancelInFlight();
        searchByTitle(initial);
    }

    /**
     * Fallback: hit the search endpoint with the title and pick the result whose
     * id matches what the caller asked for. This is what unlocks the Discover
     * → Detail flow on the free tier (where /content/{id} doesn't exist).
     */
    private void searchByTitle(SearchResult initial) {
        if (initial.title == null || initial.title.isEmpty()) {
            state.setValue(UiState.EMPTY);
            return;
        }
        String apiKey = prefs.getApiKey();
        // type=null returns both movies and shows
        searchCall = api.search(
                ApiClient.bearer(apiKey),
                initial.title,
                null,
                "available",
                "seeders",
                1,
                25,
                false,
                /* genre */ null,
                /* season */ null,
                /* episode */ null,
                apiKey);
        searchCall.enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> call, @NonNull Response<SearchResponse> response) {
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null
                        || response.body().results == null
                        || response.body().results.isEmpty()) {
                    state.setValue(UiState.EMPTY);
                    return;
                }
                SearchResult best = pickBest(response.body().results, initial);
                if (best == null) {
                    state.setValue(UiState.EMPTY);
                    return;
                }
                mergeInto(best, initial);
                DetailViewModel.this.result = best;
                resultLive.setValue(best);
                loadSeriesDetailsIfNeeded(best);
                publish(best.torrents);
            }

            @Override
            public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "search fallback failed", t);
                state.setValue(UiState.ERROR);
            }
        });
    }

    /** Prefer exact id match; otherwise prefer same title+year; otherwise first. */
    @Nullable
    private static SearchResult pickBest(@NonNull List<SearchResult> results, @NonNull SearchResult initial) {
        // 1) Exact id match
        for (SearchResult r : results) if (r.id == initial.id) return r;
        // 2) Same title and year
        if (initial.title != null && initial.year != null) {
            for (SearchResult r : results) {
                if (initial.title.equalsIgnoreCase(r.title)
                        && initial.year.equals(r.year)) return r;
            }
        }
        // 3) Same title
        if (initial.title != null) {
            for (SearchResult r : results) {
                if (initial.title.equalsIgnoreCase(r.title)) return r;
            }
        }
        return results.get(0);
    }

    /** Copy non-null header fields from {@code src} into {@code dst} so the UI
     *  retains poster/backdrop/overview when the new response is sparse. */
    private static void mergeInto(@NonNull SearchResult dst, @NonNull SearchResult src) {
        if (dst.title == null) dst.title = src.title;
        if (dst.year == null) dst.year = src.year;
        if (dst.overview == null) dst.overview = src.overview;
        if (dst.posterUrl == null) dst.posterUrl = src.posterUrl;
        if (dst.backdropUrl == null) dst.backdropUrl = src.backdropUrl;
        if (dst.ratingImdb == null) dst.ratingImdb = src.ratingImdb;
        if (dst.ratingTmdb == null) dst.ratingTmdb = src.ratingTmdb;
        if (dst.genres == null) dst.genres = src.genres;
        if (dst.contentType == null) dst.contentType = src.contentType;
    }

    private void publish(@Nullable List<TorrentItem> raw) {
        List<TorrentItem> filtered = filterAndSort(raw);
        torrents.setValue(filtered);
        state.setValue(filtered.isEmpty() ? UiState.EMPTY : UiState.SUCCESS);
    }

    public void clearEpisodeFilter() {
        selectedSeason.setValue(null);
        selectedEpisode.setValue(null);
        episodes.setValue(new ArrayList<>());
        if (result != null) publish(result.torrents);
    }

    public void selectSeason(@Nullable Integer season) {
        selectedSeason.setValue(season);
        selectedEpisode.setValue(null);
        episodes.setValue(new ArrayList<>());
        if (season == null) {
            if (result != null) publish(result.torrents);
            return;
        }
        loadSeasonEpisodes(season);
        filterTorrentsByEpisode(season, null);
    }

    public void selectEpisode(@Nullable Integer episode) {
        selectedEpisode.setValue(episode);
        Integer season = selectedSeason.getValue();
        if (season == null) return;
        filterTorrentsByEpisode(season, episode);
    }

    private void loadSeriesDetailsIfNeeded(@Nullable SearchResult r) {
        if (r == null || !r.isShow()) return;
        if (r.tmdbId == null || r.tmdbId <= 0) {
            episodeMetadataMessage.setValue("This show does not include a TMDB ID, so season metadata is unavailable.");
            return;
        }
        String credential = prefs.getTmdbCredential();
        if (credential == null || credential.trim().isEmpty()) {
            episodeMetadataMessage.setValue("Add your TMDB read token or API key in Settings to browse seasons and episodes.");
            return;
        }
        episodeMetadataMessage.setValue(null);
        if (tmdbSeriesCall != null) tmdbSeriesCall.cancel();
        tmdbSeriesCall = tmdb.seriesDetails(
                tmdbBearer(credential), r.tmdbId,
                tmdbApiKey(credential), "en-US");
        tmdbSeriesCall.enqueue(new Callback<TMDBSeriesDetails>() {
            @Override public void onResponse(@NonNull Call<TMDBSeriesDetails> call,
                                             @NonNull Response<TMDBSeriesDetails> response) {
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    episodeMetadataMessage.setValue("TMDB request failed: HTTP " + response.code());
                    return;
                }
                TMDBSeriesDetails body = response.body();
                seriesDetails.setValue(body);
                List<TMDBSeasonSummary> list = body.seasons == null
                        ? new ArrayList<>() : new ArrayList<>(body.seasons);
                seasons.setValue(list);
            }

            @Override public void onFailure(@NonNull Call<TMDBSeriesDetails> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                episodeMetadataMessage.setValue(t.getMessage() != null ? t.getMessage() : "TMDB request failed");
            }
        });
    }

    private void loadSeasonEpisodes(int season) {
        SearchResult r = result;
        String credential = prefs.getTmdbCredential();
        if (r == null || r.tmdbId == null || credential == null || credential.trim().isEmpty()) return;
        if (tmdbSeasonCall != null) tmdbSeasonCall.cancel();
        episodeLoading.setValue(true);
        tmdbSeasonCall = tmdb.seasonDetails(
                tmdbBearer(credential), r.tmdbId, season,
                tmdbApiKey(credential), "en-US");
        tmdbSeasonCall.enqueue(new Callback<TMDBSeasonDetails>() {
            @Override public void onResponse(@NonNull Call<TMDBSeasonDetails> call,
                                             @NonNull Response<TMDBSeasonDetails> response) {
                if (call.isCanceled()) return;
                episodeLoading.setValue(false);
                if (!response.isSuccessful() || response.body() == null) {
                    episodeMetadataMessage.setValue("TMDB season request failed: HTTP " + response.code());
                    return;
                }
                List<TMDBEpisode> out = new ArrayList<>();
                if (response.body().episodes != null) {
                    for (TMDBEpisode e : response.body().episodes) if (e != null && e.isReleased()) out.add(e);
                }
                episodes.setValue(out);
            }

            @Override public void onFailure(@NonNull Call<TMDBSeasonDetails> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                episodeLoading.setValue(false);
                episodeMetadataMessage.setValue(t.getMessage() != null ? t.getMessage() : "TMDB season request failed");
            }
        });
    }

    private void filterTorrentsByEpisode(@Nullable Integer season, @Nullable Integer episode) {
        SearchResult r = result;
        if (r == null || r.title == null || r.title.isEmpty()) return;
        if (!prefs.hasApiKey()) return;
        state.setValue(UiState.LOADING);
        if (searchCall != null) searchCall.cancel();
        String apiKey = prefs.getApiKey();
        searchCall = api.search(ApiClient.bearer(apiKey), r.title, "show",
                "available", "seeders", 1, 50, false,
                null, season, episode, apiKey);
        searchCall.enqueue(new Callback<SearchResponse>() {
            @Override public void onResponse(@NonNull Call<SearchResponse> call,
                                             @NonNull Response<SearchResponse> response) {
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null || response.body().results == null) {
                    torrents.setValue(new ArrayList<>());
                    state.setValue(UiState.EMPTY);
                    return;
                }
                SearchResult best = pickBest(response.body().results, r);
                publish(best == null ? null : best.torrents);
            }

            @Override public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "episode torrent filter failed", t);
                state.setValue(UiState.ERROR);
            }
        });
    }

    @Nullable private static String tmdbBearer(String credential) {
        String c = credential == null ? "" : credential.trim();
        return c.contains(".") ? "Bearer " + c : null;
    }

    @Nullable private static String tmdbApiKey(String credential) {
        String c = credential == null ? "" : credential.trim();
        return c.isEmpty() || c.contains(".") ? null : c;
    }

    private List<TorrentItem> filterAndSort(@Nullable List<TorrentItem> raw) {
        List<TorrentItem> out = new ArrayList<>();
        if (raw == null) return out;
        boolean verifiedOnly = prefs.isVerifiedOnly();
        for (TorrentItem t : raw) {
            if (t == null) continue;
            if (verifiedOnly && !t.isTrusted()) continue;
            //api provider site does not seem to work properly and stops sending magnet urls sometimes
//            if (t.magnetUrl == null || t.magnetUrl.isEmpty()) continue;
            if (t.infoHash == null || t.infoHash.isEmpty()) continue;
            out.add(t);
        }
        String pref = prefs.getDefaultQuality();
        out.sort((a, b) -> {
            int aPref = "any".equals(pref) ? 0 : pref.equalsIgnoreCase(a.quality) ? -1 : 0;
            int bPref = "any".equals(pref) ? 0 : pref.equalsIgnoreCase(b.quality) ? -1 : 0;
            if (aPref != bPref) return Integer.compare(aPref, bPref);
            return Integer.compare(b.seeders, a.seeders);
        });
        return out;
    }

    private void cancelInFlight() {
        if (detailCall != null) { detailCall.cancel(); detailCall = null; }
        if (searchCall != null) { searchCall.cancel(); searchCall = null; }
        if (tmdbSeriesCall != null) { tmdbSeriesCall.cancel(); tmdbSeriesCall = null; }
        if (tmdbSeasonCall != null) { tmdbSeasonCall.cancel(); tmdbSeasonCall = null; }
    }

    @Override
    protected void onCleared() {
        cancelInFlight();
    }
}
