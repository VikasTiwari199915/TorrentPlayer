package com.vikas.torrentplayer.ui.discover;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vikas.torrentplayer.api.ApiClient;
import com.vikas.torrentplayer.api.TorrentClawApi;
import com.vikas.torrentplayer.api.models.DiscoverItem;
import com.vikas.torrentplayer.api.models.DiscoverListResponse;
import com.vikas.torrentplayer.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * State for the Discover screen. Loads four independent sections (trending,
 * popular, recent, streaming-top) and exposes each as its own LiveData so the
 * UI can show partial results as they arrive.
 */
public class DiscoverViewModel extends AndroidViewModel {

    private static final String TAG = "DiscoverVM";

    public enum SectionState { LOADING, SUCCESS, EMPTY, ERROR }

    public static final String[] STREAMING_SERVICES = { "netflix", "prime", "disney", "apple", "crunchyroll" };

    private final TorrentClawApi api = ApiClient.get();
    private final PrefsManager prefs;

    private final MutableLiveData<List<DiscoverItem>> trending = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DiscoverItem>> popular = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DiscoverItem>> recent = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DiscoverItem>> streamingTop = new MutableLiveData<>(Collections.emptyList());

    private final MutableLiveData<SectionState> trendingState = new MutableLiveData<>(SectionState.LOADING);
    private final MutableLiveData<SectionState> popularState = new MutableLiveData<>(SectionState.LOADING);
    private final MutableLiveData<SectionState> recentState = new MutableLiveData<>(SectionState.LOADING);
    private final MutableLiveData<SectionState> streamingTopState = new MutableLiveData<>(SectionState.LOADING);

    private final MutableLiveData<String> selectedService = new MutableLiveData<>("netflix");

    private final List<Call<?>> inFlight = new ArrayList<>();
    private boolean loaded = false;

    public DiscoverViewModel(@NonNull Application app) {
        super(app);
        this.prefs = new PrefsManager(app);
    }

    public LiveData<List<DiscoverItem>> trending() { return trending; }
    public LiveData<List<DiscoverItem>> popular() { return popular; }
    public LiveData<List<DiscoverItem>> recent() { return recent; }
    public LiveData<List<DiscoverItem>> streamingTop() { return streamingTop; }

    public LiveData<SectionState> trendingState() { return trendingState; }
    public LiveData<SectionState> popularState() { return popularState; }
    public LiveData<SectionState> recentState() { return recentState; }
    public LiveData<SectionState> streamingTopState() { return streamingTopState; }

    public LiveData<String> selectedService() { return selectedService; }

    public List<String> services() { return Arrays.asList(STREAMING_SERVICES); }

    /** Trigger a load. No-op if already populated; pass {@code force=true} to refresh. */
    public void loadIfNeeded(boolean force) {
        if (loaded && !force) return;
        loaded = true;
        if (!prefs.hasApiKey()) {
            trendingState.setValue(SectionState.ERROR);
            popularState.setValue(SectionState.ERROR);
            recentState.setValue(SectionState.ERROR);
            streamingTopState.setValue(SectionState.ERROR);
            return;
        }
        loadTrending();
        loadPopular();
        loadRecent();
        loadStreamingTop(currentService());
    }

    /** Switch service for the streaming-top section and reload that section. */
    public void selectService(String service) {
        if (service == null || service.equals(currentService())) return;
        selectedService.setValue(service);
        loadStreamingTop(service);
    }

    public String currentService() {
        String s = selectedService.getValue();
        return s == null ? "netflix" : s;
    }

    private void loadTrending() {
        trendingState.setValue(SectionState.LOADING);
        String key = prefs.getApiKey();
        Call<DiscoverListResponse> c = api.getTrending(ApiClient.bearer(key), "daily", 20, 1, key);
        track(c);
        c.enqueue(new Callback<DiscoverListResponse>() {
            @Override
            public void onResponse(@NonNull Call<DiscoverListResponse> call, @NonNull Response<DiscoverListResponse> response) {
                if (call.isCanceled()) return;
                List<DiscoverItem> items = unwrap(response);
                trending.setValue(items);
                trendingState.setValue(items.isEmpty() ? SectionState.EMPTY : SectionState.SUCCESS);
            }
            @Override public void onFailure(@NonNull Call<DiscoverListResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "trending failed", t);
                trendingState.setValue(SectionState.ERROR);
            }
        });
    }

    private void loadPopular() {
        popularState.setValue(SectionState.LOADING);
        String key = prefs.getApiKey();
        Call<DiscoverListResponse> c = api.getPopular(ApiClient.bearer(key), 20, 1, key);
        track(c);
        c.enqueue(new Callback<DiscoverListResponse>() {
            @Override
            public void onResponse(@NonNull Call<DiscoverListResponse> call, @NonNull Response<DiscoverListResponse> response) {
                if (call.isCanceled()) return;
                List<DiscoverItem> items = unwrap(response);
                popular.setValue(items);
                popularState.setValue(items.isEmpty() ? SectionState.EMPTY : SectionState.SUCCESS);
            }
            @Override public void onFailure(@NonNull Call<DiscoverListResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "popular failed", t);
                popularState.setValue(SectionState.ERROR);
            }
        });
    }

    private void loadRecent() {
        recentState.setValue(SectionState.LOADING);
        String key = prefs.getApiKey();
        Call<DiscoverListResponse> c = api.getRecent(ApiClient.bearer(key), 20, 1, key);
        track(c);
        c.enqueue(new Callback<DiscoverListResponse>() {
            @Override
            public void onResponse(@NonNull Call<DiscoverListResponse> call, @NonNull Response<DiscoverListResponse> response) {
                if (call.isCanceled()) return;
                List<DiscoverItem> items = unwrap(response);
                recent.setValue(items);
                recentState.setValue(items.isEmpty() ? SectionState.EMPTY : SectionState.SUCCESS);
            }
            @Override public void onFailure(@NonNull Call<DiscoverListResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "recent failed", t);
                recentState.setValue(SectionState.ERROR);
            }
        });
    }

    private void loadStreamingTop(String service) {
        streamingTopState.setValue(SectionState.LOADING);
        String key = prefs.getApiKey();
        // "series" rather than "show" for this endpoint
        Call<List<DiscoverItem>> c = api.getStreamingTop(ApiClient.bearer(key), service, "US", "movie", key);
        track(c);
        c.enqueue(new Callback<List<DiscoverItem>>() {
            @Override
            public void onResponse(@NonNull Call<List<DiscoverItem>> call, @NonNull Response<List<DiscoverItem>> response) {
                if (call.isCanceled()) return;
                List<DiscoverItem> items = response.isSuccessful() && response.body() != null
                        ? response.body()
                        : Collections.<DiscoverItem>emptyList();
                streamingTop.setValue(items);
                streamingTopState.setValue(items.isEmpty() ? SectionState.EMPTY : SectionState.SUCCESS);
            }
            @Override public void onFailure(@NonNull Call<List<DiscoverItem>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                Log.e(TAG, "streaming-top failed", t);
                streamingTopState.setValue(SectionState.ERROR);
            }
        });
    }

    @Nullable
    private static List<DiscoverItem> unwrap(Response<DiscoverListResponse> r) {
        if (r.isSuccessful() && r.body() != null && r.body().items != null) return r.body().items;
        return Collections.emptyList();
    }

    private void track(Call<?> c) { inFlight.add(c); }

    @Override
    protected void onCleared() {
        for (Call<?> c : inFlight) {
            try { c.cancel(); } catch (Exception ignored) {}
        }
        inFlight.clear();
    }
}
