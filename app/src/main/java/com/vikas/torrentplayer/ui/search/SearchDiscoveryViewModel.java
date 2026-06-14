package com.vikas.torrentplayer.ui.search;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TMDBApiService;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.tmdb.TMDBDiscoveryItem;
import com.vikas.torrentplayer.api.models.tmdb.TMDBDiscoveryResponse;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** TMDB-powered carousels shown while the Search screen has no active query. */
public class SearchDiscoveryViewModel extends AndroidViewModel {
    private static final String TAG = "SearchDiscoveryVM";
    private static final String LANGUAGE = "en-US";

    public enum Category {
        TRENDING,
        POPULAR_MOVIES,
        POPULAR_SHOWS,
        NOW_PLAYING,
        UPCOMING,
        ON_THE_AIR
    }

    public enum State { IDLE, LOADING, CONTENT, EMPTY, ERROR, NO_CREDENTIAL }

    private final TMDBApiService api = ApiClient.tmdb();
    private final PrefsManager prefs;
    private final Map<Category, MutableLiveData<List<DiscoverItem>>> items =
            new EnumMap<>(Category.class);
    private final Map<Category, MutableLiveData<Boolean>> loading =
            new EnumMap<>(Category.class);
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final List<Call<?>> inFlight = new ArrayList<>();

    private boolean loaded;
    private String loadedCredential = "";
    private int pending;
    private int failures;

    public SearchDiscoveryViewModel(@NonNull Application app) {
        super(app);
        prefs = new PrefsManager(app);
        for (Category category : Category.values()) {
            items.put(category, new MutableLiveData<>(Collections.emptyList()));
            loading.put(category, new MutableLiveData<>(false));
        }
    }

    public LiveData<List<DiscoverItem>> items(Category category) {
        return items.get(category);
    }

    public LiveData<Boolean> loading(Category category) {
        return loading.get(category);
    }

    public LiveData<State> state() {
        return state;
    }

    public void load(boolean force) {
        String credential = prefs.getTmdbCredential();
        if (credential == null || credential.trim().isEmpty()) {
            cancelCalls();
            loaded = false;
            loadedCredential = "";
            state.setValue(State.NO_CREDENTIAL);
            return;
        }
        credential = credential.trim();
        if (loaded && credential.equals(loadedCredential) && !force) return;
        cancelCalls();

        loaded = true;
        loadedCredential = credential;
        pending = Category.values().length;
        failures = 0;
        state.setValue(State.LOADING);
        for (Category category : Category.values()) {
            MutableLiveData<Boolean> sectionLoading = loading.get(category);
            if (sectionLoading != null) sectionLoading.setValue(true);
        }

        String bearer = tmdbBearer(credential);
        String apiKey = tmdbApiKey(credential);
        enqueue(Category.TRENDING,
                api.trendingToday(bearer, apiKey, LANGUAGE, 1), "");
        enqueue(Category.POPULAR_MOVIES,
                api.discoverMovies(bearer, apiKey, true, true,
                        LANGUAGE, 1, "popularity.desc"), "movie");
        enqueue(Category.POPULAR_SHOWS,
                api.discoverShows(bearer, apiKey, true, false,
                        LANGUAGE, 1, "popularity.desc"), "show");
        enqueue(Category.NOW_PLAYING,
                api.nowPlaying(bearer, apiKey, LANGUAGE, 1), "movie");
        enqueue(Category.UPCOMING,
                api.upcomingMovies(bearer, apiKey, LANGUAGE, 1), "movie");
        enqueue(Category.ON_THE_AIR,
                api.onTheAir(bearer, apiKey, LANGUAGE, 1), "show");
    }

    private void enqueue(Category category, Call<TMDBDiscoveryResponse> call,
                         String fallbackType) {
        inFlight.add(call);
        call.enqueue(new Callback<TMDBDiscoveryResponse>() {
            @Override
            public void onResponse(@NonNull Call<TMDBDiscoveryResponse> request,
                                   @NonNull Response<TMDBDiscoveryResponse> response) {
                if (request.isCanceled()) return;
                List<DiscoverItem> mapped = map(response, fallbackType);
                MutableLiveData<List<DiscoverItem>> target = items.get(category);
                if (target != null) target.setValue(mapped);
                if (!response.isSuccessful()) failures++;
                finish(category);
            }

            @Override
            public void onFailure(@NonNull Call<TMDBDiscoveryResponse> request,
                                  @NonNull Throwable error) {
                if (request.isCanceled()) return;
                Log.e(TAG, category + " failed", error);
                failures++;
                MutableLiveData<List<DiscoverItem>> target = items.get(category);
                if (target != null) target.setValue(Collections.emptyList());
                finish(category);
            }
        });
    }

    private void finish(Category category) {
        MutableLiveData<Boolean> sectionLoading = loading.get(category);
        if (sectionLoading != null) sectionLoading.setValue(false);
        pending--;
        if (pending > 0) return;

        boolean hasContent = false;
        for (MutableLiveData<List<DiscoverItem>> value : items.values()) {
            List<DiscoverItem> list = value.getValue();
            if (list != null && !list.isEmpty()) {
                hasContent = true;
                break;
            }
        }
        if (hasContent) state.setValue(State.CONTENT);
        else if (failures > 0) state.setValue(State.ERROR);
        else state.setValue(State.EMPTY);
    }

    private static List<DiscoverItem> map(Response<TMDBDiscoveryResponse> response,
                                          String fallbackType) {
        if (!response.isSuccessful() || response.body() == null
                || response.body().results == null) {
            return Collections.emptyList();
        }
        List<DiscoverItem> out = new ArrayList<>();
        for (TMDBDiscoveryItem raw : response.body().results) {
            if (raw == null) continue;
            DiscoverItem item = raw.toDiscoverItem(fallbackType);
            if (item != null) out.add(item);
        }
        return out;
    }

    @Nullable
    private static String tmdbBearer(String credential) {
        String value = credential == null ? "" : credential.trim();
        return value.contains(".") ? "Bearer " + value : null;
    }

    @Nullable
    private static String tmdbApiKey(String credential) {
        String value = credential == null ? "" : credential.trim();
        return value.isEmpty() || value.contains(".") ? null : value;
    }

    private void cancelCalls() {
        for (Call<?> call : inFlight) {
            try {
                call.cancel();
            } catch (Exception ignored) {
            }
        }
        inFlight.clear();
        pending = 0;
    }

    @Override
    protected void onCleared() {
        cancelCalls();
    }
}
