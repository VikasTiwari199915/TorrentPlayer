package com.vikas.torrentplayer.ui.search;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.SearchResponse;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchViewModel extends AndroidViewModel {

    private static final String TAG = "SearchViewModel";
    private static final int PAGE_SIZE = 20;

    public enum UiState { IDLE, LOADING, SUCCESS, EMPTY, ERROR, NO_API_KEY }

    public enum Filter { ALL, MOVIES, SHOWS }

    private final TorrentClawApi api = ApiClient.get();
    private final PrefsManager prefs;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<List<SearchResult>> results = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    private String currentQuery = "";
    private Filter currentFilter = Filter.ALL;
    @Nullable private String currentGenre;     // null = no filter
    @Nullable private Integer currentSeason;   // null = no filter
    @Nullable private Integer currentEpisode;  // null = no filter
    private int currentPage = 1;
    private int totalAvailable = 0;
    private boolean loading = false;

    @Nullable
    private Call<SearchResponse> inFlight;

    public SearchViewModel(@NonNull Application app) {
        super(app);
        this.prefs = new PrefsManager(app);
    }

    public LiveData<UiState> state() { return state; }
    public LiveData<List<SearchResult>> results() { return results; }
    public LiveData<String> errorMessage() { return errorMessage; }

    public Filter currentFilter() { return currentFilter; }
    @Nullable public String currentGenre() { return currentGenre; }
    @Nullable public Integer currentSeason() { return currentSeason; }
    @Nullable public Integer currentEpisode() { return currentEpisode; }

    /** True when at least one non-default filter is set. Lets the SearchBar
     *  show an "active filter" indicator without reaching into VM internals. */
    public boolean hasActiveFilters() {
        return currentFilter != Filter.ALL
                || currentGenre != null
                || currentSeason != null
                || currentEpisode != null;
    }

    public void setFilter(Filter f) {
        if (f == currentFilter) return;
        this.currentFilter = f;
        if (!currentQuery.isEmpty()) {
            search(currentQuery);
        }
    }

    /** Apply a combined filter snapshot from the bottom sheet. */
    public void applyFilters(@Nullable String genre, @Nullable Integer season,
                             @Nullable Integer episode) {
        boolean changed = !eq(genre, currentGenre)
                || !eq(season, currentSeason)
                || !eq(episode, currentEpisode);
        currentGenre = genre;
        currentSeason = season;
        currentEpisode = episode;
        if (changed && !currentQuery.isEmpty()) {
            search(currentQuery);
        }
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public void search(String query) {
        if (query == null) query = "";
        this.currentQuery = query.trim();
        this.currentPage = 1;

        if (currentQuery.isEmpty()) {
            cancelInFlight();
            results.setValue(new ArrayList<>());
            state.setValue(UiState.IDLE);
            return;
        }

        if (!prefs.hasApiKey()) {
            state.setValue(UiState.NO_API_KEY);
            return;
        }

        fetch(true);
    }

    public void loadMore() {
        if (loading || currentQuery.isEmpty()) return;
        List<SearchResult> have = results.getValue();
        int loaded = have == null ? 0 : have.size();
        if (totalAvailable > 0 && loaded >= totalAvailable) return;
        currentPage++;
        fetch(false);
    }

    private void fetch(boolean reset) {
        cancelInFlight();
        loading = true;
        if (reset) state.setValue(UiState.LOADING);

        String type = filterToType(currentFilter);
        String apiKey = prefs.getApiKey();
        String bearer = ApiClient.bearer(apiKey);
        boolean verifiedOnly = prefs.isVerifiedOnly();

        inFlight = api.search(
                bearer,
                currentQuery,
                type,
                "available",
                "seeders",
                currentPage,
                PAGE_SIZE,
                verifiedOnly,
                currentGenre,
                currentSeason,
                currentEpisode,
                apiKey
        );

        final boolean firstPage = reset;
        inFlight.enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> call, @NonNull Response<SearchResponse> response) {
                loading = false;
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    String msg = "HTTP " + response.code();
                    Log.w(TAG, "search failed: " + msg);
                    errorMessage.setValue(msg);
                    state.setValue(UiState.ERROR);
                    return;
                }
                SearchResponse body = response.body();
                totalAvailable = body.total;

                List<SearchResult> merged = firstPage
                        ? new ArrayList<>()
                        : new ArrayList<>(results.getValue() == null ? new ArrayList<>() : results.getValue());
                if (body.results != null) merged.addAll(body.results);
                results.setValue(merged);

                if (merged.isEmpty()) {
                    state.setValue(UiState.EMPTY);
                } else {
                    state.setValue(UiState.SUCCESS);
                }
            }

            @Override
            public void onFailure(@NonNull Call<SearchResponse> call, @NonNull Throwable t) {
                loading = false;
                if (call.isCanceled()) return;
                Log.e(TAG, "search error", t);
                errorMessage.setValue(t.getMessage());
                state.setValue(UiState.ERROR);
            }
        });
    }

    private void cancelInFlight() {
        if (inFlight != null) {
            inFlight.cancel();
            inFlight = null;
        }
    }

    @Override
    protected void onCleared() {
        cancelInFlight();
    }

    @Nullable
    private static String filterToType(Filter f) {
        switch (f) {
            case MOVIES: return "movie";
            case SHOWS: return "show";
            default: return null;
        }
    }
}
